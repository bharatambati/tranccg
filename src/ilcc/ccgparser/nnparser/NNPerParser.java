/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ilcc.ccgparser.nnparser;

import com.google.common.primitives.Ints;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Timing;
import ilcc.ccgparser.algos.NonInc;
import ilcc.ccgparser.learn.AvePerceptron;
import ilcc.ccgparser.learn.AvePerceptron.FeatureVector;
import ilcc.ccgparser.learn.AvePerceptron.Weight;
import ilcc.ccgparser.learn.Feature;
import ilcc.ccgparser.parser.PStateItem;
import ilcc.ccgparser.parser.SRParser;
import ilcc.ccgparser.parser.TempAgenda;
import ilcc.ccgparser.utils.ArcAction;
import ilcc.ccgparser.utils.CCGDepInfo;
import ilcc.ccgparser.utils.CCGRuleInfo;
import ilcc.ccgparser.utils.CCGSentence;
import ilcc.ccgparser.utils.CCGTreeNode;
import ilcc.ccgparser.utils.DataTypes.CCGCategory;
import ilcc.ccgparser.utils.DataTypes.DepLabel;
import ilcc.ccgparser.utils.DataTypes.GoldccgInfo;
import ilcc.ccgparser.utils.SCoNLLNode;
import ilcc.ccgparser.utils.Utils;
import ilcc.ccgparser.utils.Utils.SRAction;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;

/**
 *
 * @author ambati
 */
public class NNPerParser {

    String trainCoNLLFile, trainAutoFile, trainPargFile, testCoNLLFile, testAutoFile, testPargFile, nnmodelFile, nnpermodelFile, outAutoFile, outPargFile;

    Map<Integer, GoldccgInfo> goldDetails;
    boolean isTrain, early_update, debug, incalgo, djoint;
    int beamSize, sSize, sentCount;

    public Map<String, CCGDepInfo> goldccgDeps, sysccgDeps;
    Stack<CCGTreeNode> stack;
    List<CCGTreeNode> input;
    Map<ArcAction, Integer> actsMap;
    Map<Integer, CCGSentence> gcdepsMap;
    List<ArcAction> actsList;
    SRParser srparser;
    private Map<String, Integer> wordIDs, posIDs, ccgcatIDs;    
    private Classifier classifier;
    private List<Integer> preComputed;
    private final Config config;
    private List<String> knownWords, knownPos, knownCCGCats;
    AvePerceptron model;
    List<PStateItem> agenda;
    private HashMap<Long, double[]> fvHash;
    private HashMap<Long, Integer> fvHashFreq;

    public NNPerParser(Properties props) throws IOException {
        config = new Config(props);
        trainAutoFile = props.getProperty("trainAuto");
        trainCoNLLFile = props.getProperty("trainCoNLL");
        trainPargFile = props.getProperty("trainParg");
        testAutoFile = props.getProperty("testAuto");
        testPargFile = props.getProperty("testParg");
        testCoNLLFile = props.getProperty("testCoNLL");
        outAutoFile = props.getProperty("outAuto");
        outPargFile = props.getProperty("outParg");
        isTrain = PropertiesUtils.getBool(props, "isTrain", false);
        beamSize = PropertiesUtils.getInt(props, "beam", 1);
        debug = PropertiesUtils.getBool(props, "debug", false);
        early_update = PropertiesUtils.getBool(props, "early", true);
        nnmodelFile = props.getProperty("nnmodel");
        nnpermodelFile = props.getProperty("model");

        goldDetails = new HashMap<>();
        goldccgDeps = new HashMap<>();
        fvHash = new HashMap<>();
        fvHashFreq = new HashMap<>();
        srparser = new NonInc();
        actsMap = new HashMap<>();
        agenda = new ArrayList<>(beamSize);
        model = new AvePerceptron();
        sSize = 0;
    }

    public void train(int iters) throws Exception {
        if (new File(nnpermodelFile).exists()) {
            //loadModel(nnpermodelFile);
        }

        System.err.println("Iteration: ");
        double bestlf = 0.0, lf;
        for (int i = 1; i <= iters; i++) {
            System.err.print(i + ": ");
            for (int sentid = 1; sentid < goldDetails.size() + 1; sentid++) {
                sSize++;
                if (sentid % 100 == 0) {
                    System.err.print(" " + sentid);
                }
                if (sentid % 1000 == 0) {
                    System.err.println();
                }

                if (debug) {
                    System.err.println(sentid);
                }

                parse(goldDetails.get(sentid).getccgSent(), sentid);
            }
            System.err.println();
            System.err.println("Parsing after iter: " + i);
            model.updateFinalWeights(sSize);
            lf = parse(testPargFile, testCoNLLFile, null, outPargFile);
            if (lf > bestlf) {
                saveModel(nnpermodelFile);
                bestlf = lf;
            }
        }
    }

    public double parse(String tAutoFile, String tConllFile, String oAutoFile, String oPargFile) throws IOException, Exception {

        testPargFile = tAutoFile;
        testCoNLLFile = tConllFile;

        BufferedReader conllReader = new BufferedReader(new FileReader(new File(testCoNLLFile)));
        BufferedWriter oPargWriter = new BufferedWriter(new FileWriter(new File(outPargFile)));
        BufferedWriter oAutoWriter = new BufferedWriter(new FileWriter(new File(outAutoFile)));
        oPargWriter.write("# Generated using \n# CCGParser.java\n\n");

        gcdepsMap = null;
        if(testPargFile != null){
            BufferedReader pargReader = new BufferedReader(new FileReader(new File(testPargFile)));
            gcdepsMap = getccgDepMap(pargReader);
        }

        ArrayList<CCGSentence> sentences = new ArrayList<>();
        System.err.println("Processing: ");
        srparser.init();

        String cLine;
        ArrayList<String> cLines = new ArrayList<>();
        while ((cLine = conllReader.readLine()) != null) {
            if (cLine.equals("")) {
                CCGSentence sent = new CCGSentence();
                sent.fillCoNLL(cLines);
                sentences.add(sent);
                cLines.clear();
            }
            cLines.add(cLine);
        }
        System.err.println();

        isTrain = false;
        int id = 0;
        for (CCGSentence sent : sentences) {
            id++;
            if (id % 100 == 0) {
                System.err.print(" " + id);
            }
            if (id % 100 == 0) {
                System.err.print(" ");
            }

            try {
                srparser.sent = sent;
                PStateItem output = parse(sent, id);
                if (output.stacksize() == 1) {
                    srparser.parsedSents++;
                }
                srparser.sentCount = sentences.size();
                HashMap<Integer, CCGCategory> sysCats = new HashMap<>();
                srparser.sysccgDeps = output.getSysCatsNDeps(sysCats);
                srparser.sconllDeps = output.getSysCONLLDeps();
                output = mergeFrags(output);
                output.writeDeriv(id, oAutoWriter);
                srparser.writeDeps(oPargWriter);
                if(gcdepsMap != null) {
                    CCGSentence gsent = gcdepsMap.get(id);
                    srparser.gconllDeps = sent.getDepNodes();
                    srparser.goldccgDeps = gsent.getPargDeps();
                    srparser.evaluateParseDependenciesJulia(djoint);
                    srparser.updateCatAccuray(gsent, sysCats);

                }
            } catch (Exception ex) {
                System.err.println("\n" + id + "\t" + ex);
            }
        }
        isTrain = true;
        oPargWriter.flush();
        oPargWriter.close();
        oAutoWriter.flush();
        oAutoWriter.close();
        System.err.println();
        srparser.printResults();
        return srparser.LF;
    }

    private PStateItem mergeFrags(PStateItem output) {
        int stacksize = output.stacksize();
        while (stacksize != 1) {
            ArcAction act = ArcAction.make(SRAction.REDUCE, "X");
            output = output.applyFrag(act);
            stacksize = output.stacksize();
        }
        return output;
    }

    private double[] softmax(double[] scores, List<Integer> olist) {

        if (olist.isEmpty()) {
            return scores;
        }

        int optLabel = -1;
        for (int i = 0; i < olist.size(); i++) {
            if (optLabel < 0 || scores[i] > scores[optLabel]) {
                optLabel = i;
            }
        }
        double maxScore = scores[optLabel];
        double sum = 0.0;

        for (int i = 0; i < olist.size(); i++) {
            sum += Math.exp(scores[i] - maxScore);
        }

        for (int i = 0; i < olist.size(); i++) {
            //scores[id] = Math.exp(scores[id] - maxScore - Math.log(sum));
            scores[i] = (scores[i] - maxScore - Math.log(sum));
        }

        return scores;
    }

    private double[] softmax2(double[] scores, List<Integer> olist) {

        if (olist.isEmpty()) {
            return scores;
        }

        double total = 0.0;
        for (int i = 0; i < olist.size(); i++) {
            total += Math.exp(scores[i]);
        }

        for (int i = 0; i < olist.size(); i++) {
            scores[i] = scores[i] - Math.log(total);
        }

        return scores;
    }

    public PStateItem parse(CCGSentence sent, int sentId) throws Exception {

        input = sent.getNodes();

        agenda.clear();
        PStateItem start = new PStateItem();
        agenda.add(start);

        PStateItem candidateOutput = null, output = null, correctState = start;

        int index = -1, goldId = 0;
        List<ArcAction> gActList = null;
        HashMap<Feature, Double> gfMap;

        if (isTrain) {
            gActList = goldDetails.get(sentId).getarcActs();
            if (gActList == null || gActList.isEmpty()) {
                return null;
            }
        }

        while (!agenda.isEmpty()) {
            List<PStateItem> list = new ArrayList<>(beamSize);
            ArcAction parserAct = null, goldAct = null;
            boolean corItem = false;
            ArrayList<ArcAction> acts, nacts;

            List<TempAgenda> tempAgenda = new ArrayList<>();
            for (int itemId = 0; itemId < agenda.size(); itemId++) {
                PStateItem item = agenda.get(itemId);

                int[] fArray = Ints.toArray(getFeatures(item));
                double[] scores = getHScores(fArray);
                //double[] scores = classifier.computeHidden(fArray);
                //scores = softmax(scores);
                HashMap<Feature, Double> fMap = new HashMap<>();
                for (int i = 0; i < scores.length; i++) {
                    fMap.put(Feature.make(Feature.FeatPrefix.nn, Arrays.asList(i)), scores[i]);
                }

                acts = getAction(item);

                if (debug) {
                    System.err.println(acts);
                }

                nacts = new ArrayList<>(acts.size());
                List<Integer> olist = new ArrayList<>(acts.size());
                for (ArcAction action : acts) {
                    if (actsMap.containsKey(action)) {
                        nacts.add(action);
                    }
                }
                for (ArcAction action : nacts) {
                    olist.add(actsMap.get(action));
                }
                //scores = classifier.computeScores(fArray, olist);
                scores = classifier.computeScoresY(scores, olist);
                if (beamSize != 1) {
                    scores = softmax(scores, olist);
                }
                for (int i = 0; i < scores.length; i++) {
                    fMap.put(Feature.make(Feature.FeatPrefix.nny, Arrays.asList(nacts.get(i))), scores[i]);
                }

                for (ArcAction action : acts) {
                    double score = item.getScore() + model.fv.getScore(fMap, action, isTrain);

                    TempAgenda tmp = new TempAgenda(item, action, fMap, score);
                    addToList(tempAgenda, tmp);
                }
            }

            if (isTrain) {
                goldAct = gActList.get(index + 1);
            }

            List<TempAgenda> newAgendaList = bestAgendaList(tempAgenda);

            for (int i = 0; i < newAgendaList.size(); i++) {
                TempAgenda newAgenda = newAgendaList.get(i);
                PStateItem item = newAgenda.getState();
                ArcAction action = newAgenda.getAction();
                double score = newAgenda.getScore();

                PStateItem nItem = item.copy();
                if (nItem == null) {
                    continue;
                }
                nItem = nItem.applyAction(action, input, djoint, score);

                if (nItem.isFinish(input.size())) {
                    if (candidateOutput == null || nItem.getScore() > candidateOutput.getScore()) {
                        candidateOutput = nItem;
                    }
                } else {
                    list.add(nItem);
                }

                if (i == 0) {
                    parserAct = action;
                    output = nItem;
                }

                if (isTrain) {
                    if (item.getId() == goldId && action.equals(goldAct)) {
                        goldId = nItem.getId();
                        corItem = true;
                    }
                }
            }
            index++;

            if (isTrain) {
                if (parserAct == null) {
                    return candidateOutput;
                }

                if (debug) {
                    System.err.println("\n" + corItem + " Gold Action: " + goldAct + " -- Parser Action: " + parserAct);
                }

                int[] fArray = Ints.toArray(getFeatures(correctState));
                double[] scores = getHScores(fArray);
                //double[] scores = classifier.computeHidden(fArray);
                //scores = softmax(scores);
                gfMap = new HashMap<>();
                for (int i = 0; i < scores.length; i++) {
                    gfMap.put(Feature.make(Feature.FeatPrefix.nn, Arrays.asList(i)), scores[i]);
                }

                acts = getAction(correctState);
                nacts = new ArrayList<>(acts.size());
                List<Integer> olist = new ArrayList<>(acts.size());
                for (ArcAction action : acts) {
                    if (actsMap.containsKey(action)) {
                        nacts.add(action);
                    }
                }
                for (ArcAction action : nacts) {
                    olist.add(actsMap.get(action));
                }
                //scores = classifier.computeScores(fArray, olist);
                scores = classifier.computeScoresY(scores, olist);
                if (beamSize != 1) {
                    scores = softmax(scores, olist);
                }
                for (int i = 0; i < scores.length; i++) {
                    gfMap.put(Feature.make(Feature.FeatPrefix.nny, Arrays.asList(nacts.get(i))), scores[i]);
                }

                double score = correctState.getScore() + model.fv.getScore(gfMap, goldAct, isTrain);
                correctState = correctState.applyAction(goldAct, input, djoint, score);
                if (corItem == false) {
                    updateScores(correctState, 1);
                    updateScores(output, -1);
                    if (early_update) {
                        agenda.clear();
                        return candidateOutput;
                    } else {
                        agenda.clear();
                        if (correctState.isFinish(input.size())) {
                            return candidateOutput;
                        }
                        agenda.add(correctState);
                        goldId = correctState.getId();
                    }
                } else {
                    agenda.clear();
                    agenda.addAll(list);
                }

                if (index == gActList.size() - 1) {
                    return candidateOutput;
                }
            } else {
                agenda.clear();
                agenda.addAll(list);
                if (debug) //System.err.println(parserAct+" "+acts);
                {
                    System.err.println("\n" + parserAct);
                }
            }
        }

        if (isTrain && candidateOutput.getId() != goldId) {
            updateScores(correctState, 1);
            updateScores(candidateOutput, -1);
        }

        if (candidateOutput == null) {
            return output;
        } else {
            return candidateOutput;
        }
    }

    private double[] softmax(double[] scores) {

        double total = 0.0;
        for (int i = 0; i < scores.length; i++) {
            total += Math.exp(scores[i]);
        }

        for (int i = 0; i < scores.length; i++) {
            scores[i] = Math.exp(scores[i] - Math.log(total));
        }

        return scores;
    }

    private void updateScores(PStateItem root, int update) {
        PStateItem cur = root;
        PStateItem prevState = cur.getStatePtr();
        while (prevState != null) {
            ArcAction act = cur.getArcAction();

            int[] fArray = Ints.toArray(getFeatures(prevState));
            double[] scores = getHScores(fArray);
            //double[] scores = classifier.computeHidden(fArray);
            //scores = softmax(scores);
            HashMap<Feature, Double> featMap = new HashMap<>();
            for (int i = 0; i < scores.length; i++) {
                featMap.put(Feature.make(Feature.FeatPrefix.nn, Arrays.asList(i)), scores[i]);
            }

            ArrayList<ArcAction> acts, nacts;
            acts = getAction(prevState);
            nacts = new ArrayList<>(acts.size());
            List<Integer> olist = new ArrayList<>(acts.size());
            for (ArcAction action : acts) {
                if (actsMap.containsKey(action)) {
                    nacts.add(action);
                }
            }
            for (ArcAction action : nacts) {
                olist.add(actsMap.get(action));
            }
            //scores = classifier.computeScores(fArray, olist);
            scores = classifier.computeScoresY(scores, olist);
            if (beamSize != 1) {
                scores = softmax(scores, olist);
            }
            for (int i = 0; i < scores.length; i++) {
                featMap.put(Feature.make(Feature.FeatPrefix.nny, Arrays.asList(nacts.get(i))), scores[i]);
            }

            model.updateWeights(featMap, act, update, sSize);
            cur = prevState;
            prevState = cur.getStatePtr();
        }
    }
    
    private double[] getHScores(int[] fArray){
        double[] scores;
        /*
        long hash = getHashCode(fArray);        
        if (fvHash.containsKey(hash)) {
            scores = fvHash.get(hash);
        } else {
            scores = classifier.computeHidden(fArray);
            if (fvHashFreq.containsKey(hash)) {
                int val = fvHashFreq.get(hash);
                if (val == 2)
                    fvHash.put(hash, scores);
                else
                    fvHashFreq.put(hash, val + 1);
            } else {
                fvHashFreq.put(hash, 1);
            }
        }
        */
        scores = classifier.computeHidden(fArray);
        return scores;
    }
    
    private long getHashCode(int[] fArray){
        long hash = 0;
        for(int i : fArray)
            hash = 31*hash + i;
        return hash;
    }

    private void addToList(List<TempAgenda> list, TempAgenda item) {
        int i = 0, j = 0;
        while (i < list.size()) {
            if (item.getScore() > list.get(i).getScore()) {
                break;
            }
            i++;
        }
        list.add(i, item);
    }

    private List<TempAgenda> bestAgendaList(List<TempAgenda> list) {
        List<TempAgenda> nList = new ArrayList<>();
        for (int i = 0; i < beamSize && i < list.size(); i++) {
            nList.add(list.get(i));
        }
        return nList;
    }

    private ArrayList<ArcAction> getAction(PStateItem state) {
        ArrayList<ArcAction> actions;
        CCGTreeNode left, right, inode;
        left = right = inode = null;
        int stacksize = state.stacksize();
        if (state.getCurrentWrd() < input.size()) {
            inode = input.get(state.getCurrentWrd());
        }
        if (stacksize > 1) {
            left = state.getStackPtr().getNode();
        }
        if (stacksize >= 1) {
            right = state.getNode();
        }

        actions = srparser.treebankRules.getActions(left, right, inode, djoint);

        return actions;
    }

    public int getWordID(String s) {
        return wordIDs.containsKey(s) ? wordIDs.get(s) : wordIDs.get(Config.UNKNOWN);
    }

    public int getPosID(String s) {
        return posIDs.containsKey(s) ? posIDs.get(s) : posIDs.get(Config.UNKNOWN);
    }

    public int getCCGCatID(String s) {
        return ccgcatIDs.containsKey(s) ? ccgcatIDs.get(s) : ccgcatIDs.get(Config.NULL);
    }

    private void updateHeadFeatures(CCGTreeNode node, List<Integer> fLabel) {
        if (node != null) {
            fLabel.add(getCCGCatID(node.getConllNode().getccgCat().toString()));
        } else {
            fLabel.add(getCCGCatID("-NULL-"));
        }
    }

    private void updateStackFeatures(CCGTreeNode node, List<Integer> fWord, List<Integer> fPos, List<Integer> fLabel) {
        if (node != null) {
            fWord.add(getWordID(node.getWrdStr()));
            fPos.add(getPosID(node.getPOS().toString()));
            fLabel.add(getCCGCatID(node.getCCGcat().toString()));
        } else {
            fWord.add(getWordID("-NULL-"));
            fPos.add(getPosID("-NULL-"));
            fLabel.add(getCCGCatID("-NULL-"));
        }
    }

    private void updateInputFeatures(CCGTreeNode node, List<Integer> fWord, List<Integer> fPos) {
        if (node != null) {
            fWord.add(getWordID(node.getWrdStr()));
            fPos.add(getPosID(node.getPOS().toString()));
        } else {
            fWord.add(getWordID("-NULL-"));
            fPos.add(getPosID("-NULL-"));
        }
    }

    public List<Integer> getFeatures(PStateItem curState) {
        // Presize the arrays for very slight speed gain. Hardcoded, but so is the current feature list.
        List<Integer> fWord = new ArrayList<>(12);
        List<Integer> fPos = new ArrayList<>(12);
        List<Integer> fLabel = new ArrayList<>(8);

        CCGTreeNode s0, s1, s2, s3;
        CCGTreeNode s0l, s0r, s0u, s0h;
        CCGTreeNode s1l, s1r, s1u, s1h;
        CCGTreeNode q0, q1, q2, q3;

        int stacksize = curState.stacksize();
        s0 = stacksize < 1 ? null : curState.getNode();
        s1 = stacksize < 2 ? null : curState.getStackPtr().getNode();
        s2 = stacksize < 3 ? null : curState.getStackPtr().getStackPtr().getNode();
        s3 = stacksize < 4 ? null : curState.getStackPtr().getStackPtr().getStackPtr().getNode();

        int inputsize = input.size(), cwrdid = curState.getCurrentWrd();

        q0 = cwrdid >= inputsize ? null : input.get(cwrdid);
        q1 = cwrdid + 1 >= inputsize ? null : input.get(cwrdid + 1);
        q2 = cwrdid + 2 >= inputsize ? null : input.get(cwrdid + 2);
        q3 = cwrdid + 3 >= inputsize ? null : input.get(cwrdid + 3);

        if (s0 != null) {
            s0l = s0.getLeftChild();
            s0r = s0.getRightChild();
            s0h = (s0.getHeadDir() == 0) ? s0l : s0r;
        } else {
            s0l = null;
            s0r = null;
            s0h = null;
        }

        if (s1 != null) {
            s1l = s1.getLeftChild();
            s1r = s1.getRightChild();
            s1h = (s1.getHeadDir() == 0) ? s1l : s1r;
        } else {
            s1l = null;
            s1r = null;
            s1h = null;
        }

        updateStackFeatures(s0, fWord, fPos, fLabel);
        updateStackFeatures(s1, fWord, fPos, fLabel);
        updateStackFeatures(s2, fWord, fPos, fLabel);
        updateStackFeatures(s3, fWord, fPos, fLabel);
        updateStackFeatures(s0l, fWord, fPos, fLabel);
        updateStackFeatures(s0r, fWord, fPos, fLabel);
        updateStackFeatures(s1l, fWord, fPos, fLabel);
        updateStackFeatures(s1r, fWord, fPos, fLabel);
        updateHeadFeatures(s0h, fLabel);
        updateHeadFeatures(s1h, fLabel);
        updateInputFeatures(q0, fWord, fPos);
        updateInputFeatures(q1, fWord, fPos);
        updateInputFeatures(q2, fWord, fPos);
        updateInputFeatures(q3, fWord, fPos);

        List<Integer> feature = new ArrayList<>(34);
        feature.addAll(fWord);
        feature.addAll(fPos);
        feature.addAll(fLabel);
        return feature;
    }

    public HashMap<Integer, CCGSentence> getccgDepMap(BufferedReader iReader) throws IOException {
        String line;
        int sentcount = 0;
        HashMap<Integer, CCGSentence> ccgDeps = new HashMap<>();
        HashMap<String, CCGDepInfo> tmpmap = new HashMap<>();
        CCGSentence tmpsent = new CCGSentence();
        while ((line = iReader.readLine()) != null) {
            if (line.startsWith("# ") || line.trim().isEmpty()) {
                continue;
            }
            if (line.startsWith("<s id")) {
                tmpmap = new HashMap<>();
                tmpsent = getSentInfo(line);
                sentcount++;
            } else if (line.startsWith("<\\s")) {
                //ccgDeps.put(sentcount, tmpmap);
                tmpsent.setpargdeps(tmpmap);
                ccgDeps.put(sentcount, tmpsent);
            } else {
                String parts[] = line.trim().replaceAll("[ \t]+", "\t").split("\t");
                int hid = Integer.parseInt(parts[1]) + 1;
                int aid = Integer.parseInt(parts[0]) + 1;
                String key = hid + "--" + aid;
                CCGDepInfo info = new CCGDepInfo(hid, aid, Integer.parseInt(parts[3]), parts[2], false);
                tmpmap.put(key, info);
            }
        }
        return ccgDeps;
    }

    private CCGSentence getSentInfo(String line) {
        String[] parts = line.split("<c>|</c>")[1].split(" ");
        CCGSentence tmpsent = new CCGSentence();
        for (int i = 0; i < parts.length; i++) {
            String[] wpc = parts[i].split("\\|");
            CCGTreeNode lnode = CCGTreeNode.makeLeaf(CCGCategory.make(wpc[2]), new SCoNLLNode(i + 1, wpc[0], wpc[1], wpc[2]));
            tmpsent.addCCGTreeNode(lnode);
        }
        return tmpsent;
    }

    public void fillData() throws Exception {
        srparser.fillData(trainCoNLLFile, trainAutoFile, trainAutoFile, goldDetails);
    }

    public void saveModel(String modelFile) throws IOException {

        BufferedWriter output = new BufferedWriter(new FileWriter(new File(modelFile)));

        HashMap<String, ArrayList<CCGRuleInfo>> uRules = srparser.treebankRules.getUnaryRules();
        HashMap<String, ArrayList<CCGRuleInfo>> bRules = srparser.treebankRules.getBinaryRules();
        List<ArcAction> acts = srparser.actsList;

        output.write("Classes=" + acts.size() + "\n");
        output.write("UnaryRules=" + uRules.size() + "\n");
        output.write("BinaryRules=" + bRules.size() + "\n");

        // Classes
        for (ArcAction act : acts) {
            output.write(act.toString() + "\n");
        }

        // Unary and Binary Rules
        for (String key : uRules.keySet()) {
            ArrayList<CCGRuleInfo> list = uRules.get(key);
            output.write(key);
            for (CCGRuleInfo info : list) {
                output.write("  " + info.toString());
            }
            output.write("\n");
        }

        for (String key : bRules.keySet()) {
            ArrayList<CCGRuleInfo> list = bRules.get(key);
            output.write(key);
            for (CCGRuleInfo info : list) {
                output.write("  " + info.toString());
            }
            output.write("\n");
        }

        HashMap<Feature, HashMap<ArcAction, Weight>> fv = model.fv.getFeatureVector();
        SortedSet<Feature> keys = new TreeSet<>(fv.keySet());

        for (Feature key : keys) {
            StringBuilder sb = new StringBuilder();
            //System.err.println(key);
            HashMap<ArcAction, Weight> map = fv.get(key);
            SortedSet<ArcAction> keys2 = new TreeSet<>(map.keySet());
            for (ArcAction act : keys2) {
                Weight wt = map.get(act);
                if (wt.rawWeight != 0 || wt.totalWeight != 0) {
                    sb.append(act.toString());
                    sb.append(" : ");
                    sb.append(map.get(act).rawWeight);
                    sb.append(" / ");
                    sb.append(map.get(act).totalWeight);
                    sb.append(" ");
                }
            }
            if (!sb.toString().isEmpty()) {
                output.write(key + "\t");
                output.write(sb.toString().trim());
                output.write("\n");
            }
        }
        output.close();
    }

    public void loadModel(String modelFile) throws IOException {

        BufferedReader modelReader = new BufferedReader(new FileReader(new File(modelFile)));

        String line;

        line = modelReader.readLine();
        int classes = Integer.parseInt(line.substring(line.indexOf('=') + 1));
        line = modelReader.readLine();
        int nuRules = Integer.parseInt(line.substring(line.indexOf('=') + 1));
        line = modelReader.readLine();
        int nbRules = Integer.parseInt(line.substring(line.indexOf('=') + 1));

        String[] splits;

        for (int k = 0; k < classes; k++) {
            line = modelReader.readLine().trim();
            splits = line.split("--");
            srparser.actsMap.put(ArcAction.make(SRAction.valueOf(splits[0]), splits[1]), k);
        }

        for (int k = 0; k < nuRules; k++) {
            line = modelReader.readLine().trim();
            splits = line.split("  ");
            String key = splits[0];
            for (int i = 1; i < splits.length; i++) {
                String[] parts = splits[i].split("--");
                //CCGRuleInfo info = new CCGRuleInfo(CCGCategory.make(parts[0]), null, CCGCategory.make(parts[2]), parts[3].equals("true"), 0);
                CCGRuleInfo info = new CCGRuleInfo(CCGCategory.make(parts[0]), null, CCGCategory.make(parts[2]), parts[3].equals("true"), DepLabel.make(parts[4]), 0);
                srparser.treebankRules.addUnaryRuleInfo(info, key);
            }
        }

        for (int k = 0; k < nbRules; k++) {
            line = modelReader.readLine().trim();
            splits = line.split("  ");
            String key = splits[0];
            for (int i = 1; i < splits.length; i++) {
                String[] parts = splits[i].split("--");
                CCGRuleInfo info = new CCGRuleInfo(CCGCategory.make(parts[0]), CCGCategory.make(parts[1]), CCGCategory.make(parts[2]), parts[3].equals("true"), DepLabel.make(parts[4]), 0);
                srparser.treebankRules.addBinaryRuleInfo(info, key);
            }
        }

        HashMap<Feature, HashMap<ArcAction, Weight>> featVector = new HashMap<>();

        while ((line = modelReader.readLine()) != null) {
            line = line.trim();
            String[] parts = line.split("\t");
            if (parts.length != 2) {
                continue;
            }
            Feature key = Feature.make(parts[0]);
            String[] items = parts[1].split(" ");
            HashMap<ArcAction, Weight> map = new HashMap<>();
            for (int i = 0; i < items.length; i += 5) {
                String[] actstr = items[i].split("--");
                ArcAction act;
                if (actstr.length == 3) {
                    act = ArcAction.make(SRAction.valueOf(actstr[0]), actstr[1], DepLabel.make(actstr[2]));
                } else {
                    act = ArcAction.make(SRAction.valueOf(actstr[0]), actstr[1]);
                }
                Weight wt = new Weight(Double.parseDouble(items[i + 2]), Double.parseDouble(items[i + 4]), -1, 1);
                map.put(act, wt);
            }
            featVector.put(key, map);
        }
        modelReader.close();
        model.fv = new FeatureVector(featVector);
    }

    private void loadNNModelFile(String modelFile, boolean verbose) throws IOException {
        Timing t = new Timing();
        try {

            System.err.println("Loading ccg parser model file: " + modelFile + " ... ");
            String s;
            BufferedReader input = IOUtils.readerFromString(modelFile);

            s = input.readLine();
            int nDict = Integer.parseInt(s.substring(s.indexOf('=') + 1));
            s = input.readLine();
            int nPOS = Integer.parseInt(s.substring(s.indexOf('=') + 1));
            s = input.readLine();
            int nccgCat = Integer.parseInt(s.substring(s.indexOf('=') + 1));
            s = input.readLine();
            int eSize = Integer.parseInt(s.substring(s.indexOf('=') + 1));
            s = input.readLine();
            int hSize = Integer.parseInt(s.substring(s.indexOf('=') + 1));
            s = input.readLine();
            int nTokens = Integer.parseInt(s.substring(s.indexOf('=') + 1));
            s = input.readLine();
            int nPreComputed = Integer.parseInt(s.substring(s.indexOf('=') + 1));
            s = input.readLine();
            int classes = Integer.parseInt(s.substring(s.indexOf('=') + 1));
            s = input.readLine();
            int nuRules = Integer.parseInt(s.substring(s.indexOf('=') + 1));
            s = input.readLine();
            int nbRules = Integer.parseInt(s.substring(s.indexOf('=') + 1));

            actsMap = new HashMap<>();
            knownWords = new ArrayList<>();
            knownPos = new ArrayList<>();
            knownCCGCats = new ArrayList<>();
            //srparser = new NonInc();

            double[][] E = new double[nDict + nPOS + nccgCat][eSize];
            String[] splits;
            int index = 0;

            for (int k = 0; k < classes; k++) {
                s = input.readLine().trim();
                splits = s.split("--");
                actsMap.put(ArcAction.make(Utils.SRAction.valueOf(splits[0]), splits[1]), k);
            }
            //srparser.updateActMap();

            for (int k = 0; k < nuRules; k++) {
                s = input.readLine().trim();
                splits = s.split("  ");
                String key = splits[0];
                for (int i = 1; i < splits.length; i++) {
                    String[] parts = splits[i].split("--");
                    CCGRuleInfo info = new CCGRuleInfo(CCGCategory.make(parts[0]), null, CCGCategory.make(parts[2]), parts[3].equals("true"), DepLabel.make(parts[4]), 0);
                    srparser.treebankRules.addUnaryRuleInfo(info, key);
                }
            }

            for (int k = 0; k < nbRules; k++) {
                s = input.readLine().trim();
                splits = s.split("  ");
                String key = splits[0];
                for (int i = 1; i < splits.length; i++) {
                    String[] parts = splits[i].split("--");
                    CCGRuleInfo info = new CCGRuleInfo(CCGCategory.make(parts[0]), CCGCategory.make(parts[1]), CCGCategory.make(parts[2]), parts[3].equals("true"), DepLabel.make(parts[4]), 0);
                    srparser.treebankRules.addBinaryRuleInfo(info, key);
                }
            }

            for (int k = 0; k < nDict; ++k) {
                s = input.readLine();
                splits = s.split(" ");
                knownWords.add(splits[0]);
                for (int i = 0; i < eSize; ++i) {
                    E[index][i] = Double.parseDouble(splits[i + 1]);
                }
                index = index + 1;
            }
            for (int k = 0; k < nPOS; ++k) {
                s = input.readLine();
                splits = s.split(" ");
                knownPos.add(splits[0]);
                for (int i = 0; i < eSize; ++i) {
                    E[index][i] = Double.parseDouble(splits[i + 1]);
                }
                index = index + 1;
            }
            for (int k = 0; k < nccgCat; ++k) {
                s = input.readLine();
                splits = s.split(" ");
                knownCCGCats.add(splits[0]);
                for (int i = 0; i < eSize; ++i) {
                    E[index][i] = Double.parseDouble(splits[i + 1]);
                }
                index = index + 1;
            }
            generateIDs();

            double[][] W1 = new double[hSize][eSize * nTokens];
            for (int j = 0; j < W1[0].length; ++j) {
                s = input.readLine();
                splits = s.split(" ");
                for (int i = 0; i < W1.length; ++i) {
                    W1[i][j] = Double.parseDouble(splits[i]);
                }
            }

            double[] b1 = new double[hSize];
            s = input.readLine();
            splits = s.split(" ");
            for (int i = 0; i < b1.length; ++i) {
                b1[i] = Double.parseDouble(splits[i]);
            }

            double[][] W2 = new double[classes][hSize];
            for (int j = 0; j < W2[0].length; ++j) {
                s = input.readLine();
                splits = s.split(" ");
                for (int i = 0; i < W2.length; ++i) {
                    W2[i][j] = Double.parseDouble(splits[i]);
                }
            }

            preComputed = new ArrayList<>();
            while (preComputed.size() < nPreComputed) {
                s = input.readLine();
                splits = s.split(" ");
                for (String split : splits) {
                    preComputed.add(Integer.parseInt(split));
                }
            }
            input.close();
            classifier = new Classifier(config, E, W1, b1, W2, preComputed);
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }

        // initialize the loaded parser
        // Pre-compute matrix multiplications
        if (config.numPreComputed > 0) {
            classifier.preCompute();
        }
        t.done("Initializing ccg parser");
    }

    private void generateIDs() {
        wordIDs = new HashMap<>();
        posIDs = new HashMap<>();
        ccgcatIDs = new HashMap<>();

        int index = 0;
        for (String word : knownWords) {
            wordIDs.put(word, (index++));
        }
        for (String pos : knownPos) {
            posIDs.put(pos, (index++));
        }
        for (String label : knownCCGCats) {
            ccgcatIDs.put(label, (index++));
        }
    }

    private static final Map<String, Integer> numArgs = new HashMap<>();

    static {
        numArgs.put("textFile", 1);
        numArgs.put("outFile", 1);
    }

    public static void main(String[] args) throws IOException, Exception {

        String trainAutoFile, trainConllFile, trainPargFile, testAutoFile, testPargFile, testConllFile, outAutoFile, outPargFile, modelFile, nnpermodelFile, embedFile;

        String home = "/home/ambati/ilcc/projects/parsing/experiments/english/ccg/";
        trainAutoFile = home + "data/final/train.gccg.auto";
        trainConllFile = home + "data/final/train.nnccg.conll";
        trainPargFile = home + "data/final/train.gccg.parg";
        trainAutoFile = home + "data/tmp/train.gccg.auto";
        trainConllFile = home + "data/tmp/train.accg.conll";
        trainPargFile = home + "data/tmp/train.gccg.parg";
        //testAutoFile = home+"data/final/devel.gccg.auto";
        testAutoFile = "";
        testPargFile = home + "data/final/devel.gccg.parg";
        testConllFile = home + "data/final/devel.nnccg.conll";
        outAutoFile = home + "models/out1.txt";
        outPargFile = home + "models/out2.txt";
        modelFile = home + "models/nn.model.txt.gz";
        nnpermodelFile = home + "models/nnperccg.model.txt.gz";

        if (args.length == 0) {
            args = new String[]{
                "-trainCoNLL", trainConllFile, "-trainAuto", trainAutoFile, "-trainParg", trainPargFile,
                "-testCoNLL", testConllFile, "-testAuto", testAutoFile, "-testParg", testPargFile,
                "-outAuto", outAutoFile, "-outParg", outPargFile, "-nnmodel", modelFile, "-model", nnpermodelFile,
                "-iters", "1", "-beam", "2", "-isTrain", "true", "-debug", "false", "-early", "true"
            };
        }

        Properties props = StringUtils.argsToProperties(args, numArgs);
        NNPerParser nnperpar = new NNPerParser(props);

        long start;

        System.err.println("Started Training: " + new Date(System.currentTimeMillis()) + "\n");
        start = (long) (System.currentTimeMillis());
        nnperpar.loadNNModelFile(props.getProperty("nnmodel"), true);
        if (props.getProperty("trainCoNLL") != null) {
            nnperpar.fillData();
            nnperpar.train(Integer.parseInt(props.getProperty("iters")));
        }
        System.err.println("Training Time: " + (System.currentTimeMillis() - start) / 1000.0 + " (s)");

        System.err.println("Loading Model: " + new Date(System.currentTimeMillis()) + "\n");
        nnperpar.loadModel(props.getProperty("model"));

        System.err.println("Started Parsing: " + new Date(System.currentTimeMillis()) + "\n");
        start = (long) (System.currentTimeMillis());
        nnperpar.parse(props.getProperty("testParg"), props.getProperty("testCoNLL"), props.getProperty("outAuto"), props.getProperty("outParg"));
        System.err.println("Parsing Time: " + (System.currentTimeMillis() - start) / 1000.0 + " (s)");
    }
}
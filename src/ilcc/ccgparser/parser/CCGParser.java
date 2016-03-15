/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ilcc.ccgparser.parser;

import edu.stanford.nlp.util.PropertiesUtils;
import ilcc.ccgparser.algos.Joint;
import ilcc.ccgparser.algos.NonInc;
import ilcc.ccgparser.algos.RevInc;
import ilcc.ccgparser.learn.AvePerceptron;
import ilcc.ccgparser.learn.AvePerceptron.FeatureVector;
import ilcc.ccgparser.learn.AvePerceptron.Weight;
import ilcc.ccgparser.learn.Feature;
import ilcc.ccgparser.utils.ArcAction;
import ilcc.ccgparser.utils.CCGDepInfo;
import ilcc.ccgparser.utils.CCGRuleInfo;
import ilcc.ccgparser.utils.CCGRules;
import ilcc.ccgparser.utils.CCGSentence;
import ilcc.ccgparser.utils.CCGTreeNode;
import ilcc.ccgparser.utils.DataTypes.CCGCategory;
import ilcc.ccgparser.utils.DataTypes.DepLabel;
import ilcc.ccgparser.utils.DataTypes.GoldccgInfo;
import ilcc.ccgparser.utils.SCoNLLNode;
import ilcc.ccgparser.utils.Utils.SRAction;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 *
 * @author ambati
 */
public class CCGParser {

    String trainAutoFile, trainCoNLLFile, trainPargFile, testAutoFile, testPargFile, testCoNLLFile, modelFile, outAutoFile, outPargFile;
    AvePerceptron model;

    private final CCGRules rules;
    SRParser srparser;
    HashMap<Integer, GoldccgInfo> goldDetails;

    String algo;
    boolean ftrue, revelf, uncov, isTrain, early_update, incalgo, djoint, lookAhead;
    int beamSize, sSize, debug, iters;

    Stack<CCGTreeNode> stack;
    List<CCGTreeNode> input;
    List<PStateItem> agenda;

    public CCGParser(Properties props) throws IOException {

        rules = new CCGRules();
        model = new AvePerceptron();
        sSize = 0;
        goldDetails = new HashMap<>();
        trainAutoFile = props.getProperty("trainAuto");
        trainCoNLLFile = props.getProperty("trainCoNLL");
        trainPargFile = props.getProperty("trainParg");
        testAutoFile = props.getProperty("testAuto");
        testPargFile = props.getProperty("testParg");
        testCoNLLFile = props.getProperty("testCoNLL");
        outAutoFile = props.getProperty("outAuto");
        outPargFile = props.getProperty("outParg");
        modelFile = props.getProperty("model");

        algo = props.getProperty("algo", "NonInc");
        isTrain = PropertiesUtils.getBool(props, "isTrain", false);
        beamSize = PropertiesUtils.getInt(props, "beam", 1);
        iters = PropertiesUtils.getInt(props, "iters", 10);
        early_update = PropertiesUtils.getBool(props, "early", true);
        lookAhead = PropertiesUtils.getBool(props, "lookAhead", true);
        debug = PropertiesUtils.getInt(props, "debug", 0);

        if (algo.equals("RevInc")) {
            srparser = new RevInc();
        } else if (algo.equals("Joint")) {
            srparser = new Joint(true);
        } else {
            srparser = new NonInc();
        }

        agenda = new ArrayList<>(beamSize);
        incalgo = srparser instanceof RevInc;
        djoint = srparser instanceof Joint;
    }

    public void addRules(String unaryRuleFile, String binaryRuleFile) throws IOException {
        rules.addRules(unaryRuleFile, binaryRuleFile);
    }

    public void fillData() throws Exception {
        srparser.fillData(trainCoNLLFile, trainAutoFile, trainAutoFile, goldDetails);
    }

    public void setModelFile(String mfile) {
        modelFile = mfile;
    }

    public void train() throws Exception {
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

                if (debug >= 1) {
                    System.err.println(sentid);
                }

                parse(goldDetails.get(sentid).getccgSent(), sentid);
            }
            System.err.println();
            System.err.println("Parsing after iter: " + i);
            model.updateFinalWeights(sSize);
            lf = parse();
            if (lf > bestlf) {
                saveModel(modelFile);
                bestlf = lf;
            }
        }
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
            ArrayList<ArcAction> acts;

            List<TempAgenda> tempAgenda = new ArrayList<>();
            for (int itemId = 0; itemId < agenda.size(); itemId++) {
                PStateItem item = agenda.get(itemId);
                Context context = new Context(item, input, djoint);

                HashMap<Feature, Double> fMap;
                fMap = context.getFeatureList(djoint);
                acts = getAction(item);

                if (debug >= 2) {
                    System.err.println(fMap.toString());
                    System.err.println(acts);
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

                if (debug >= 2) {
                    System.err.println("\n" + corItem + " Gold Action: " + goldAct + " -- Parser Action: " + parserAct);
                }

                Context context = new Context(correctState, input, djoint);
                gfMap = context.getFeatureList(djoint);
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
                if (debug >= 2) {
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

    private void updateScores(PStateItem root, int update) {
        PStateItem cur = root;
        PStateItem prevState = cur.getStatePtr();
        while (prevState != null) {
            ArcAction act = cur.getArcAction();
            Context context = new Context(prevState, input, djoint);
            HashMap<Feature, Double> featMap = context.getFeatureList(djoint);
            model.updateWeights(featMap, act, update, sSize);
            cur = prevState;
            prevState = cur.getStatePtr();
        }
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

        // Dep actions
        if (state.depstacksize() >= 2) {
            String lpos = input.get(state.getDepNode().getId() - 1).getPOS().toString();
            String rpos = input.get(state.getDStackPtr().getDepNode().getId() - 1).getPOS().toString();
            List<ArcAction> list = srparser.depRules.get(lpos + "--" + rpos);
            if (list != null) {
                actions.addAll(srparser.depRules.get(lpos + "--" + rpos));
            }
        }

        return actions;
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

    public double parse() throws IOException, Exception {

        BufferedReader conllReader = new BufferedReader(new FileReader(new File(testCoNLLFile)));
        BufferedWriter oAutoWriter = new BufferedWriter(new FileWriter(new File(outAutoFile)));
        BufferedWriter oPargWriter = new BufferedWriter(new FileWriter(new File(outPargFile)));
        oPargWriter.write("# Generated using \n# CCGParser.java\n\n");

        HashMap<Integer, CCGSentence> gcdepsMap = null;
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
            if (id % 100 == 0)
                System.err.print(" " + id);
            if (id % 100 == 0)
                System.err.print(" ");

            try {
                srparser.sent = sent;
                PStateItem output = parse(sent, id);
                if (output.stacksize() == 1)
                    srparser.parsedSents++;
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
                    srparser.updateCatAccuray(gsent, sysCats);
                    srparser.evaluateParseDependenciesJulia(djoint);
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

    public void saveModel(String modelFile) throws IOException {

        BufferedWriter output = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(new File(modelFile))), "UTF-8"));

        HashMap<String, ArrayList<CCGRuleInfo>> uRules = srparser.treebankRules.getUnaryRules();
        HashMap<String, ArrayList<CCGRuleInfo>> bRules = srparser.treebankRules.getBinaryRules();
        List<ArcAction> acts = srparser.actsList;

        output.write("Classes=" + acts.size() + "\n");
        output.write("UnaryRules=" + uRules.size() + "\n");
        output.write("BinaryRules=" + bRules.size() + "\n");

        // Classes
        for (ArcAction act : acts)
            output.write(act.toString()+"\n");

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
                    sb.append(act.toString()); sb.append(" : ");
                    sb.append(map.get(act).rawWeight); sb.append(" / "); sb.append(map.get(act).totalWeight);
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

    public void loadModel() throws IOException {

        BufferedReader modelReader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(new File(modelFile))), "UTF-8"));

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
            if (parts.length != 2)
                continue;
            Feature key = Feature.make(parts[0]);
            String[] items = parts[1].split(" ");
            HashMap<ArcAction, Weight> map = new HashMap<>();
            for (int i = 0; i < items.length; i += 5) {
                String[] actstr = items[i].split("--");
                ArcAction act;
                if (actstr.length == 3)
                    act = ArcAction.make(SRAction.valueOf(actstr[0]), actstr[1], DepLabel.make(actstr[2]));
                else
                    act = ArcAction.make(SRAction.valueOf(actstr[0]), actstr[1]);
                Weight wt = new Weight(Double.parseDouble(items[i+2]), Double.parseDouble(items[i+4]), -1, 1);
                map.put(act, wt);
            }
            featVector.put(key, map);
        }
        modelReader.close();
        model.fv = new FeatureVector(featVector);
    }
}
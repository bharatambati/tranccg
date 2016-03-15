/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ilcc.ccgparser.nnparser;

import com.google.common.primitives.Ints;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Timing;
import ilcc.ccgparser.algos.NonInc;
import ilcc.ccgparser.parser.PStateItem;
import ilcc.ccgparser.parser.SRParser;
import ilcc.ccgparser.utils.ArcAction;
import ilcc.ccgparser.utils.CCGDepInfo;
import ilcc.ccgparser.utils.CCGRuleInfo;
import ilcc.ccgparser.utils.CCGSentence;
import ilcc.ccgparser.utils.CCGTreeNode;
import ilcc.ccgparser.utils.DataTypes;
import ilcc.ccgparser.utils.DataTypes.CCGCategory;
import ilcc.ccgparser.utils.DataTypes.DepLabel;
import ilcc.ccgparser.utils.DataTypes.GoldccgInfo;
import ilcc.ccgparser.utils.SCoNLLNode;
import ilcc.ccgparser.utils.Utils;
import ilcc.ccgparser.utils.Utils.SRAction;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Stack;

/**
 *
 * @author ambati
 */
public class CCGNNParser {

    String trainCoNLLFile, trainAutoFile, trainPargFile, testCoNLLFile, testAutoFile, testPargFile, modelFile, outAutoFile, outPargFile;

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

    public CCGNNParser(Properties props) throws IOException {
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
        early_update = PropertiesUtils.getBool(props, "early", false);
        modelFile = props.getProperty("model");

        goldDetails = new HashMap<>();
        goldccgDeps = new HashMap<>();
        srparser = new NonInc();
        actsMap = new HashMap<>();
    }

    public class TempNNAgenda {

        private final PStateItem state;
        private final ArcAction action;
        private final int[] featArray;
        private final double score;

        public TempNNAgenda(PStateItem item, ArcAction act, int[] feats, double val) {
            state = item;
            action = act;
            featArray = feats;
            score = val;
        }

        public double getScore() {
            return score;
        }

        public PStateItem getState() {
            return state;
        }

        public ArcAction getAction() {
            return action;
        }

        public int[] getFeatArray() {
            return featArray;
        }
    }

    /**
     * Load a parser model file, printing out some messages about the grammar in
     * the file.
     *
     * @param modelFile The file (classpath resource, etc.) to load the model
     * from.
     */
    public void loadModelFile(String modelFile) throws IOException {
        loadModelFile(modelFile, true, null);
    }

    private void loadModelFile(String modelFile, boolean verbose, Dataset trainSet) throws IOException {
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
            srparser = new NonInc();

            double[][] E = new double[nDict + nPOS + nccgCat][eSize];
            String[] splits;
            int index = 0;

            for (int k = 0; k < classes; k++) {
                s = input.readLine().trim();
                splits = s.split("--");
                actsMap.put(ArcAction.make(SRAction.valueOf(splits[0]), splits[1]), k);
            }

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
            if(trainSet == null)
                classifier = new Classifier(config, E, W1, b1, W2, preComputed);
            else
                classifier = new Classifier(config, trainSet, E, W1, b1, W2, preComputed);
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

    private void loadCCGFiles(String conllFile, String derivFile, String depsFile, List<CCGSentence> sents, List<CCGTreeNode> trees) throws IOException, Exception {

        BufferedReader conllReader = new BufferedReader(new FileReader(conllFile));
        BufferedReader derivReader = new BufferedReader(new FileReader(derivFile));
        //BufferedReader depReader = new BufferedReader(new FileReader(depsFile));
        String dLine;
        ArrayList<String> cLines;

        while (derivReader.readLine() != null) {
            updateSentCount();
            //getCCGDepsParg(depReader);
            cLines = Utils.getConll(conllReader);
            CCGSentence sent = new CCGSentence();
            dLine = Utils.getccgDeriv(derivReader);
            CCGTreeNode root = Utils.parseDrivString(dLine, sent);
            sent.updateCCGCats(cLines);
            sent.updateConllDeps(cLines);
            List<ArcAction> actslist = parseGold(sent);
            sents.add(sent);
            trees.add(root);
            goldDetails.put(sentCount, new GoldccgInfo(actslist, goldccgDeps, sent));
        }
        updateActMap();
    }

    private void updateActMap() {
        HashMap<ArcAction, Integer> map = new HashMap();
        actsList = new ArrayList<>(actsMap.size());
        actsList.addAll(actsMap.keySet());
        Collections.sort(actsList);
        for (int i = 0; i < actsList.size(); i++) {
            map.put(actsList.get(i), i);
        }
        actsMap = map;
    }

    protected List<ArcAction> parseGold(CCGSentence sent) throws Exception {
        CCGTreeNode root = sent.getDerivRoot();
        ArrayList<ArcAction> gActs = new ArrayList<>();
        ArrayList<CCGTreeNode> list = new ArrayList<>();
        postOrder(root, list);
        for (CCGTreeNode node : list) {
            if (node.isLeaf()) {
                gActs.add(ArcAction.make(Utils.SRAction.SHIFT, node.getCCGcat().toString()));
            } else if (node.getChildCount() == 1) {
                DataTypes.CCGCategory lcat = node.getLeftChild().getCCGcat();
                DataTypes.CCGCategory rescat = DataTypes.CCGCategory.make(node.getCCGcat().toString());
                CCGRuleInfo info = new CCGRuleInfo(lcat, null, rescat, true, -1);
                srparser.treebankRules.addUnaryRuleInfo(info, lcat.toString());
                gActs.add(ArcAction.make(Utils.SRAction.RU, node.getCCGcat().toString()));

            } else {
                CCGTreeNode left = node.getLeftChild(), right = node.getRightChild();
                DataTypes.CCGCategory lcat = left.getCCGcat(), rcat = right.getCCGcat();
                DataTypes.CCGCategory rescat = DataTypes.CCGCategory.make(node.getCCGcat().toString());
                CCGRuleInfo info = new CCGRuleInfo(lcat, rcat, rescat, (node.getHeadDir() == 0), 0);
                srparser.treebankRules.addBinaryRuleInfo(info, lcat.toString() + " " + rcat.toString());

                if (node.getHeadDir() == 0) {
                    gActs.add(ArcAction.make(Utils.SRAction.RR, node.getCCGcat().toString()));
                } else {
                    gActs.add(ArcAction.make(Utils.SRAction.RL, node.getCCGcat().toString()));
                }
            }
        }
        addtoactlist(gActs);
        return gActs;
    }

    private void addtoactlist(ArrayList<ArcAction> gActs) {
        for (ArcAction act : gActs) {
            Integer counter = actsMap.get(act);
            actsMap.put(act, counter == null ? 1 : counter + 1);
        }
    }

    public void postOrder(CCGTreeNode root, List list) {
        if (root == null) {
            return;
        }
        postOrder(root.getLeftChild(), list);
        postOrder(root.getRightChild(), list);
        list.add(root);
    }

    private void updateSentCount() {
        sentCount++;
        if (sentCount % 1000 == 0) {
            System.err.println(sentCount);
        } else if (sentCount % 100 == 0) {
            System.err.print(sentCount + " ");
        }
    }

    private void genDictionaries(List<CCGSentence> sents, List<CCGTreeNode> trees) {
        List<String> word = new ArrayList<>();
        List<String> pos = new ArrayList<>();
        List<String> ccgcat = new ArrayList<>();

        for (CCGSentence sentence : sents) {
            for (CCGTreeNode node : sentence.getNodes()) {
                word.add(node.getWrdStr());
                pos.add(node.getPOS().getPos());
            }
        }

        for (CCGTreeNode tree : trees) {
            ArrayList<CCGTreeNode> list = new ArrayList<>();
            postOrder(tree, list);
            for (CCGTreeNode node : list) {
                ccgcat.add(node.getCCGcat().getCatStr());
            }
        }

        // Generate "dictionaries," possibly with frequency cutoff
        knownWords = Util.generateDict(word, config.wordCutOff);
        knownPos = Util.generateDict(pos);
        knownCCGCats = Util.generateDict(ccgcat);

        knownWords.add(0, Config.UNKNOWN);
        knownWords.add(1, Config.NULL);
        knownWords.add(2, Config.ROOT);

        knownPos.add(0, Config.UNKNOWN);
        knownPos.add(1, Config.NULL);
        knownPos.add(2, Config.ROOT);

        knownCCGCats.add(0, Config.NULL);
        generateIDs();

        System.err.println(Config.SEPARATOR);
        System.err.println("#Word: " + knownWords.size());
        System.err.println("#POS:" + knownPos.size());
        System.err.println("#CCGCats: " + knownCCGCats.size());
    }

    /**
     * Generate unique integer IDs for all known words, pos/ccg-tags
     *
     * All three of the aforementioned types are assigned IDs from a continuous
     * range of integers; all IDs 0 <= ID < n_w are word IDs, all IDs n_w <= ID
     * < n_w + n_pos are POS tag IDs, and so on.
     */
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

    private double[][] readEmbedFile(String embedFile, Map<String, Integer> embedID) {

        double[][] embeddings = null;
        if (embedFile != null) {
            BufferedReader input = null;
            try {
                input = IOUtils.readerFromString(embedFile);
                List<String> lines = new ArrayList<String>();
                for (String s; (s = input.readLine()) != null;) {
                    lines.add(s);
                }

                int nWords = lines.size();
                String[] splits = lines.get(0).split("\\s+");

                int dim = splits.length - 1;
                embeddings = new double[nWords][dim];
                System.err.println("Embedding File " + embedFile + ": #Words = " + nWords + ", dim = " + dim);

                if (dim != config.embeddingSize) {
                    throw new IllegalArgumentException("The dimension of embedding file does not match config.embeddingSize");
                }

                for (int i = 0; i < lines.size(); ++i) {
                    splits = lines.get(i).split("\\s+");
                    embedID.put(splits[0], i);
                    for (int j = 0; j < dim; ++j) {
                        embeddings[i][j] = Double.parseDouble(splits[j + 1]);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeIOException(e);
            } finally {
                IOUtils.closeIgnoringExceptions(input);
            }
        }
        return embeddings;
    }

    /**
     * Get an integer ID for the given word. This ID can be used to index into
     * the embeddings {@link #embeddings}.
     *
     * @return An ID for the given word, or an ID referring to a generic
     * "unknown" word if the word is unknown
     */
    public int getWordID(String s) {
        return wordIDs.containsKey(s) ? wordIDs.get(s) : wordIDs.get(Config.UNKNOWN);
    }

    public int getPosID(String s) {
        return posIDs.containsKey(s) ? posIDs.get(s) : posIDs.get(Config.UNKNOWN);
    }

    public int getCCGCatID(String s) {
        return ccgcatIDs.get(s);
    }

    // Prepare a classifier for training with the given dataset.
    private void setupClassifierForTraining(List<CCGSentence> trainSents, List<CCGTreeNode> trainTrees, String embedFile, String preModel) {
        double[][] E = new double[knownWords.size() + knownPos.size() + knownCCGCats.size()][config.embeddingSize];
        double[][] W1 = new double[config.hiddenSize][config.embeddingSize * config.numTokens];
        double[] b1 = new double[config.hiddenSize];
        double[][] W2 = new double[actsList.size()][config.hiddenSize];

        // Randomly initialize weight matrices / vectors
        Random random = Util.getRandom();
        for (int i = 0; i < W1.length; ++i) {
            for (int j = 0; j < W1[i].length; ++j) {
                W1[i][j] = random.nextDouble() * 2 * config.initRange - config.initRange;
            }
        }

        for (int i = 0; i < b1.length; ++i) {
            b1[i] = random.nextDouble() * 2 * config.initRange - config.initRange;
        }

        for (int i = 0; i < W2.length; ++i) {
            for (int j = 0; j < W2[i].length; ++j) {
                W2[i][j] = random.nextDouble() * 2 * config.initRange - config.initRange;
            }
        }

        // Read embeddings into `embedID`, `embeddings`
        Map<String, Integer> embedID = new HashMap<String, Integer>();
        double[][] embeddings = readEmbedFile(embedFile, embedID);

        // Try to match loaded embeddings with words in dictionary
        int foundEmbed = 0;
        for (int i = 0; i < E.length; ++i) {
            int index = -1;
            if (i < knownWords.size()) {
                String str = knownWords.get(i);
                //NOTE: exact match first, and then try lower case..
                if (embedID.containsKey(str)) {
                    index = embedID.get(str);
                } else if (embedID.containsKey(str.toLowerCase())) {
                    index = embedID.get(str.toLowerCase());
                }
            }
            if (index >= 0) {
                ++foundEmbed;
                for (int j = 0; j < E[i].length; ++j) {
                    E[i][j] = embeddings[index][j];
                }
            } else {
                for (int j = 0; j < E[i].length; ++j) //E[i][j] = random.nextDouble() * config.initRange * 2 - config.initRange;
                //E[i][j] = random.nextDouble() * 0.2 - 0.1;
                //E[i][j] = random.nextGaussian() * Math.sqrt(0.1);
                {
                    E[i][j] = random.nextDouble() * 0.02 - 0.01;
                }
            }
        }
        System.err.println("Found embeddings: " + foundEmbed + " / " + knownWords.size());        
        Dataset trainSet = genTrainExamples(trainSents, trainTrees);

        if (preModel != null) {
            try {
                System.err.println("Loading pre-trained model file: " + preModel + " ... ");                
                loadModelFile(preModel, true, trainSet);
            } catch (IOException e) {
                throw new RuntimeIOException(e);
            }
        }
        else{
            classifier = new Classifier(config, trainSet, E, W1, b1, W2, preComputed);
        }
    }

    public Dataset genTrainExamples(List<CCGSentence> sents, List<CCGTreeNode> trees) {
        int numTrans = actsList.size();
        Dataset ret = new Dataset(config.numTokens, numTrans);

        Counter<Integer> tokPosCount = new IntCounter<>();
        System.err.println(Config.SEPARATOR);
        System.err.println("Generate training examples...");
        System.err.println("With #transitions: " + numTrans);
        double start = (long) (System.currentTimeMillis()), end;
        System.err.println("Started at: " + new Date(System.currentTimeMillis()));

        for (int i = 0; i < sents.size(); ++i) {
            if (i > 0) {
                if (i % 1000 == 0) {
                    System.err.print(i + " ");
                }
                if (i % 10000 == 0 || i == sents.size() - 1) {
                    System.err.println();
                }
            }

            //if (trees.get(i).isProjective()) {
            input = sents.get(i).getNodes();
            PStateItem stateitem = new PStateItem();
            List<ArcAction> gActList = goldDetails.get(i + 1).getarcActs();
            for (ArcAction gAct : gActList) {
                ArrayList<ArcAction> acts = getAction(stateitem);

                List<Integer> feature = getFeatures(stateitem);

                ///*
                List<Integer> label = new ArrayList<>(Collections.nCopies(numTrans, -1));
                for (ArcAction act : acts) {
                    Integer id = actsMap.get(act);
                    if (id != null) {
                        if (act.equals(gAct)) {
                            label.set(id, 1);
                        } else {
                            label.set(id, 0);
                        }
                    }
                }

                ret.addExample(feature, label);
                for (int j = 0; j < feature.size(); ++j) {
                    tokPosCount.incrementCount(feature.get(j) * feature.size() + j);
                }
                stateitem = stateitem.applyAction(gAct, input, djoint, 0.0);
            }
            //}
        }
        System.err.println("#Train Examples: " + ret.n);
        end = (long) System.currentTimeMillis();
        System.err.println("Ended at : " + new Date(System.currentTimeMillis()) + " taking " + 0.001 * (end - start) + " secs");

        List<Integer> sortedTokens = Counters.toSortedList(tokPosCount, false);
        preComputed = new ArrayList<>(sortedTokens.subList(0, Math.min(config.numPreComputed, sortedTokens.size())));

        return ret;
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

        /*
         // Dep actions
         if(state.depstacksize()>=2){
         String lpos = input.get(state.getDepNode().getId()-1).getPOS().toString();
         String rpos = input.get(state.getDStackPtr().getDepNode().getId()-1).getPOS().toString();
         List<ArcAction> list = srparser.depRules.get(lpos+"--"+rpos);
         if(list != null)
         actions.addAll(srparser.depRules.get(lpos+"--"+rpos));
         }
        
         //actions.addAll(srparser.treebankRules.getActions(left, right, inode));
         */
        return actions;
    }

    public void train(String trainFile, String devFile, String modelFile, String embedFile, String preModel) throws Exception {
        System.err.println("Train File: " + trainFile);
        System.err.println("Dev File: " + devFile);
        System.err.println("Model File: " + modelFile);
        System.err.println("Embedding File: " + embedFile);
        System.err.println("Pre-trained Model File: " + preModel);

        List<CCGSentence> trainSents = new ArrayList<>();
        List<CCGTreeNode> trainTrees = new ArrayList<>();
        loadCCGFiles(trainCoNLLFile, trainAutoFile, trainPargFile, trainSents, trainTrees);

        List<CCGSentence> devSents = new ArrayList<>();
        List<CCGTreeNode> devTrees = new ArrayList<>();
        if (devFile != null) {
            loadTestFiles(testCoNLLFile, testAutoFile, testPargFile, devSents, devTrees);
        }
        genDictionaries(trainSents, trainTrees);

        // Initialize a classifier; prepare for training
        setupClassifierForTraining(trainSents, trainTrees, embedFile, preModel);

        System.err.println(Config.SEPARATOR);
        config.printParameters();

        long startTime = System.currentTimeMillis();
        // Track the best UAS performance we've seen.
        double bestLF = 0;

        for (int iter = 0; iter < config.maxIter; ++iter) {
            System.err.println("##### Iteration " + iter);

            Classifier.Cost cost = classifier.computeCostFunction(config.batchSize, config.regParameter, config.dropProb);
            System.err.println("Cost = " + cost.getCost() + ", Correct(%) = " + cost.getPercentCorrect());
            classifier.takeAdaGradientStep(cost, config.adaAlpha, config.adaEps);

            System.err.println("Elapsed Time: " + (System.currentTimeMillis() - startTime) / 1000.0 + " (s)");

            // evaluation
            if (devFile != null && iter % config.evalPerIter == 0) {
                // Redo precomputation with updated weights. This is only
                // necessary because we're updating weights -- for normal
                // prediction, we just do this once in #initialize
                classifier.preCompute();

                double lf = parse(devSents);
                System.err.println("LF: " + lf);

                if (config.saveIntermediate && lf > bestLF) {
                    System.err.printf("Exceeds best previous LF of %f. Saving model file..%n", bestLF);
                    bestLF = lf;
                    writeModelFile(modelFile);
                }
            }

            // Clear gradients
            if (config.clearGradientsPerIter > 0 && iter % config.clearGradientsPerIter == 0) {
                System.err.println("Clearing gradient histories..");
                classifier.clearGradientHistories();
            }
        }

        classifier.finalizeTraining();

        //*
        if (devFile != null) {
            // Do final UAS evaluation and save if final model beats the best intermediate one
            double lf = parse(devSents);
            if (lf > bestLF) {
                System.err.printf("Final model LF: %f%n", lf);
                System.err.printf("Exceeds best previous LF of %f. Saving model file..%n", bestLF);
                writeModelFile(modelFile);
            }
        } else {
            writeModelFile(modelFile);
        }
        //*/
    }

    public void test(String tconllfile, String tderivfile, String tpargfile) throws Exception {
        testCoNLLFile = tconllfile;
        testAutoFile = tderivfile;
        testPargFile = tpargfile;
        System.err.println("Testing file: " + testCoNLLFile);

        List<CCGSentence> devSents = new ArrayList<>();
        List<CCGTreeNode> devTrees = new ArrayList<>();
        loadTestFiles(testCoNLLFile, testAutoFile, testPargFile, devSents, devTrees);
        double lf = parse(devSents);
    }

    private void loadTestFiles(String conllFile, String derivFile, String pargFile, List<CCGSentence> sents, List<CCGTreeNode> trees) throws FileNotFoundException, IOException {
        
        BufferedReader conllReader = new BufferedReader(new FileReader(new File(conllFile)));
        
        if (pargFile != null) {
            BufferedReader pargReader = new BufferedReader(new FileReader(new File(pargFile)));
            gcdepsMap = getccgDepMap(pargReader);
        }

        ArrayList<String> cLines = new ArrayList<>();
        String cLine;

        while ((cLine = conllReader.readLine()) != null) {
            if (cLine.equals("")) {
                CCGSentence sent = new CCGSentence();
                sent.fillCoNLL(cLines);
                sents.add(sent);
                cLines.clear();
            }
            cLines.add(cLine);
        }
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

    public double parse(List<CCGSentence> sentences) throws IOException, Exception {

        BufferedWriter oPargWriter = new BufferedWriter(new FileWriter(new File(outPargFile)));
        BufferedWriter oAutoWriter = new BufferedWriter(new FileWriter(new File(outAutoFile)));
        oPargWriter.write("# Generated using \n# CCGParser.java\n\n");
        int id = 0;
        srparser.init();
        for (CCGSentence sent : sentences) {
            id++;
            if (id % 100 == 0) {
                System.err.print(" " + id);
            }
            if (id % 1000 == 0) {
                System.err.println();
            }

            //PStateItem output = parse(sent, id);
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
        oPargWriter.flush();
        oPargWriter.close();
        oAutoWriter.flush();
        oAutoWriter.close();
        srparser.printResults();
        return srparser.LF;
    }

    public PStateItem parse(CCGSentence sent, int sentId) throws Exception {

        input = sent.getNodes();
        List<PStateItem> agenda = new ArrayList<>();
        agenda.add(new PStateItem(0, null, null, null, null, null, null, null, 0, null, 1.0));

        PStateItem candidateOutput = null, output = null;

        while (!agenda.isEmpty()) {
            List<PStateItem> list = new ArrayList<>(beamSize);
            ArcAction parserAct = null;
            List<ArcAction> acts, nacts;

            List<TempNNAgenda> tempAgenda = new ArrayList<>();
            for (int itemId = 0; itemId < agenda.size(); itemId++) {
                PStateItem item = agenda.get(itemId);
                int[] fArray = Ints.toArray(getFeatures(item));
                acts = getAction(item);
                List<Integer> olist = new ArrayList<>(acts.size());
                nacts = new ArrayList<>();
                for (ArcAction action : acts) {
                    if (actsMap.containsKey(action)) {
                        nacts.add(action);
                    }
                    //else
                    //    System.err.println("Act not in the map: "+action);
                }

                /*
                 double[] scores = classifier.computeScores(fArray);
                
                 if(beamSize!=1)
                 //scores = softmax2(scores, nacts);
                 scores = softmax3(scores, nacts);
                
                 for(ArcAction action : nacts){
                 Integer id = actsMap.get(action);
                 double score = (beamSize!=1) ? (item.getScore() + scores[id]) : scores[id] ;
                 //double score = item.getScore() * scores[id];
                 TempNNAgenda tmp = new TempNNAgenda(item, action, fArray, score);
                 addToList(tempAgenda, tmp);
                 }*/
                ///*              
                for (ArcAction action : nacts) {
                    olist.add(actsMap.get(action));
                }
                double[] scores = classifier.computeScores(fArray, olist);

                if (beamSize != 1) {
                    scores = softmax(scores, olist);
                }

                for (int i = 0; i < nacts.size(); i++) {
                    ArcAction action = nacts.get(i);
                    double score = (beamSize != 1) ? (item.getScore() + scores[i]) : scores[i];
                    //double score = scores[i] ;
                    //double score = item.getScore() * scores[id];
                    TempNNAgenda tmp = new TempNNAgenda(item, action, fArray, score);
                    addToList(tempAgenda, tmp);
                }
                //*/
            }

            List<TempNNAgenda> newAgendaList = bestAgendaList(tempAgenda);

            for (int i = 0; i < newAgendaList.size(); i++) {
                TempNNAgenda newAgenda = newAgendaList.get(i);
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
            }
            agenda.clear();
            agenda.addAll(list);
            if (debug) //System.err.println(parserAct+" "+acts);
            {
                System.err.println("\n" + parserAct);
            }
        }

        if (candidateOutput == null) {
            return output;
        } else {
            return candidateOutput;
        }
    }

    private void addToList(List<TempNNAgenda> list, TempNNAgenda item) {
        int i = 0;
        while (i < list.size()) {
            if (item.getScore() > list.get(i).getScore()) {
                break;
            }
            i++;
        }
        list.add(i, item);
    }

    private List<TempNNAgenda> bestAgendaList(List<TempNNAgenda> list) {
        List<TempNNAgenda> nList = new ArrayList<>();
        for (int i = 0; i < beamSize && i < list.size(); i++) {
            nList.add(list.get(i));
        }
        return nList;
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

    private double[] softmax(double[] scores, List<Integer> olist) {

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

    private double[] softmax2(double[] scores, List<ArcAction> acts) {

        if (acts.isEmpty()) {
            return scores;
        }

        Integer id;
        double total = 0.0;

        for (ArcAction action : acts) {
            id = actsMap.get(action);
            total += Math.exp(scores[id]);
        }

        for (ArcAction action : acts) {
            id = actsMap.get(action);
            scores[id] = (scores[id] - Math.log(total));
        }

        return scores;
    }

    private double[] softmax3(double[] scores, List<ArcAction> acts) {

        if (acts.isEmpty()) {
            return scores;
        }

        int optLabel = -1;
        Integer id;
        for (ArcAction action : acts) {
            id = actsMap.get(action);
            if (optLabel < 0 || scores[id] > scores[optLabel]) {
                optLabel = id;
            }
        }

        double maxScore = scores[optLabel];
        double sum = 0.0;

        for (ArcAction action : acts) {
            id = actsMap.get(action);
            sum += Math.exp(scores[id] - maxScore);
        }

        for (ArcAction action : acts) {
            id = actsMap.get(action);
            //scores[id] = Math.exp(scores[id] - maxScore - Math.log(sum));
            scores[id] = (scores[id] - maxScore - Math.log(sum));
        }

        return scores;
    }

    public void writeModelFile(String modelFile) {
        try {
            double[][] W1 = classifier.getW1();
            double[] b1 = classifier.getb1();
            double[][] W2 = classifier.getW2();
            double[][] E = classifier.getE();

            Writer output = IOUtils.getPrintWriter(modelFile);

            HashMap<String, ArrayList<CCGRuleInfo>> uRules = srparser.treebankRules.getUnaryRules();
            HashMap<String, ArrayList<CCGRuleInfo>> bRules = srparser.treebankRules.getBinaryRules();

            output.write("dict=" + knownWords.size() + "\n");
            output.write("pos=" + knownPos.size() + "\n");
            output.write("ccg cats=" + knownCCGCats.size() + "\n");
            output.write("embeddingSize=" + E[0].length + "\n");
            output.write("hiddenSize=" + b1.length + "\n");
            output.write("numTokens=" + (W1[0].length / E[0].length) + "\n");
            output.write("preComputed=" + preComputed.size() + "\n");
            output.write("classes=" + actsMap.size() + "\n");
            output.write("UnaryRules=" + uRules.size() + "\n");
            output.write("BinaryRules=" + bRules.size() + "\n");
            int index = 0;

            // Classes
            for (ArcAction act : actsList) {
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

            // First write word / POS / label embeddings
            for (String word : knownWords) {
                output.write(word);
                for (int k = 0; k < E[index].length; ++k) {
                    output.write(" " + E[index][k]);
                }
                output.write("\n");
                index = index + 1;
            }
            for (String pos : knownPos) {
                output.write(pos);
                for (int k = 0; k < E[index].length; ++k) {
                    output.write(" " + E[index][k]);
                }
                output.write("\n");
                index = index + 1;
            }
            for (String label : knownCCGCats) {
                output.write(label);
                for (int k = 0; k < E[index].length; ++k) {
                    output.write(" " + E[index][k]);
                }
                output.write("\n");
                index = index + 1;
            }

            // Now write classifier weights
            for (int j = 0; j < W1[0].length; ++j) {
                for (int i = 0; i < W1.length; ++i) {
                    output.write("" + W1[i][j]);
                    if (i == W1.length - 1) {
                        output.write("\n");
                    } else {
                        output.write(" ");
                    }
                }
            }
            for (int i = 0; i < b1.length; ++i) {
                output.write("" + b1[i]);
                if (i == b1.length - 1) {
                    output.write("\n");
                } else {
                    output.write(" ");
                }
            }
            for (int j = 0; j < W2[0].length; ++j) {
                for (int i = 0; i < W2.length; ++i) {
                    output.write("" + W2[i][j]);
                    if (i == W2.length - 1) {
                        output.write("\n");
                    } else {
                        output.write(" ");
                    }
                }
            }

            // Finish with pre-computation info
            for (int i = 0; i < preComputed.size(); ++i) {
                output.write("" + preComputed.get(i));
                if ((i + 1) % 100 == 0 || i == preComputed.size() - 1) {
                    output.write("\n");
                } else {
                    output.write(" ");
                }
            }

            output.close();
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    private static final Map<String, Integer> numArgs = new HashMap<>();

    static {
        numArgs.put("textFile", 1);
        numArgs.put("outFile", 1);
    }

    public static void main(String[] args) throws IOException, Exception {

        String trainAutoFile, trainConllFile, trainPargFile, testAutoFile, testPargFile, testConllFile, outAutoFile, outPargFile, modelFile, embedFile;

        String home = "/home/ambati/ilcc/projects/parsing/experiments/english/ccg/";
        trainAutoFile = home + "data/final/train.gccg.auto";
        trainConllFile = home + "data/final/train.accg.conll";
        trainPargFile = home + "data/final/train.gccg.parg";
        trainAutoFile = home + "data/final/devel.gccg.auto";
        trainConllFile = home + "data/final/devel.accg.conll";
        trainPargFile = home + "data/final/devel.gccg.parg";
        //testAutoFile = home+"data/final/devel.gccg.auto";
        testAutoFile = "";
        testPargFile = home + "data/final/devel.gccg.parg";
        testConllFile = home + "data/final/devel.accg.conll";
        outAutoFile = home + "models/out1.txt";
        outPargFile = home + "models/out2.txt";
        modelFile = home + "models/nnccg.model.txt.gz";
        embedFile = "/home/ambati/ilcc/tools/neural-networks/embeddings/turian/embeddings.raw";

        if (args.length == 0) {
            args = new String[]{
                "-trainCoNLL", trainConllFile, "-trainAuto", trainAutoFile, "-trainParg", trainPargFile,
                "-testCoNLL", testConllFile, "-testAuto", testAutoFile, "-testParg", testPargFile,
                "-outParg", outPargFile, "-model", modelFile, "-beam", "1", "-embedFile", embedFile,
                "-maxIter", "1", //"-isTrain", true, "-beam", 1, "-debug", false, "-early", false
            };
        }

        Properties props = StringUtils.argsToProperties(args, numArgs);
        CCGNNParser ccgnnpar = new CCGNNParser(props);

        long start;

        System.err.println("Started Training: " + new Date(System.currentTimeMillis()) + "\n");
        start = (long) (System.currentTimeMillis());
        if (props.getProperty("trainCoNLL") != null) {
            ccgnnpar.train(props.getProperty("trainCoNLL"), props.getProperty("testCoNLL"), props.getProperty("model"),
                    props.getProperty("embedFile"), props.getProperty("preModel"));
        }
        System.err.println("Training Time: " + (System.currentTimeMillis() - start) / 1000.0 + " (s)");

        System.err.println("Loading Model: " + new Date(System.currentTimeMillis()) + "\n");
        ccgnnpar.loadModelFile(props.getProperty("model"));

        System.err.println("Started Parsing: " + new Date(System.currentTimeMillis()) + "\n");
        start = (long) (System.currentTimeMillis());
        ccgnnpar.test(props.getProperty("testCoNLL"), props.getProperty("testAuto"), props.getProperty("testParg"));
        System.err.println("Parsing Time: " + (System.currentTimeMillis() - start) / 1000.0 + " (s)");
    }
}
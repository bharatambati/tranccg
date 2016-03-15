/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ilcc.ccgparser.test;

import edu.stanford.nlp.util.StringUtils;
import ilcc.ccgparser.parser.CCGParser;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 *
 * @author ambati
 */
public class ParserTest {

    private static final Map<String, Integer> numArgs = new HashMap<>();

    static {
        numArgs.put("textFile", 1);
        numArgs.put("outFile", 1);
    }

    public static void main(String[] args) throws IOException, Exception {

        String trainAutoFile, trainConllFile, trainPargFile, testAutoFile, testPargFile, testConllFile, outAutoFile, outPargFile, modelFile, algo;
        boolean isTrain, greedy, isearly, lookAhead;
        int debug, iters, beamsize;

        if (args.length == 0) {
            String home = "/home/ambati/ilcc/projects/parsing/experiments/english/ccg/";
            algo = "NonInc";
            isTrain = true;
            iters = 1;
            beamsize = 1;
            greedy = true;
            isearly = true;
            debug = 0;
            lookAhead = true;
            trainAutoFile = home + "data/final/train.gccg.auto";
            trainConllFile = home + "data/final/train.accg.conll";
            trainPargFile = home + "data/final/train.gccg.jparg";
            testAutoFile = home + "data/final/devel.gccg.auto";
            testConllFile = home + "data/final/devel.accg.conll";
            testPargFile = home + "data/final/devel.gccg.parg";
//            trainAutoFile = home + "data/final/devel.gccg.auto";
//            trainConllFile = home + "data/final/devel.accg.conll";
            modelFile = home + "models/per.gsrcnc.model.txt.gz";
            outAutoFile = home + "models/out1.txt";
            outPargFile = home + "models/out2.txt";
            args = new String[]{
                //"-trainCoNLL", trainConllFile, "-trainAuto", trainAutoFile, "-trainParg", trainPargFile,
                "-testCoNLL", testConllFile, "-testAuto", testAutoFile, "-testParg", testPargFile,
                "-outParg", outPargFile, "-outAuto", outAutoFile, "-model", modelFile,
                "-beam", "" + beamsize, "-isTrain", "" + isTrain, "-debug", "" + debug, "-early", "" + isearly, "-iters", "" + iters, "-lookAhead", "" + lookAhead, "-algo", algo
            };
        }

        Properties props = StringUtils.argsToProperties(args, numArgs);

        CCGParser parser = new CCGParser(props);
        long start, end;
        double time;

        System.err.println("Started Training: " + new Date(System.currentTimeMillis()) + "\n");
        start = (long) (System.currentTimeMillis());
        if (props.getProperty("trainCoNLL") != null) {
            parser.fillData();
            parser.train();
            //parser.saveModel(modelFile);
        }
        end = (long) (System.currentTimeMillis());
        time = 0.001 * (end - start) / 60;
        System.err.println("Finished Training: " + new Date(System.currentTimeMillis()) + "\nTraining took: " + time + " minutes \n");

        System.err.println("Loading Model: " + new Date(System.currentTimeMillis()));
        parser.loadModel();

        System.err.println("Started Parsing: " + new Date(System.currentTimeMillis()));
        start = (long) (System.currentTimeMillis());
        parser.parse();
        end = (long) (System.currentTimeMillis());
        time = 0.001 * (end - start);
        System.err.println("Finished Parsing: " + new Date(System.currentTimeMillis()) + "\nParsing took: " + time + " seconds\n");
    }
}

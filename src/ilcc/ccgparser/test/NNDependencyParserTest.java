/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ilcc.ccgparser.test;

import stanford.nndep.DependencyParser;
import java.io.IOException;

/**
 *
 * @author ambati
 */
public class NNDependencyParserTest {
    
    public static void main(String[] args) throws IOException, Exception {
        
        String trainFile, develFile, embedFile, modelFile, outFile;
        
        if(args.length==8){
            trainFile = args[0];
            develFile = args[1];
            embedFile = args[2];
            modelFile = args[3];
        }
        else{
            System.err.println("java DependencyParser <train-file> <devel-file> <embedding-file> <model-file>");
            System.err.println(args.length);
            
            String dephome = "/home/ambati/ilcc/projects/parsing/experiments/replication/eacl2014-depccg/data/english/stanford/conll/";
            String home = "/home/ambati/ilcc/projects/parsing/experiments/english/ccg/";
            
            trainFile = dephome+"devel.auto.conll";
            develFile = dephome+"devel.auto.conll";
            embedFile = "/home/ambati/ilcc/tools/neural-networks/embeddings/turian/embeddings.raw";
            modelFile = home+"models/nndep.model.txt.gz";
            outFile = home+"nndep/out.conll";
            
            args = new String[] {"-trainFile", trainFile, "-devFile", develFile, "-embedFile", embedFile, "-model", modelFile, "-testFile", develFile, "-outFile", outFile, "-maxIter", "20"};
        }
        
        DependencyParser.main(args);
    }    
}
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ilcc.ccgparser.test;

import java.io.IOException;
import ilcc.easyccg.tagger.EasyCCGTagger;

/**
 *
 * @author ambati
 */
public class EasyCCGTaggerTest {
    
    public static void main(String[] args) throws IOException, Exception {
        
        String model, input, output;
        
        if(args.length < 6){
            //input = "/home/ambati/ilcc/projects/parsing/experiments/replication/naacl2015-inc-ccg/data/pre-process/devel.raw";
            //output = "/home/ambati/ilcc/projects/parsing/experiments/replication/naacl2015-inc-ccg/data/pre-process/devel.nnccg.msuper2";
            //model = "/home/ambati/ilcc/tools/easyccg-0.2/model";
            input = "/home/ambati/ilcc/projects/parsing/experiments/hindi-ccgbank/data/coarse/devel-htb-gcoarse.stagged";
            output = "/home/ambati/ilcc/projects/parsing/experiments/hindi-ccgbank/data/coarse/tmp1.txt";
            model = "/home/ambati/ilcc/projects/parsing/experiments/hindi-ccgbank/data/embed/model.hincsuper";
            args = new String[] {"-m", model, "-f", input, "-o", output, "-e"};
        }
        
        EasyCCGTagger.main(args);
        
        long start = (long) (System.currentTimeMillis());
        System.err.println("Tagging Time: " + (System.currentTimeMillis() - start) / 1000.0 + " (s)");
        //parser.parse();
    }
}
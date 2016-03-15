/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ilcc.ccgparser.algos;

import ilcc.ccgparser.parser.SRParser;
import ilcc.ccgparser.utils.ArcAction;
import ilcc.ccgparser.utils.CCGSentence;
import java.io.IOException;
import java.util.List;

/**
 *
 * @author ambati
 */
public class RevInc extends SRParser {
    
    public RevInc() throws IOException {
    }
    
    @Override
    public List<ArcAction> parse(CCGSentence sent) throws Exception {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ilcc.ccgparser.utils;

import ilcc.ccgparser.utils.DataTypes.*;
import java.io.Serializable;

/**
 *
 * @author ambati
 * 
 */
public class SCoNLLNode implements Serializable{
    
    private final int nodeId;
    private final Word word;
    private final POS pos;
    private final CCGCategory ccgcat;
    
    public SCoNLLNode(int id, String wrd, String postag, String cat){
        nodeId = id;
        word = Word.make(wrd);
        pos = POS.make(postag);
        ccgcat = CCGCategory.make(cat);
    }    
    
    public int getNodeId(){
        return nodeId;
    }
    
    public Word getWrd(){
        return word;
    }
    
    public POS getPOS(){
        return pos;
    }
    
    public CCGCategory getccgCat(){
        return ccgcat;
    }    
    
    @Override
    public String toString(){
        return new StringBuilder().append(nodeId).append("::").append(word).toString();
    }
}

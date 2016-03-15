/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ilcc.ccgparser.utils;

import ilcc.ccgparser.utils.DataTypes.DepLabel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author ambati
 */
public class DepInfo {
    
    private final DepLabel label;
    private final boolean isHeadLeft;
    private final static Map<String, DepInfo> cache = Collections.synchronizedMap(new HashMap<String, DepInfo>());
    
    public static DepInfo make(DepLabel label, boolean isheadleft) {
        DepInfo result = cache.get(label+"--"+isheadleft);
        if (result == null) {
            result = new DepInfo(label, isheadleft);
            cache.put(result.toString(), result);
        }
        return result;
    }
    
    public DepInfo(DepLabel deplabel, boolean isheadleft){
        label = deplabel;
        isHeadLeft = isheadleft;
    }
    
    public DepInfo copy(){
        return new DepInfo(label, isHeadLeft);
    }
    
    public DepLabel getDepLabel(){
        return label;
    }
    
    public boolean IsHeadLeft(){
        return isHeadLeft;
    }
    
    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append(isHeadLeft);sb.append("--");
        sb.append(label);
        return sb.toString();
    }
}

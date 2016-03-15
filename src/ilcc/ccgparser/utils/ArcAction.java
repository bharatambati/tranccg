/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/

package ilcc.ccgparser.utils;

import ilcc.ccgparser.utils.DataTypes.DepLabel;
import ilcc.ccgparser.utils.Utils.SRAction;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author ambati
 */

public class ArcAction implements Comparable<ArcAction> {
    
    private final SRAction sraction;
    private final Label label;
    private final DepLabel deplabel;
    private final String actStr;
    private final static Map<String, ArcAction> cache = Collections.synchronizedMap(new HashMap<String, ArcAction>());
    
    public static ArcAction make(SRAction act, String cat) {
        ArcAction result = cache.get(act+"--"+cat);
        if (result == null) {
            result = new ArcAction(act, cat);
            cache.put(act+"--"+cat, result);
        }
        return result;
    }
    
    public static ArcAction make(SRAction act, String cat, DepLabel dlabel) {
        String key = act+"--"+cat+"--"+dlabel;
        ArcAction result = cache.get(key);
        if (result == null) {
            result = new ArcAction(act, cat, dlabel);
            cache.put(key, result);
        }
        return result;
    }
    
    private ArcAction(SRAction act, String cat, DepLabel dlabel){
        sraction = act;
        label = Label.make(cat);
        deplabel = dlabel;
        StringBuilder sb = new StringBuilder();
        sb.append(act.toString());sb.append("--");
        sb.append(cat);
        actStr = sb.toString();
    }
    
    private ArcAction(SRAction act, String cat){
        sraction = act;
        label = Label.make(cat);
        deplabel = null;
        StringBuilder sb = new StringBuilder();
        sb.append(act.toString());sb.append("--");
        sb.append(cat);
        actStr = sb.toString();
    }
    
    public SRAction getAction(){
        return sraction;
    }
    
    public Label getLabel(){
        return label;
    }
    
    public DepLabel getDepLabel(){
        return deplabel;
    }
        
    @Override
    public int compareTo(ArcAction act) {
        return this.toString().compareTo(act.toString());
    }
    
    @Override
    public String toString(){
        return actStr;
    }  
//    @Override
//    public String toString(){
//        StringBuilder sb = new StringBuilder();
//        sb.append(sraction.toString());
//        //sb.append("--");
//        sb.append(label.toString());
//        //if(deplabel != null){
//        //    sb.append("--"); sb.append(deplabel.toString());
//        //}
//        return sb.toString();
//    }
    
    public String toString2(){
        StringBuilder sb = new StringBuilder();
        sb.append(sraction.toString());sb.append("--");
        sb.append(label.toString());
        return sb.toString();
    }
    
    @Override
    public int hashCode(){
        return toString().hashCode();
    }
    
    @Override
    public boolean equals(Object object2) {
        return object2 instanceof ArcAction && toString().equals(((ArcAction)object2).toString());
    }
}

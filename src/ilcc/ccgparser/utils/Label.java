/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ilcc.ccgparser.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author ambati
 */
public class Label {
    private final String deplabel;
    private final static Map<String, Label> cache = Collections.synchronizedMap(new HashMap<String, Label>());
    
    public static Label make(String string) {
        Label result = cache.get(string);
        if (result == null) {
            result = new Label(string);
            cache.put(string, result);
        }
        return result;
    }
    
    private Label(String str){
        deplabel = str;
    }
    
    public Label copy(){
        return Label.make(deplabel);
    }
    
    @Override
    public String toString(){
        return deplabel;
    }
}

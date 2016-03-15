/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/

package ilcc.ccgparser.learn;

import ilcc.ccgparser.utils.DataTypes.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author ambati
 */

public class Feature implements Comparable<Feature>{
    public enum FeatPrefix {
        
        //CCG Features
        s0w, s0p, s0c, s0wp, s0pc, s0wc, s1w, s1p, s1c, s1wp, s1pc, s1wc,
        s2w, s2p, s2c, s2wp, s2pc, s2wc, s3w, s3p, s3c, s3wp, s3pc, s3wc,
        q0w, q0p, q0wp, q1w, q1p, q1wp, q2w, q2p, q2wp, q3w, q3p, q3wp,
        s0Lwc, s0Lpc, s0Rwc, s0Rpc, s0Uwc, s0Upc,
        s1Lwc, s1Lpc, s1Rwc, s1Rpc, s1Uwc, s1Upc,
        
        s0wcs1wc, s0cs1w, s0ws1c, s0cs1c,
        s0wcq0wp, s0cq0wp, s0wcq0p, s0cq0p, 
        s1wcq0wp, s1cq0wp, s1wcq0p, s1cq0p, 
        
        //Set 5
        s0wcs1cs2c, s0cs1wcs2c, s0cs1cs2wc, s0cs1cs2c, s0ps1ps2p,
        s0wcs1cq0p, s0cs1wcq0p, s0cs1cq0wp, s0cs1cq0p, s0ps1pq0p,
        s0wcq0pq1p, s0cq0wpq1p, s0cq0pq1wp, s0cq0pq1p, s0pq0pq1p,
        
        //Set 6
        s0cs0hcs0lc, s0cs0lcs1c, s0cs0lcs1w, s0cs0hcs0rc, s0cs0rcq0p, 
        s0cs0rcq0w, s0cs1cs1rc, s0ws1cs1rc, s1cs1hcs1rc,
        
        //Incremental
        l1c, l2c, l3c, l4c, l5c, l1cs0c, l2cs0c, l3cs0c, l4cs0c, l5cs0c,        
        l1cs0cs1c, l2cs0cs1c, l3cs0cs1c, l4cs0cs1c, l5cs0cs1c,
        l1cs1c, l2cs1c, l3cs1c, l1p, l2p, l3p, 
        l1ps0ps1p, l2ps0ps1p, l3ps0ps1p, l4ps0ps1p, l5ps0ps1p, 
        l1wcs0cs1c, l2wcs0cs1c, l3wcs0cs1c, l4wcs0cs1c, l5wcs0cs1c, 
        
        
        //Huang and Sagae 2010 Features
        //Unigram
        sS0wt, sS0w, sS0t, sS1wt, sS1w, sS1t,
        sQ0wt, sQ0w, sQ0t,
        
        //Bigram
        sS0wS1w, sS0tS1t, sS0tQ0t, sS0wS0tS1t, sS0tS1wS1t, 
        sS0wS1wS1t, sS0wS0tS1w, sS0wS0tS1wS1t, 
        sQ0wS0w, sQ0wS1w, sQ0wS0t, sQ0wS1t, 
        
        //Trigram
        sS0tQ0tQ1t, sS1tS0tQ0t, sS0wQ0tQ1t, sS1tS0wQ0t, sS2tS1tS0t,
        
        //Modifier
        sS1tS1ltS0t, sS1tS1rtS0t, sS1tS0tS0rt, sS1tS1ltS0w,
        sS1tS1rtS0w, sS1tS0wS0lt, sS1tS0tS0lt,
        
        //Dep Labels
        sS0ld, sS0rd, sS1ld, sS1rd, 
        
        
        //Dependency Features
        
        //from single words
        dS0wp, dS0w, dS0p, dN0wp, dN0w, dN0p,  
        dN1wp, dN1w, dN1p, dN2wp, dN2w, dN2p,
        
        //from word pairs
        dS0wpN0wp, dS0wpN0w, dS0wN0wp, dS0wpN0p,
        dS0pN0wp, dS0wN0w, dS0pN0p, dN0pN1p,
        
        //from three words        
        dN0pN1pN2p, dS0pN0pN1p, dS0hpS0pN0p, 
        dS0pS0lpN0p, dS0pS0rpN0p, dS0pN0pN0lp,
        
        //distance
        dS0wd, dS0pd, dN0wd, dN0pd, dS0wN0wd, dS0pN0pd,
        
        //valency
        dS0wvr, dS0pvr, dS0wvl, dS0pvl, dN0wvl, dN0pvl,
        
        //unigrams
        dS0hw, dS0hp, dS0l, dS0lw, dS0lp, dS0ll, 
        dS0rw, dS0rp, dS0rl, dN0lw, dN0lp, dN0ll,
        
        //third-order
        dS0h2w, dS0h2p, dS0hl, dS0l2w, dS0l2p, dS0l2l,
        dS0r2w, dS0r2p, dS0r2l, dN0l2w, dN0l2p, dN0l2l,
        dS0pS0lpS0l2p, dS0pS0rpS0r2p, dS0pS0hpS0h2p, dN0pN0lpN0l2p,

        //label set
        dS0wsr, dS0psr, dS0wsl, dS0psl, dN0wsl, dN0psl,
        
        //NeuralNet
        nn, nny,
    }
    
    private final String featStr;
    private final static Map<String, Feature> cache = Collections.synchronizedMap(new HashMap<String, Feature>());
    
    public static Feature make(FeatPrefix pre, List flist) {
        StringBuilder sb = new StringBuilder(pre.toString());
        sb.append(":");
        for(int i = 0; i < flist.size(); i++){
            sb.append(flist.get(i).toString());
            sb.append("--");
        }
        String feat = sb.substring(0, sb.length()-2);
        Feature result = cache.get(feat);
        if (result == null) {
            result = new Feature(feat);
            cache.put(feat, result);
        }
        return result;
    }
    
    public static Feature make(String feat) {
        Feature result = cache.get(feat);
        if (result == null) {
            result = new Feature(feat);
            cache.put(feat, result);
        }
        return result;
    }
    
    private Feature(String str){
        featStr = str;
    }
    
    public String getFeatStr(){
        return featStr;
    }
    
    @Override
    public String toString(){
        return featStr;
    }
    
    @Override
    public int compareTo(Feature o) {
        return this.toString().compareTo(o.toString());
    }
}

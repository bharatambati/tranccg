/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/

package ilcc.ccgparser.learn;

import ilcc.ccgparser.utils.ArcAction;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author ambati
 */

public class AvePerceptron {
    
    public FeatureVector fv;
    
    public AvePerceptron() {
        fv = new FeatureVector();
    }
    
    public void updateFinalWeights(int id) {
        for(Feature feat : fv.featVector.keySet()){
            HashMap<ArcAction, Weight> map = fv.featVector.get(feat);
            HashMap<ArcAction, Weight> newmap = new HashMap<>();
            for(ArcAction outAct : map.keySet()){
                Weight wt = map.get(outAct);
                wt.totalWeight += wt.rawWeight*(id-wt.counter) + wt.rawWeight;
                wt.counter = id;
                if(wt.rawWeight != 0 || wt.totalWeight != 0)
                    newmap.put(outAct, wt);
            }
            fv.featVector.put(feat, newmap);
        }
    }
    
    public void updateWeights(HashMap<Feature, Double> featMap, ArcAction action, int update, int id) {
        
        for(Feature feat : featMap.keySet()){
            HashMap<ArcAction, Weight> map = new HashMap<>();
            if(fv.featVector.containsKey(feat))
                map = fv.featVector.get(feat);
            Weight wt;
            double nupdate = update*featMap.get(feat);
            if(map.containsKey(action)){
                wt = map.get(action);
                wt.totalWeight += wt.rawWeight*(id-wt.counter);
                wt.rawWeight += nupdate;
                wt.counter = id;
                wt.occurances += 1;
            }
            else{
                wt = new Weight(nupdate, 0, id, 1);
            }
            
            map.put(action, wt);
            fv.featVector.put(feat, map);
        }
    }
    
    public static class FeatureVector {
        
        HashMap<Feature, HashMap<ArcAction, Weight>> featVector;
        
        public FeatureVector() {
            featVector = new HashMap<>();
        }
        
        public FeatureVector(HashMap<Feature, HashMap<ArcAction, Weight>> fv) {
            featVector = fv;
        }
        
        public HashMap<Feature, HashMap<ArcAction, Weight>> getFeatureVector(){
            return featVector;
        }
        
        public double getScore(HashMap<Feature, Double> featMap, ArcAction act, boolean isTrain){
            double score = 0.0;
            HashMap<ArcAction, Weight> map;
            Weight wt;
            double count;
            
            for(Map.Entry<Feature, Double> feat : featMap.entrySet()){
                count = feat.getValue();
                if((map = featVector.get(feat.getKey())) != null){
                    if( (wt = map.get(act)) != null) {
                        if(isTrain)
                            score += count * wt.rawWeight;
                            //score += getscore(count, wt.rawWeight);
                        else
                            //score += count * (wt.totalWeight/wt.occurances);
                            //score += getscore(count, wt.totalWeight);
                            score += count * wt.totalWeight;
                            //score += count * wt.rawWeight;
                    }
                }
            }
            return score;
        }
    }
    
    public static class Weight {
        
        public double rawWeight;
        public double totalWeight;
        public double counter;
        public double occurances;
        
        public Weight() {
            rawWeight = 0.0;
            totalWeight = 0.0;
            counter = 0.0;
            occurances = 0.0;
        }
        
        public Weight(double rWeight, double tWeight, int tCounter, int tOccur) {
            rawWeight = rWeight;
            totalWeight = tWeight;
            counter = tCounter;
            occurances = tOccur;
        }
    }
    
}
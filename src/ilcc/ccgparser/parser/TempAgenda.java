/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ilcc.ccgparser.parser;

import ilcc.ccgparser.learn.Feature;
import ilcc.ccgparser.utils.ArcAction;
import java.util.HashMap;

/**
 *
 * @author ambati
 */
public class TempAgenda {
   
   private final PStateItem state;
   private final ArcAction action;
   private final HashMap<Feature, Double> featMap;
   private final double score;
   
   public TempAgenda(PStateItem item, ArcAction act, HashMap<Feature, Double> fMap, double val){
       state = item;
       action = act;
       featMap = fMap;
       score = val;
   }
   
   public double getScore(){
       return score;
   }
   
   public PStateItem getState(){
       return state;
   }
   
   public ArcAction getAction(){
       return action;
   }
   
   public HashMap<Feature, Double> getFeatureList(){
       return featMap;
   }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ilcc.ccgparser.utils;

import com.google.common.primitives.Doubles;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author ambati
 */
public class DataTypes {
    
    public static class Word {
        
        private final String word;        
        private final static Map<String, Word> cache = Collections.synchronizedMap(new HashMap<String, Word>());
        
        public static Word make(String string) {
            Word result = cache.get(string);
            if (result == null) {
                result = new Word(string);
                cache.put(string, result);
            }
            return result;
        }
        
        private Word(String str){
            word = str;
        }
        
        public String getWord(){
            return word;
        }
        
        @Override
        public String toString(){
            return word;
        }
    }
    
    public static class POS {
        
        private final String pos;        
        private final static Map<String, POS> cache = Collections.synchronizedMap(new HashMap<String, POS>());
        
        public static POS make(String string) {
            POS result = cache.get(string);
            if (result == null) {
                result = new POS(string);
                cache.put(string, result);
            }
            return result;
        }
        
        private POS(String str){
            pos = str;
        }
        
        public String getPos(){
            return pos;
        }
        
        @Override
        public String toString(){
            return pos;
        }
    }
    
    public static class DepLabel {
        
        private final String deplabel;        
        private final static Map<String, DepLabel> cache = Collections.synchronizedMap(new HashMap<String, DepLabel>());
        
        public static DepLabel make(String string) {
            DepLabel result = cache.get(string);
            if (result == null) {
                result = new DepLabel(string);
                cache.put(string, result);
            }
            return result;
        }
        
        private DepLabel(String str){
            deplabel = str;
        }
        
        public String getDepLabelStr(){
            return deplabel;
        }
        
        public DepLabel copy(){
            return DepLabel.make(deplabel);
        }
        
        @Override
        public String toString(){
            return deplabel;
        }
    }
    
    public static class CCGCategory {
        
        private final String ccgCat;        
        private final static Map<String, CCGCategory> cache = Collections.synchronizedMap(new HashMap<String, CCGCategory>());
        
        public static CCGCategory make(String string) {
            CCGCategory result = cache.get(string);
            if (result == null) {
                result = new CCGCategory(string);
                cache.put(string, result);
            }
            return result;
        }
        
        private CCGCategory(String str){
            ccgCat = str;
        }
        
        public String getCatStr(){
            return ccgCat;
        }
        
        public CCGCategory copy(){
            return CCGCategory.make(ccgCat);
        }
        
        @Override
        public String toString(){
            return ccgCat;
        }
    }
    
    public static class CDKey {
        
        private final String key;        
        private final static Map<String, CDKey> cache = Collections.synchronizedMap(new HashMap<String, CDKey>());
        
        public static CDKey make(String string) {
            CDKey result = cache.get(string);
            if (result == null) {
                result = new CDKey(string);
                cache.put(string, result);
            }
            return result;
        }
        
        private CDKey(String str){
            key = str;
        }
        
        public CCGCategory copy(){
            return CCGCategory.make(key);
        }
        
        @Override
        public String toString(){
            return key;
        }
    }
    
    public static class GoldccgInfo {
        private final List<ArcAction> arcActs;
        private final Map<String, CCGDepInfo> ccgDeps;
        private final CCGSentence ccgsent;
        
        public GoldccgInfo(List<ArcAction> acts, Map<String, CCGDepInfo> ccgdeps, CCGSentence sent){
            arcActs = acts;
            ccgDeps = ccgdeps;
            ccgsent = sent;
        }
        
        public CCGSentence getccgSent(){
            return ccgsent;
        }
        
        public Map<String, CCGDepInfo> getccgDeps(){
            return ccgDeps;
        }
        
        public List<ArcAction> getarcActs(){
            return arcActs;
        }
    }
    
    public static class DTreeNode  {
        private final int nodeid;
        private DepLabel deplabel;
        
        public DTreeNode(int id){
            nodeid = id;
            deplabel = null;
        }
        
        public DTreeNode copy(){
            return new DTreeNode(nodeid, deplabel);
        }
        
        public DTreeNode(int id, DepLabel label){
            nodeid = id;
            deplabel = label;
        }
        
        public int getId(){
            return nodeid;
        }
        
        public DepLabel getDepLabel(){
            return deplabel;
        }
        
        public void setDepLabel(DepLabel nlabel){
            deplabel = nlabel;
        }
        
        @Override
        public String toString(){
            return String.valueOf(nodeid+"--"+deplabel);
        }
    }    
    
    public static class ScoredLabel implements Comparable<ScoredLabel> {
        private final Label label;
        private final double score;
        public ScoredLabel(Label ilabel, double score)
        {
            this.label = ilabel;
            this.score = score;
        }
        
        public Label getLabel(){
            return label;
        }
        
        public double getScore(){
            return score;
        }
        
        @Override
        public int compareTo(ScoredLabel o)
        {
            return Doubles.compare(o.score, score);
        }
    }
}
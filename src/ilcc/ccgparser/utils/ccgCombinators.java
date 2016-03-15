/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ilcc.ccgparser.utils;

import edinburgh.ccg.deps.CCGcat;
import java.util.ArrayList;

/**
 *
 * @author ambati
 */
public class ccgCombinators {
    
    private static ArrayList<RuleType> ccgRules = init();

    public enum RuleType {
        fa, ba, fc, bc, bx, gfc, gbc, gbx, tfc, lp, rp, conj, conjF, lpconj, rpconj, tr, lexicon, lex, lreveal, rreveal, other, LEXICON, UNARY;
    }    
    
    public static ArrayList<RuleType> init(){
        
        ccgRules = new ArrayList<>();
        //ccgRules.add("lp");
        //ccgRules.add("rp");
        ccgRules.add(RuleType.fa);
        ccgRules.add(RuleType.ba);
        ccgRules.add(RuleType.gfc);
        ccgRules.add(RuleType.gbc);
        //ccgRules.add("tfc");
        ccgRules.add(RuleType.conj);
        ccgRules.add(RuleType.conjF);
        
        addPunctRules();
        addTFCRule();
        addCrossedRules();
        
        return ccgRules;
    }
    
    public ccgCombinators() {
        ccgRules = new ArrayList<>();
        //ccgRules.add("lp");
        //ccgRules.add("rp");
        ccgRules.add(RuleType.fa);
        ccgRules.add(RuleType.ba);
        ccgRules.add(RuleType.gfc);
        ccgRules.add(RuleType.gbc);
        //ccgRules.add("tfc");
        ccgRules.add(RuleType.conj);
        ccgRules.add(RuleType.conjF);        
        addTFCRule();
        addCrossedRules();
        addPunctRules();
    }
    
    public static void addPunctRules(){
        ccgRules.add(RuleType.lp);
        ccgRules.add(RuleType.rp);
        //ccgRules.add(RuleType.lpconj);
        //ccgRules.add(RuleType.rpconj);
    }
    
    public static void addTFCRule(){
        ccgRules.add(RuleType.tfc);
    }
    
    public static void addCrossedRules(){
        ccgRules.add(RuleType.bx);
    }
    
    public static CCGcat checkRule(CCGcat lcat, CCGcat rcat, RuleType rule){
        CCGcat result = null;
        
        try {
            if(rule.equals(RuleType.lp)) {
                result = CCGcat.punctuation(rcat, lcat);
                if(rcat.toString().endsWith("[conj]"))
                    return null;
            }
            else if(rule.equals(RuleType.rp))
                result = CCGcat.punctuation(lcat, rcat);
            else if(rule.equals(RuleType.lpconj) && Utils.isPunct(lcat))
                    result = CCGcat.punctConj(lcat, rcat);
            else if(rule.equals(RuleType.rpconj) && Utils.isPunct(rcat) )
                    result = CCGcat.punctConj(rcat, lcat);
            else if(rule.equals(RuleType.fa))
                result = CCGcat.forwardApplication(lcat, rcat);
            else if(rule.equals(RuleType.ba))
                result = CCGcat.backwardApplication(lcat, rcat);
            else if(rule.equals(RuleType.gfc))
                result = CCGcat.forwardComposition(lcat, rcat);
            else if(rule.equals(RuleType.gbc))
                result = CCGcat.backwardComposition(lcat, rcat);
            else if(rule.equals(RuleType.bx))
                result = CCGcat.backwardCrossedComposition(lcat, rcat);
            else if(rule.equals(RuleType.tfc))
                result = applyTFC(lcat, rcat);
            else if(rule.equals(RuleType.conj))
                result = CCGcat.conjItermediate(lcat, rcat);
            else if(rule.equals(RuleType.conjF))
                result = CCGcat.conjFinal(lcat, rcat);
        }
        catch (Exception ex){
            System.err.println(ex+"\t"+lcat+" "+rcat+" "+rule);
            return null;
        }
        return result;
    }
    
    private static CCGcat applyTFC(CCGcat lcat, CCGcat rcat) {
        CCGcat result;        
        result = CCGcat.typeRaiseForwardComposition(lcat, rcat, "(S\\NP)\\(S\\NP)", CCGcat.FW);
        if(result == null)
            result = CCGcat.typeRaiseForwardComposition(lcat, rcat, "S\\NP", CCGcat.FW);
        //if(result == null)
        //    result = CCGcat.typeRaiseForwardComposition(lcat, rcat, "(S\\NP)/PP", CCGcat.FW);        
        //if(result == null)
        //    result = CCGcat.typeRaiseForwardComposition(lcat, rcat, "S", CCGcat.BW);
        if(result == null)
            result = CCGcat.typeRaiseForwardComposition(lcat, rcat, "S", CCGcat.FW);
        if(result == null)
            result = CCGcat.typeRaiseForwardComposition(lcat, rcat, "NP", CCGcat.FW);
        if(result == null)
            result = CCGcat.typeRaiseForwardComposition(lcat, rcat, "N", CCGcat.FW);
        if(result == null)
            result = CCGcat.typeRaiseForwardComposition(lcat, rcat, "N/N", CCGcat.FW);
        return result;
    }
        
    public static CCGRuleInfo checkCCGRules(CCGcat lcat, CCGcat rcat){
        CCGcat rescat;
        CCGRuleInfo info = null;

        for(RuleType rule : ccgRules){
            if((rescat = checkRule(lcat, rcat, rule)) != null){                
                //info = new CCGRuleInfo(lcat, rcat, rescat, true, rule, 0);
                break;
            }
        }
        
        return info;
    }
    
    public static ArrayList checkCCGRules(CCGcat lcat, CCGcat rcat, String key){
        CCGcat rescat = null;
        ArrayList list = new ArrayList();
        RuleType combinator = null;

        for(RuleType rule : ccgRules){
            if((rescat = checkRule(lcat, rcat, rule)) != null){                
                combinator = rule;
                break;
            }
        }
        
        list.add(rescat); list.add(combinator);
        return list;
    }
    
    public static RuleType findCombinator(CCGcat lcat, CCGcat rcat, String rescatstr){
        CCGcat resCat;
        RuleType combinator = RuleType.other;
        if(lcat.matches("conj") && rcat.toString().endsWith("[conj]") && rescatstr.endsWith("[conj]")){
            int a =10;
        }
            

        for(RuleType rule : ccgRules){
            if((resCat = checkRule(lcat, rcat, rule)) != null){
                if(resCat.toString().equals(rescatstr)){
                    combinator = rule;
                    break;
                }
            }
        }
        if(combinator == RuleType.other){
            //System.err.println(lcat+" "+rcat+" "+rescatstr);
        }
        return combinator;
    }
}

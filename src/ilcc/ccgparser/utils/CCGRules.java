/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ilcc.ccgparser.utils;

import ilcc.ccgparser.utils.Utils.SRAction;
import ilcc.ccgparser.utils.DataTypes.CCGCategory;
import ilcc.ccgparser.utils.ccgCombinators.RuleType;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 *
 * @author ambati
 * 
 */
public class CCGRules {
    
    private final HashMap<String, ArrayList<CCGRuleInfo>> unaryRules;
    private final HashMap<String, ArrayList<CCGRuleInfo>> binaryRules;
    
    public CCGRules() throws IOException{
        unaryRules = new HashMap<>();
        binaryRules = new HashMap<>();
    }
    
    public CCGRules(HashMap urules, HashMap brules, HashMap rrules){
        unaryRules = urules;
        binaryRules = brules;
    }
    
    public HashMap<String, ArrayList<CCGRuleInfo>> getUnaryRules(){
        return unaryRules;
    }
    
    public HashMap<String, ArrayList<CCGRuleInfo>> getBinaryRules(){
        return binaryRules;
    }
    
    public void addRules(String unaryRuleFile, String binaryRuleFile) throws FileNotFoundException, IOException{
        addUnaryRules(unaryRuleFile);
        addBinaryRules(binaryRuleFile);
    }
    
    private void addUnaryRules(String unaryRuleFile) throws FileNotFoundException, IOException{
        BufferedReader unaryReader = new BufferedReader(new FileReader(new File(unaryRuleFile)));
        String line;
        while ((line = unaryReader.readLine()) != null) {
            if(line.startsWith("#"))
                continue;
            //#childCat\trescat\tCombinator\tCount\n
            String[] parts = line.trim().split("\t");
            CCGRuleInfo ruleInfo  = getRuleInfo(parts, true);
            String key = parts[0];
            updateUnaryRuleInfo(ruleInfo, key);
        }
    }
    
    private void addBinaryRules(String binaryRuleFile) throws FileNotFoundException, IOException{
        BufferedReader binaryReader = new BufferedReader(new FileReader(new File(binaryRuleFile)));
        String line;
        while ((line = binaryReader.readLine()) != null) {
            if(line.startsWith("#"))
                continue;
            //#lcat\trcat\trescat\theadDirection\tCombinator\tCount\n
            String[] parts = line.trim().split("\t");
            CCGRuleInfo ruleInfo  = getRuleInfo(parts, false);
            String key = parts[0]+" "+parts[1];
            updateBinaryRuleInfo(ruleInfo, key);
        }
    }
    
    private void addNSort(ArrayList list, CCGRuleInfo ruleInfo){
                    
        int i;
        for(i = 0; i < list.size(); i++){
            CCGRuleInfo info = (CCGRuleInfo) list.get(i);
            if(info.getRuleCount() < ruleInfo.getRuleCount())
                break;
        }
        list.add(i, ruleInfo);        
    }
    
    private void add(ArrayList list, CCGRuleInfo ruleInfo){
        
        boolean found = false;
        for(int i = 0; i < list.size(); i++){
            CCGRuleInfo info = (CCGRuleInfo) list.get(i);
            if(info.toString().equals(ruleInfo.toString())) {
            //if(info.getResultCat().toString().equals(ruleInfo.getResultCat().toString()) &&
            //        info.getLevel() == ruleInfo.getLevel() && info.getHeadDir() == ruleInfo.getHeadDir()){
                info.setRuleCount(info.getRuleCount()+1);
                found = true;
                break;
            }
        }
        if(!found)
            list.add(ruleInfo);
    }
    
    private CCGRuleInfo getRuleInfo(String[] rParts, boolean isunary){
        
        String lCat, rCat, resCat;
        RuleType comb;
        boolean dir;
        int count;
        CCGCategory lcat, rcat, rescat;
        
        if(isunary){
            lCat = rParts[0];            
            resCat = rParts[1];
            dir = true;
            comb = RuleType.valueOf(rParts[2]);
            count = Integer.parseInt(rParts[3]);            
            rcat = null;
        }
        else{
            lCat = rParts[0];
            rCat = rParts[1];
            resCat = rParts[2];
            dir = rParts[3].equals("left");
            comb = RuleType.valueOf(rParts[4]);
            count = Integer.parseInt(rParts[5]);
            rcat = CCGCategory.make(rCat);
        }
        lcat = CCGCategory.make(lCat);
        rescat = CCGCategory.make(resCat);
        
        CCGRuleInfo rinfo = new CCGRuleInfo(lcat, rcat, rescat, dir, count);
        return rinfo;
    }
    
    public List<CCGRuleInfo> getUnaryRuleInfo(String key){
        return unaryRules.get(key);
    }
    
    public List<CCGRuleInfo> getBinRuleInfo(String key){
        return binaryRules.get(key);
    }
    
    public void updateBinaryRuleInfo(CCGRuleInfo ruleInfo, String key){
        ArrayList<CCGRuleInfo> list = new ArrayList<>();
        if (binaryRules.containsKey(key))
            list = binaryRules.get(key);
        addNSort(list, ruleInfo);
        binaryRules.put(key, list);
    }
    
    public void updateUnaryRuleInfo(CCGRuleInfo ruleInfo, String key){
        ArrayList<CCGRuleInfo> list = new ArrayList<>();
        if (unaryRules.containsKey(key))
            list = unaryRules.get(key);
        addNSort(list, ruleInfo);
        unaryRules.put(key, list);
    }
    
    public void addBinaryRuleInfo(CCGRuleInfo ruleInfo, String key){
        addRuleInfo(ruleInfo, key, binaryRules);
    }
    
    public void addRevealRuleInfo(CCGRuleInfo ruleInfo, String key){
        //addRuleInfo(ruleInfo, key, revealRules);
    }
    
    public void addUnaryRuleInfo(CCGRuleInfo ruleInfo, String key){
        addRuleInfo(ruleInfo, key, unaryRules);
    }
    
    private void addRuleInfo(CCGRuleInfo ruleInfo, String key, HashMap<String, ArrayList<CCGRuleInfo>> ruleMap){
        ArrayList<CCGRuleInfo> list = new ArrayList<>();
        if (ruleMap.containsKey(key))
            list = ruleMap.get(key);
        add(list, ruleInfo);
        ruleMap.put(key, list);
    }
    
    public ArrayList<ArcAction> getActions(CCGTreeNode left, CCGTreeNode right, CCGTreeNode inode, boolean joint){
        ArrayList<ArcAction> actions = new ArrayList<>();        
        actions.addAll(shiftActions(inode));
        if(right != null)
            actions.addAll(unaryActions(right));
        if(left != null && right != null){
            actions.addAll(reduceActions(left, right, joint));
        }
        return actions;
    }
    
    public ArrayList<ArcAction> shiftActions(CCGTreeNode node){
        ArrayList<ArcAction> actions = new ArrayList<>();
        ArrayList<CCGCategory> cats = getInputCatList(node);
        for(CCGCategory cat : cats){
            ArcAction act = ArcAction.make(SRAction.SHIFT, cat.toString());
            actions.add(act);
        }
        return actions;
    }
    
    private ArrayList<CCGCategory> getInputCatList(CCGTreeNode node){
        ArrayList<CCGCategory> cats = new ArrayList<>();
        if(node == null)
            return cats;
        cats = node.getCCGcats();
        return cats;                
    }

    
    public ArrayList<ArcAction> reduceActions(CCGTreeNode left, CCGTreeNode right, boolean joint){
        ArrayList<ArcAction> actions = new ArrayList<>();
        String lCat = left.getCCGcat().toString();
        String rCat = right.getCCGcat().toString();
        String key = lCat+" "+rCat;
        ArrayList<CCGRuleInfo> list;
        if((list = binaryRules.get(key)) != null)
            for(CCGRuleInfo info : list){
                if(joint)
                    actions.add(ArcAction.make(info.getHeadDir() ? SRAction.RRA : SRAction.RLA, info.getResultCat().toString(), info.getDepLabel()));
                else
                    actions.add(ArcAction.make(info.getHeadDir() ? SRAction.RR : SRAction.RL, info.getResultCat().toString()));
            }
        return actions;
    }

    public ArrayList<ArcAction> unaryActions(CCGTreeNode top){
        
        ArrayList<ArcAction> actions = new ArrayList<>();
        String catStr = top.getCCGcat().toString();
        ArrayList<CCGRuleInfo> list;
        if((list = unaryRules.get(catStr)) != null)
            for(CCGRuleInfo info : list)
                actions.add(ArcAction.make(SRAction.RU, info.getResultCat().toString()));
        return actions;
    }
    
    public void printRules(){
        System.err.println("Binary Rules");
        printRules(binaryRules);
    }
    
    public void printRules(HashMap<String, ArrayList<CCGRuleInfo>> rules){
        for(String key : rules.keySet()){
            ArrayList<CCGRuleInfo> list = rules.get(key);
            for(CCGRuleInfo info : list){
                    System.err.println(key+"\t"+info.toString());
            }
        }
    }
}
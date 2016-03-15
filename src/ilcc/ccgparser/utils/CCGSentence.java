/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ilcc.ccgparser.utils;

import ilcc.ccgparser.utils.DataTypes.DepLabel;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author ambati
 */

public class CCGSentence implements Serializable {
    
    ArrayList<CCGTreeNode> nodes;
    public HashMap<String, CCGDepInfo> pargDeps;
    public HashMap<String, DepInfo> conllDeps;
    HashMap<Integer, DepTreeNode> conllDepNodes;
    public DepTreeNode depTree;
    public String ccgDeriv;
    public CCGTreeNode ccgDerivTree;
    public String sentence;
    
    public CCGSentence(){
        nodes = new ArrayList<>();
        pargDeps = new HashMap();
        conllDeps = new HashMap();
        conllDepNodes = new HashMap<>();
        ccgDeriv = "";
        sentence = "";
    }
    
    public int getLength(){
        //return nodes.size();
        return toString().split(" ").length;
    }
    
    public void setCcgDeriv(String deriv){
        ccgDeriv = deriv;
    }
    
    public void setSentence(String sent){
        sentence = sent;
    }
    
    public void setccgDerivTree(CCGTreeNode root){
        ccgDerivTree = root;
    }
    
    public void addCCGTreeNode(CCGTreeNode node){
        nodes.add(node);
        sentence+=node.getHeadWrd()+" ";
    }
    
    public CCGTreeNode getNode(int id){
        return nodes.get(id);
    }
    
    public CCGTreeNode get(int id){
        return nodes.get(id);
    }
    
    public void setpargdeps(HashMap<String, CCGDepInfo> deps){
        pargDeps = deps;
    }
    
    public HashMap<String, CCGDepInfo> getPargDeps(){
        return pargDeps;
    }
    
    public void setCoNLLDeps(HashMap<String, DepInfo> deps){
        conllDeps = deps;
    }
    
    public HashMap<String, DepInfo> getCoNLLDeps(){
        return conllDeps;
    }
    
    public void fillCoNLL(ArrayList<String> lines){
        String[] cats;
        StringBuilder sb = new StringBuilder();
        for(String line:lines){
            String[] parts = line.split("\t");
            if(parts.length >= 8){
                cats = parts[8].split("\\|\\|");
                SCoNLLNode cnode = new SCoNLLNode(Integer.parseInt(parts[0]), parts[1], parts[3], cats[0]);
                CCGTreeNode node = CCGTreeNode.makeLeaf(null, cnode);
                node.setCCGcats(cats);
                nodes.add(node);
                sb.append(parts[1]);sb.append(" ");
            }
        }
        sentence += sb.toString();
    }
    
    public void updateCCGCats(ArrayList<String> lines){
        String[] cats;
        int id = 0;
        for(String line:lines){
            String[] parts = line.split("\t");
            if(parts.length >= 8){
                //CoNLLNode node = new CoNLLNode(Integer.parseInt(parts[0]), parts[1], parts[2], parts[3], parts[4], parts[5], Integer.parseInt(parts[6]), parts[7], parts[8]);
                cats = parts[8].split("\\|\\|");
                //CCGJNode clnode = new CCGJNode(node, "");
                //CCGTreeNode clnode = new CCGTreeNode(node, cats);
                //nodes.add(clnode);
                //sentence+=parts[1]+" ";
                CCGTreeNode cnode = getNode(id);
                cnode.setCCGcats(cats);
            }
            id++;
        }
    }
    
    public void updateConllDeps(ArrayList<String> lines){
        
        DepTreeNode[] depList = new DepTreeNode[lines.size()+1];
        
        for(String line:lines){
            String[] parts = line.split("\t");
            if(parts.length >= 8){                
                int cid = Integer.parseInt(parts[0]), pid = Integer.parseInt(parts[6]);                
                DepLabel dlabel = DepLabel.make(parts[7]);
                conllDeps.put((cid<pid) ? (cid+"--"+pid) : (pid+"--"+cid), new DepInfo(dlabel, (cid>pid) ));
                conllDepNodes.put(cid, new DepTreeNode(pid, dlabel));
                if(depList[cid]==null)
                    depList[cid] = new DepTreeNode(cid);
                if(depList[pid]==null)
                    depList[pid] = new DepTreeNode(pid);
                if(pid==0)
                    depTree = depList[cid];
                else
                    depList[pid].add(depList[cid]);
            }
        }
    }
    
    public void fillDeriv(String deriv){
        ccgDeriv = deriv;
    }
    
    public ArrayList<CCGTreeNode> getNodes(){
        ArrayList<CCGTreeNode> list = new ArrayList<>();
        for(CCGTreeNode node : nodes)
            list.add(node);
        return list;
    }
    
    public HashMap<Integer, DepTreeNode> getDepNodes(){
        return conllDepNodes;
    }
    
    public DepTreeNode getDepNode(int id){
        return conllDepNodes.get(id);
    }
    
    public CCGTreeNode getDerivRoot(){
        return ccgDerivTree;
    }
    
    @Override
    public String toString(){
        return sentence;
    } 
}
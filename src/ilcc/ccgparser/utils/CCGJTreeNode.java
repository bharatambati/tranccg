/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ilcc.ccgparser.utils;

import edinburgh.ccg.deps.CCGcat;
import edinburgh.ccg.deps.DepList;
import ilcc.ccgparser.utils.DataTypes.*;
import ilcc.ccgparser.utils.ccgCombinators.RuleType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author ambati
 */
public abstract class CCGJTreeNode {
    
    private final CCGcat ccgCat;
    private final RuleType ruleType;
    private final int headDir;
    private final SCoNLLNode headNode;
    private HashMap<String, CCGDepInfo> ccgDeps;
    private CCGJTreeNode parent;
    private ArrayList<CCGCategory> ccgCats;
    
    private CCGJTreeNode(CCGcat ccgCat, RuleType rtype, int headId, SCoNLLNode headNode, HashMap depMap) {
        this.ccgCat = ccgCat;
        this.headDir = headId;
        this.headNode = headNode;
        this.ruleType = rtype;
        updateDepMap(depMap);
        this.ccgDeps = depMap;
        this.parent = null;
    }
    
    public abstract CCGJTreeNode copy();    
    
    public void setCCGcats(String[] cats){
        ccgCats = new ArrayList<>(cats.length);
        for(String catStr : cats)
            ccgCats.add(CCGCategory.make(catStr));
    }
    
    public ArrayList<CCGCategory> getCCGcats(){
        return ccgCats;
    }
    
    ///*
    private void updateDepMap(HashMap<String, CCGDepInfo> depMap){
        if(ccgDeps == null)
            ccgDeps = new HashMap<>();
        for(String key : depMap.keySet()){
            if(!ccgDeps.containsKey(key))
                ccgDeps.put(key, depMap.get(key));
        }
    }
    //*/
    
    public void setParent(CCGJTreeNode node){
        this.parent = node;
    }
    
    public CCGJTreeNode getParent(){
        return this.parent;
    }
    
    public int getHeadDir(){
        return this.headDir;
    }
    
    public HashMap getDeps(){
        return ccgDeps;
    }
    
    public int getLSpan(){
        CCGJTreeNode left = this;
        while(left.getLeftChild() != null)
            left = left.getLeftChild();
        
        return left.getConllNode().getNodeId();
    }
    
    public int getRSpan(){        
        CCGJTreeNode right = this;
        int childcount = right.getChildCount();
        while(childcount != 0){
            if(childcount == 2)
                right = right.getRightChild();
            else
                right = right.getLeftChild();
            childcount = right.getChildCount();
                
        }
        return right.getConllNode().getNodeId();
    }
    
    public SCoNLLNode getConllNode(){
        return headNode;
    }
    
    public Word getHeadWrd(){
        return headNode.getWrd();
    }
    
    public String getWrdStr(){
        return headNode.getWrd().toString();
    }
    
    public int getNodeId(){
        return headNode.getNodeId();
    }
    
    public POS getPOS(){
        return headNode.getPOS();
    }
    
    public CCGcat getCCGcat(){
        return ccgCat;
    }
    
    public CCGCategory getHeadcat(){
        return headNode.getccgCat();
    }
    
    public RuleType getRuleType(){
        return ruleType;
    }
    
    public String toString2(){
        StringBuilder sb = new StringBuilder();
        sb.append(ccgCat.toString());sb.append("::");
        sb.append(headNode.getNodeId());sb.append("::");
        sb.append(headNode.getWrd());
        return sb.toString();
    }
    
    @Override
    public String toString(){        
        StringBuilder sb = new StringBuilder();        
        sb.append(headNode.getNodeId());sb.append("::");
        sb.append(headNode.getWrd());sb.append("::");
        sb.append(ccgCat.toString());
        return sb.toString();
    }
    
    public boolean isLeaf(){
        return (this instanceof CCGJTreeNodeLeaf);
    }
    
    public boolean isUnary(){
        return (this instanceof CCGJTreeNodeUnary);
    }
    
    public boolean isBinary(){
        return (this instanceof CCGJTreeNodeBinary);
    }
        
    public int getLeftChildSpan(){
        int span = 0;
        if(this instanceof CCGJTreeNodeBinary){
            CCGJTreeNodeBinary node = (CCGJTreeNodeBinary) this;
            span = node.getRSpan();
        }
        else if(this instanceof CCGJTreeNodeUnary){
            CCGJTreeNodeUnary node = (CCGJTreeNodeUnary) this;
            span = node.getRSpan();
        }
        return span;
    }
        
    public HashMap getParseDeps(){
        HashMap<String, String> tdeps = new HashMap<>();
        List<CCGJTreeNode> list = new LinkedList();
        list.add(this);
        while(!list.isEmpty()){
            CCGJTreeNode node = list.get(0);
            HashMap<String, String> ndeps = node.getDeps();
            for(String key : ndeps.keySet())
                if(!tdeps.containsKey(key))            
                    tdeps.put(key, ndeps.get(key));
            if(node instanceof CCGJTreeNodeBinary){
                CCGJTreeNodeBinary bnode = (CCGJTreeNodeBinary) node;
                list.add(bnode.leftChild);
                list.add(bnode.rightChild);
            }
            else if(node instanceof CCGJTreeNodeUnary){
                CCGJTreeNodeUnary unode = (CCGJTreeNodeUnary) node;
                list.add(unode.child);
            }
            list.remove(0);
        }
        return tdeps;
    }
    
    static class CCGJTreeNodeBinary extends CCGJTreeNode {
        
        final boolean headIsLeft;
        final CCGJTreeNode leftChild;
        final CCGJTreeNode rightChild;
        
        private CCGJTreeNodeBinary(CCGcat ccgCat, int headId, SCoNLLNode headNode, 
                RuleType ruleType, boolean headIsLeft, CCGJTreeNode leftChild, CCGJTreeNode rightChild, HashMap depMap) {
            super(ccgCat, ruleType, headId, headNode, depMap);
            this.headIsLeft = headIsLeft;
            this.leftChild = leftChild;
            this.rightChild = rightChild;
        }        
        
        @Override
        public int getChildCount() {
            return 2;
        }
        
        @Override
        public CCGJTreeNode getLeftChild(){
            return this.leftChild;
        }
        
        @Override
        public CCGJTreeNode getRightChild(){
            return this.rightChild;
        }
        
        @Override
        public CCGJTreeNode copy(){
            return new CCGJTreeNodeBinary(this.getCCGcat().copy(), this.getHeadDir(), this.getConllNode(), this.getRuleType(), this.headIsLeft, this.getLeftChild(), this.getRightChild(), this.getDeps());
        }
    }
    
    static class CCGJTreeNodeLeaf extends CCGJTreeNode {
        private CCGJTreeNodeLeaf(CCGcat ccgCat, int headId, SCoNLLNode headNode, HashMap depMap) {
            super(ccgCat, RuleType.lexicon, headId, headNode, depMap);
        }
                
        @Override
        public int getChildCount() {
            return 0;
        }       
        
        @Override
        public CCGJTreeNode getLeftChild(){
            return null;
        }
        
        @Override
        public CCGJTreeNode getRightChild(){
            return null;
        }    
        
        @Override
        public CCGJTreeNode copy(){
            return new CCGJTreeNodeLeaf(this.getCCGcat().copy(), this.getHeadDir(), this.getConllNode(), this.getDeps());
        }
    }
    
    static class CCGJTreeNodeUnary extends CCGJTreeNode {
        
        final CCGJTreeNode child;
        
        private CCGJTreeNodeUnary(CCGcat ccgCat, RuleType rtype, int headId, SCoNLLNode headNode, CCGJTreeNode child, HashMap depMap) {
            super(ccgCat, rtype, headId, headNode, depMap);            
            this.child = child;
        }
                
        @Override
        public int getChildCount() {
            return 1;
        }
        
        @Override
        public CCGJTreeNode getLeftChild() {
            return this.child;
        }        
        
        @Override
        public CCGJTreeNode getRightChild() {
            return null;
        }

        @Override
        public CCGJTreeNode copy() {
            return new CCGJTreeNodeUnary(this.getCCGcat().copy(), this.getRuleType(), this.getHeadDir(), this.getConllNode(), this.getLeftChild(), this.getDeps());
        }
    }
    
    public static CCGJTreeNode makeBinary(CCGcat ccgCat, RuleType ruleType, boolean headIsLeft, CCGJTreeNode left, CCGJTreeNode right){
        
        int headid;
        SCoNLLNode headcNode;
        if(headIsLeft)
            headcNode = left.getConllNode();
        else
            headcNode = right.getConllNode();
        
        headid = (headIsLeft) ? 0 : 1;
        HashMap<String, String> depMap = updateBinaryDeps(left, right, ccgCat);        
        CCGJTreeNode result = new CCGJTreeNodeBinary(ccgCat, headid, headcNode, 
                ruleType, headIsLeft, left, right, depMap);
        
        return result;
    }
    
    public static CCGJTreeNode makeUnary(CCGcat ccgCat, RuleType ruleType, CCGJTreeNode child) {
        int headid;
        SCoNLLNode headcNode = child.getConllNode();
        headid = 0;
        HashMap<String, String> depMap = updateUnaryDeps(child, ccgCat);
        return new CCGJTreeNodeUnary(ccgCat, ruleType, headid, headcNode, child, depMap);
    }
    
    private static HashMap updateBinaryDeps(CCGJTreeNode left, CCGJTreeNode right, CCGcat rescat){
        HashMap<String, CCGDepInfo> depMap = new HashMap<>();
        getDeps(rescat.filledDependencies, depMap);
        getDeps(left.getCCGcat().filledDependencies, depMap);
        getDeps(right.getCCGcat().filledDependencies, depMap);        
        getDeps(left.getDeps(), depMap);
        getDeps(right.getDeps(), depMap);
        
        return depMap;
    }
    
    private static HashMap updateUnaryDeps(CCGJTreeNode child, CCGcat rescat){
        HashMap<String, CCGDepInfo> depMap = new HashMap<>();
        getDeps(rescat.filledDependencies, depMap);
        getDeps(child.getCCGcat().filledDependencies, depMap);        
        getDeps(child.getDeps(), depMap);
        
        return depMap;
    }
    
    private static void getDeps(DepList dep, HashMap depMap){
        while(dep!=null){
            String key = dep.headIndex+"--"+dep.argIndex;
            CCGDepInfo dinfo = new CCGDepInfo(dep.headIndex, dep.argIndex, dep.argPos, dep.headCat, dep.extracted);
            if(!depMap.containsKey(key))
                depMap.put(key, dinfo);
            dep = dep.next();
        }
    }

    private static void getDeps(HashMap<String, String> olddepMap, HashMap depMap){
        for(String key : olddepMap.keySet()) {
                depMap.put(key, olddepMap.get(key));
        }
    }
    
    public static CCGJTreeNode makeLeaf(CCGcat ccgCat, SCoNLLNode headcNode) {
        int headid;
        headid = -1;
        return new CCGJTreeNodeLeaf(ccgCat, headid, headcNode, new HashMap<>());
    }
    
    public abstract int getChildCount();
    public abstract CCGJTreeNode getLeftChild();
    public abstract CCGJTreeNode getRightChild();
}
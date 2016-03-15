/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ilcc.ccgparser.utils;

import ilcc.ccgparser.utils.DataTypes.*;
import java.util.ArrayList;

/**
 *
 * @author ambati
 */
public abstract class CCGTreeNode {
    
    private final CCGCategory ccgCat;
    private int headDir;
    private final SCoNLLNode headNode;
    private CCGTreeNode parent;
    private ArrayList<CCGCategory> ccgCats;
    
    private CCGTreeNode(CCGCategory ccgCat, int headId, SCoNLLNode headNode) {
        this.ccgCat = ccgCat;
        this.headDir = headId;
        this.headNode = headNode;
        this.parent = null;
    }
    
    public abstract CCGTreeNode copy();    
    
    public void setCCGcats(String[] cats){
        ccgCats = new ArrayList<>(cats.length);
        for(String catStr : cats)
            ccgCats.add(CCGCategory.make(catStr));
    }
    
    public ArrayList<CCGCategory> getCCGcats(){
        return ccgCats;
    }
    
    public void setParent(CCGTreeNode node){
        this.parent = node;
    }
    
    public void setHeadDir(int dir){
        this.headDir = dir;
    }
    
    public CCGTreeNode getParent(){
        return this.parent;
    }
    
    public int getHeadDir(){
        return this.headDir;
    }
    
    public int getLSpan(){
        CCGTreeNode left = this;
        while(left.getLeftChild() != null)
            left = left.getLeftChild();
        
        return left.getConllNode().getNodeId();
    }
    
    public int getRSpan(){        
        CCGTreeNode right = this;
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
    
    public CCGCategory getCCGcat(){
        return ccgCat;
    }
    
    public CCGCategory getHeadcat(){
        return headNode.getccgCat();
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
        return (this instanceof CCGTreeNodeLeaf);
    }
    
    public boolean isUnary(){
        return (this instanceof CCGTreeNodeUnary);
    }
    
    public boolean isBinary(){
        return (this instanceof CCGTreeNodeBinary);
    }
        
    public int getLeftChildSpan(){
        int span = 0;
        if(this instanceof CCGTreeNodeBinary){
            CCGTreeNodeBinary node = (CCGTreeNodeBinary) this;
            span = node.getRSpan();
        }
        else if(this instanceof CCGTreeNodeUnary){
            CCGTreeNodeUnary node = (CCGTreeNodeUnary) this;
            span = node.getRSpan();
        }
        return span;
    }
    
    static class CCGTreeNodeBinary extends CCGTreeNode {
        
        final boolean headIsLeft;
        final CCGTreeNode leftChild;
        final CCGTreeNode rightChild;
        
        private CCGTreeNodeBinary(CCGCategory ccgCat, int headId, SCoNLLNode headNode, 
                boolean headIsLeft, CCGTreeNode leftChild, CCGTreeNode rightChild) {
            super(ccgCat, headId, headNode);
            this.headIsLeft = headIsLeft;
            this.leftChild = leftChild;
            this.rightChild = rightChild;
        }        
        
        @Override
        public int getChildCount() {
            return 2;
        }
        
        @Override
        public CCGTreeNode getLeftChild(){
            return this.leftChild;
        }
        
        @Override
        public CCGTreeNode getRightChild(){
            return this.rightChild;
        }
        
        @Override
        public CCGTreeNode copy(){
            return new CCGTreeNodeBinary(this.getCCGcat().copy(), this.getHeadDir(), this.getConllNode(), this.headIsLeft, this.getLeftChild(), this.getRightChild());
        }
    }
    
    static class CCGTreeNodeLeaf extends CCGTreeNode {
        private CCGTreeNodeLeaf(CCGCategory ccgCat, int headId, SCoNLLNode headNode) {
            super(ccgCat, headId, headNode);
        }
                
        @Override
        public int getChildCount() {
            return 0;
        }       
        
        @Override
        public CCGTreeNode getLeftChild(){
            return null;
        }
        
        @Override
        public CCGTreeNode getRightChild(){
            return null;
        }    
        
        @Override
        public CCGTreeNode copy(){
            return new CCGTreeNodeLeaf(this.getCCGcat().copy(), this.getHeadDir(), this.getConllNode());
        }
    }
    
    static class CCGTreeNodeUnary extends CCGTreeNode {
        
        final CCGTreeNode child;
        
        private CCGTreeNodeUnary(CCGCategory ccgCat, int headId, SCoNLLNode headNode, CCGTreeNode child) {
            super(ccgCat, headId, headNode);            
            this.child = child;
        }
                
        @Override
        public int getChildCount() {
            return 1;
        }
        
        @Override
        public CCGTreeNode getLeftChild() {
            return this.child;
        }        
        
        @Override
        public CCGTreeNode getRightChild() {
            return null;
        }

        @Override
        public CCGTreeNode copy() {
            return new CCGTreeNodeUnary(this.getCCGcat().copy(), this.getHeadDir(), this.getConllNode(), this.getLeftChild());
        }
    }
    
    public static CCGTreeNode makeBinary(CCGCategory ccgCat, boolean headIsLeft, CCGTreeNode left, CCGTreeNode right){
        
        int headid;
        SCoNLLNode headcNode;
        if(headIsLeft)
            headcNode = left.getConllNode();
        else
            headcNode = right.getConllNode();
        
        headid = (headIsLeft) ? 0 : 1;
        CCGTreeNode result = new CCGTreeNodeBinary(ccgCat, headid, headcNode, 
                headIsLeft, left, right);
        
        return result;
    }
    
    public static CCGTreeNode makeUnary(CCGCategory ccgCat, CCGTreeNode child) {
        int headid;
        SCoNLLNode headcNode = child.getConllNode();
        headid = 0;
        return new CCGTreeNodeUnary(ccgCat, headid, headcNode, child);
    }
    
    public static CCGTreeNode makeLeaf(CCGCategory ccgCat, SCoNLLNode headcNode) {
        int headid;
        headid = -1;
        return new CCGTreeNodeLeaf(ccgCat, headid, headcNode);
    }
    
    public abstract int getChildCount();
    public abstract CCGTreeNode getLeftChild();
    public abstract CCGTreeNode getRightChild();
}
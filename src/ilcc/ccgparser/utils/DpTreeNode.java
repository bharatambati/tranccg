/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ilcc.ccgparser.utils;

import ilcc.ccgparser.utils.DataTypes.*;

/**
 *
 * @author ambati
 */
public abstract class DpTreeNode {
    
    private DepLabel depLabel;
    private final int headDir;
    private final SCoNLLNode headNode;
    private DpTreeNode parent;
    
    private DpTreeNode(DepLabel label, int headId, SCoNLLNode headNode) {
        this.depLabel = label;
        this.headDir = headId;
        this.headNode = headNode;
        this.parent = null;
    }
    
    public abstract DpTreeNode copy();    
        
    public void setParent(DpTreeNode node){
        this.parent = node;
    }
    
    public DpTreeNode getParent(){
        return this.parent;
    }
    
    public int getHeadDir(){
        return this.headDir;
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

    public DepLabel getDepLabel(){
        return depLabel;
    }
    
    public void setDepLabel(DepLabel nlabel){
        depLabel = nlabel;
    }
    
    @Override
    public String toString(){        
        StringBuilder sb = new StringBuilder();        
        sb.append(headNode.getNodeId());sb.append("::");
        sb.append(headNode.getWrd());sb.append("::");
        sb.append(depLabel.toString());
        return sb.toString();
    }

    public boolean isLeaf(){
        return (this instanceof DpTreeNodeLeaf);
    }
    
    public boolean isBinary(){
        return (this instanceof DpTreeNodeBinary);
    }
    
    static class DpTreeNodeBinary extends DpTreeNode {
        
        final boolean headIsLeft;
        final DpTreeNode leftChild;
        final DpTreeNode rightChild;
        
        private DpTreeNodeBinary(DepLabel deplabel, int headId, SCoNLLNode headNode, 
                boolean headIsLeft, DpTreeNode leftChild, DpTreeNode rightChild) {
            super(deplabel, headId, headNode);
            this.headIsLeft = headIsLeft;
            this.leftChild = leftChild;
            this.rightChild = rightChild;
        }        
        
        @Override
        public int getChildCount() {
            return 2;
        }
        
        @Override
        public DpTreeNode getLeftChild(){
            return this.leftChild;
        }
        
        @Override
        public DpTreeNode getRightChild(){
            return this.rightChild;
        }
        
        @Override
        public DpTreeNode copy(){
            return new DpTreeNodeBinary(this.getDepLabel().copy(), this.getHeadDir(), this.getConllNode(), this.headIsLeft, this.getLeftChild(), this.getRightChild());
        }
    }
    
    static class DpTreeNodeLeaf extends DpTreeNode {
        private DpTreeNodeLeaf(DepLabel deplabel, int headId, SCoNLLNode headNode) {
            super(deplabel, headId, headNode);
        }
                
        @Override
        public int getChildCount() {
            return 0;
        }       
        
        @Override
        public DpTreeNode getLeftChild(){
            return null;
        }
        
        @Override
        public DpTreeNode getRightChild(){
            return null;
        }    
        
        @Override
        public DpTreeNode copy(){
            return new DpTreeNodeLeaf(this.getDepLabel().copy(), this.getHeadDir(), this.getConllNode());
        }
    }    
    
    public static DpTreeNode makeBinary(DepLabel deplabel, boolean headIsLeft, DpTreeNode left, DpTreeNode right){
        
        int headid;
        SCoNLLNode headcNode;
        if(headIsLeft)
            headcNode = left.getConllNode();
        else
            headcNode = right.getConllNode();
        
        headid = (headIsLeft) ? 0 : 1;       
        DpTreeNode result = new DpTreeNodeBinary(deplabel, headid, headcNode, 
                headIsLeft, left, right);
        
        return result;
    }
    
    public static DpTreeNode makeLeaf(DepLabel deplabel, SCoNLLNode headcNode) {
        int headid;
        headid = -1;
        return new DpTreeNodeLeaf(deplabel, headid, headcNode);
    }
    
    public abstract int getChildCount();
    public abstract DpTreeNode getLeftChild();
    public abstract DpTreeNode getRightChild();
}
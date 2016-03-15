/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ilcc.ccgparser.parser;

import edinburgh.ccg.deps.CCGcat;
import ilcc.ccgparser.utils.Utils.SRAction;
import ilcc.ccgparser.utils.*;
import ilcc.ccgparser.utils.DataTypes.CCGCategory;
import ilcc.ccgparser.utils.DataTypes.DTreeNode;
import ilcc.ccgparser.utils.DataTypes.DepLabel;
import ilcc.ccgparser.utils.ccgCombinators.RuleType;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

/**
 *
 * @author ambati
 */
public class PStateItem {
    
    private final CCGTreeNode node;
    private final PStateItem statePtr;
    private final PStateItem stackPtr, dstackPtr;
    private final DTreeNode s0, s0l, s0r;
    private final int currentWord;
    private final ArcAction action;
    private final double score;
    private final int id;
    
    public PStateItem(){        
        id = 0;
        node = null;
        statePtr = null;
        stackPtr = null;        
        s0 = s0l = s0r = null;        
        dstackPtr = null;        
        currentWord = 0;
        action = null;
        score = 0.0;
    }
    
    public PStateItem(int nid, CCGTreeNode nNode, DTreeNode ns0, DTreeNode ns0l, DTreeNode ns0r, PStateItem ndStackPtr, PStateItem nStatePtr, PStateItem nStackPtr, int nCurrentWrd, ArcAction nAction, double nScore){
        
        id = nid;
        node = (nNode== null) ? nNode : nNode.copy();
        s0 = ns0;
        s0l = ns0l;
        s0r = ns0r;
        dstackPtr = ndStackPtr;
        statePtr = nStatePtr;
        stackPtr = nStackPtr;
        currentWord = nCurrentWrd;
        action = nAction;
        score = nScore;
    }
    
    public int getCurrentWrd(){
        return currentWord;
    }
   
    public PStateItem getStatePtr(){
        return statePtr;
    }    
   
    public PStateItem getStackPtr(){
        return stackPtr;
    }
        
    public PStateItem getDStackPtr(){
        return dstackPtr;
    }
       
    public CCGTreeNode getNode(){
        return node;
    }
    
    public ArcAction getArcAction(){
        return action;
   }
    
    public double getScore(){
        return score;
    }
    
    public int getId(){
        return id;
    }
    
    public DTreeNode getDepNode(){
        return s0;
    }
    
    public DTreeNode getLDepNode(){
        return s0l;
    }
    
    public DTreeNode getRDepNode(){
        return s0r;
    }
    
    public int stacksize(){
        int size = 0;
        PStateItem current = this;
        while (current.node != null) {
            size++;// no node -> start/fini
            current = current.stackPtr;
        }
        return size;
   }
    
    public int depstacksize(){
        int size = 0;
        PStateItem current = this;
        if(s0 != null){
            size++;
            if(current.dstackPtr != null && current.dstackPtr.s0 != null)
                size++;
        }
        return size;
   }
    
    public boolean isFinish(int size){        
        return currentWord == size && stacksize()==1;
    }
    
    public PStateItem copy() {
        return new PStateItem(id, node==null ? node : node.copy(), s0==null ? s0 : s0.copy(), s0l==null ? s0l : s0l.copy(), s0r==null ? s0r : s0r.copy(), 
                dstackPtr, statePtr, stackPtr, currentWord, action, score);
    }
    
    public PStateItem applyAction(ArcAction act, List<CCGTreeNode> input, boolean incalgo, double val){
        if(act.getAction() == SRAction.SHIFT)
            return applyShift(act, input, val);
        else if(act.getAction() == SRAction.RU || act.getAction() == SRAction.RL || act.getAction() == SRAction.RR
                || act.getAction() == SRAction.RLA || act.getAction() == SRAction.RRA)
            return applyReduce(act, val);
        else if(act.getAction() == SRAction.LA || act.getAction() == SRAction.RA)
            return applyDepArc(act, val);
        return null;
    }
    
    public PStateItem applyShift(ArcAction act, List<CCGTreeNode> input, double val){        
        CCGTreeNode inode = input.get(currentWord);
        String rescatstr = act.getLabel().toString();
        SCoNLLNode pcnode = inode.getConllNode();
        SCoNLLNode cnode = new SCoNLLNode(pcnode.getNodeId(), pcnode.getWrd().toString(), pcnode.getPOS().toString(), rescatstr);
        CCGCategory rescat = CCGCategory.make(rescatstr);
        CCGTreeNode result = CCGTreeNode.makeLeaf(rescat, cnode);
        DTreeNode depnode = new DTreeNode(cnode.getNodeId(), null);
        PStateItem retval = new PStateItem(id+1, result, depnode, null, null, this, this, this, currentWord+1, act, val);
        return retval;
    }
    
    public PStateItem applyReduce(ArcAction act, double val) {
        
        CCGTreeNode left, right, result;
        PStateItem retval = null;
        SRAction sract = act.getAction();
        CCGCategory rescat = CCGCategory.make(act.getLabel().toString());
        
        boolean single_child = false, isHeadLeft = false;
        if(sract == SRAction.RU)
            single_child = true;
        else if(sract == SRAction.RR || sract == SRAction.RRA)
            isHeadLeft = true;
        
        if(single_child){
            right = node;
            result = CCGTreeNode.makeUnary(rescat, right);
            right.setParent(result);
            retval = new PStateItem(id+1, result, s0, s0l, s0r, dstackPtr, this, stackPtr, currentWord, act, val);
        }
        else if(sract == SRAction.RR || sract == SRAction.RL){
            right = node;
            left = stackPtr.node;
            result = CCGTreeNode.makeBinary(rescat, isHeadLeft, left, right);
            left.setParent(result); right.setParent(result);
            retval = new PStateItem(id+1, result, s0, s0l, s0r, dstackPtr, this, stackPtr.stackPtr, currentWord, act, val);
        }
        else if(sract == SRAction.RRA || sract == SRAction.RLA){
            right = node;
            left = stackPtr.node;
            result = CCGTreeNode.makeBinary(rescat, isHeadLeft, left, right);
            left.setParent(result); right.setParent(result);
            DepLabel deplabel = DepLabel.make(act.getDepLabel().toString());
            DTreeNode ns0, ns0l, ns0r;
            if(sract == SRAction.RLA){
                ns0 = s0; ns0l = dstackPtr.s0; ns0r = s0r;
                ns0l.setDepLabel(deplabel);
            }
            else{
                ns0 = dstackPtr.s0; ns0l = dstackPtr.s0l; ns0r = s0;
                ns0r.setDepLabel(deplabel);
            }
            retval = new PStateItem(id+1, result, ns0, ns0l, ns0r, dstackPtr.dstackPtr, this, stackPtr.stackPtr, currentWord, act, val);
        }
        return retval;
    }
        
    public PStateItem applyFrag(ArcAction act) {
        CCGTreeNode left, right, result;
        right = node;
        left = stackPtr.node;
        result = CCGTreeNode.makeBinary(CCGCategory.make("X"), false, left, right);
        return new PStateItem(id+1, result, s0, s0l, s0r, dstackPtr, this, stackPtr.stackPtr, currentWord, act, 0.0);
    }
    
    public PStateItem applyDepArc(ArcAction act, double val) {
        
        SRAction sract = act.getAction();
        DepLabel deplabel = DepLabel.make(act.getLabel().toString());
        DTreeNode ns0, ns0l, ns0r;
        
        if(sract == SRAction.LA){
            ns0 = s0;
            ns0l = dstackPtr.s0;
            ns0r = s0r;
            ns0l.setDepLabel(deplabel);
            return new PStateItem(id+1, node, ns0, ns0l, ns0r, dstackPtr.dstackPtr, this, stackPtr, currentWord, act, val);
        }
        else{            
            ns0 = dstackPtr.s0;
            ns0l = dstackPtr.s0l;
            ns0r = s0;
            ns0r.setDepLabel(deplabel);
            return new PStateItem(id+1, node, ns0, ns0l, ns0r, dstackPtr.dstackPtr, this, stackPtr, currentWord, act, val);
        }
    }
    
    public HashMap<String, CCGDepInfo> getSysCatsNDeps(HashMap<Integer, CCGCategory> sysCats){
        HashMap<String, CCGDepInfo> sysccgDeps = new HashMap<>();
        if(stacksize()>=1){
            PStateItem curState = this;
            while(curState.node != null){
                getStackDeps(curState, sysccgDeps, sysCats);
                curState = curState.stackPtr;
           }
        }
        return sysccgDeps;
    }
    
    public HashMap<Integer, DepTreeNode> getSysCONLLDeps(){
        HashMap<Integer, DepTreeNode> sysconllDeps = new HashMap<>();
        if(stacksize()>=1){
            PStateItem curState = this;
            while(curState.s0 != null){
                getCoNLLStackDeps(curState, sysconllDeps);                
                sysconllDeps.put(curState.s0.getId(), new DepTreeNode(0, DepLabel.make("root")));
                curState = curState.dstackPtr;
           }
        }
        return sysconllDeps;
    }
    
    public void getCoNLLStackDeps(PStateItem curState, HashMap<Integer, DepTreeNode> conllDeps){
        while(curState != null){
            if(curState.s0!=null){
                int cid, pid = curState.s0.getId();
                if(curState.s0l != null){
                    cid = curState.s0l.getId();
                    conllDeps.put(cid, new DepTreeNode(pid, curState.s0l.getDepLabel()) );
                    //conllDeps.put(curState.s0.getId()+"--"+curState.s0l.getId(), new DepInfo(curState.s0l.getDepLabel(), true));
                }
                if(curState.s0r != null){
                    cid = curState.s0r.getId();
                    conllDeps.put(cid, new DepTreeNode(pid, curState.s0r.getDepLabel()) );
                }
            }
            curState = curState.statePtr;
        }
    }    
    
    public void getStackDeps(PStateItem curState, HashMap<String, CCGDepInfo> ccgDeps, HashMap<Integer, CCGCategory> sysCats){
        CCGTreeNode root = curState.getNode();
        Stack<CCGJTreeNode> nodes = new Stack<>();        
        postOrder(root, nodes, sysCats);
        ccgDeps.putAll(nodes.get(0).getDeps());
    }
    
    public void postOrder(CCGTreeNode root, Stack<CCGJTreeNode> nodes, HashMap<Integer, CCGCategory> sysCats){
        if(root == null)
            return;
        
        postOrder(root.getLeftChild(), nodes, sysCats);
        postOrder(root.getRightChild(), nodes, sysCats);
        CCGJTreeNode nnode = null;
        if(root.getChildCount() == 0){
            SCoNLLNode cnode = root.getConllNode();
            CCGcat rescat = CCGcat.lexCat(cnode.getWrd().toString(), cnode.getccgCat().toString(), cnode.getNodeId());
            // NAACL2015: Change of Head rule for auxiliary
            //if(rescat.isAux()) rescat.handleAux();
            nnode = CCGJTreeNode.makeLeaf(rescat, cnode);
            sysCats.put(cnode.getNodeId(), cnode.getccgCat());
        }
        else if(root.getChildCount() == 1){
            String rescatstr = root.getCCGcat().toString();
            
            CCGJTreeNode left = nodes.pop();
            CCGcat lcat = left.getCCGcat();
            CCGcat rescat = CCGcat.typeRaiseTo(lcat, rescatstr);
            if (rescat == null)
                rescat = CCGcat.typeChangingRule(lcat, rescatstr);
            nnode = CCGJTreeNode.makeUnary(rescat, RuleType.other, left);
            left.setParent(nnode);
            
        }
        else if(root.getChildCount() == 2){          
            String rescatstr = root.getCCGcat().toString();
            
            CCGJTreeNode right = nodes.pop(), left = nodes.pop();
            CCGcat lcat = left.getCCGcat(), rcat = right.getCCGcat();
            CCGcat rescat = CCGcat.combine(lcat, rcat, rescatstr);
            nnode = CCGJTreeNode.makeBinary(rescat, RuleType.other, (root.getHeadDir()==0), left, right);
            left.setParent(nnode);
            right.setParent(nnode);
        }
        nodes.add(nnode);
    }
    
        public void postOrder(CCGTreeNode root, ArrayList list) {
        if (root == null) {
            return;
        }
        postOrder(root.getLeftChild(), list);
        postOrder(root.getRightChild(), list);
        list.add(root);
    }

    public void writeDeriv(int id, BufferedWriter odWriter) throws IOException {
        CCGTreeNode root = node;
        ArrayList<CCGTreeNode> list = new ArrayList<>();
        postOrder(root, list);
        Stack<String> sStack = new Stack<>();
        for (CCGTreeNode cNode : list) {
            if (cNode.isLeaf()) {
                StringBuilder sb = new StringBuilder();
                String wrd, pos, cat;
                wrd = cNode.getWrdStr();
                pos = cNode.getPOS().toString();
                cat = cNode.getCCGcat().toString();
                sb.append("(<L ");
                sb.append(cat);
                sb.append(" ");
                sb.append(pos);
                sb.append(" ");
                sb.append(pos);
                sb.append(" ");
                sb.append(wrd);
                sb.append(" ");
                sb.append(cat);
                sb.append(">)");
                sStack.push(sb.toString());
            } else if (cNode.getChildCount() == 1) {

                StringBuilder sb = new StringBuilder();
                String cat;
                cat = cNode.getCCGcat().toString();
                sb.append("(<T ");
                sb.append(cat);
                sb.append(" lex");
                sb.append(" 0 1> ");
                sb.append(sStack.pop());
                sb.append(" )");
                sStack.push(sb.toString());
            } else {
                String rstr = sStack.pop();
                String lstr = sStack.pop();
                StringBuilder sb = new StringBuilder();
                String cat = cNode.getCCGcat().toString();
                String dir = (cNode.getHeadDir() == 1) ? "1" : "0";
                sb.append("(<T ");
                sb.append(cat);
                sb.append(" ");
                sb.append(dir);
                sb.append(" 2> ");
                sb.append(lstr);
                sb.append(" ");
                sb.append(rstr);
                sb.append(" )");
                sStack.push(sb.toString());
            }
        }
        odWriter.write("ID=" + id + "\n");
        odWriter.write(sStack.pop().trim() + "\n");
        odWriter.flush();
    }    
        }

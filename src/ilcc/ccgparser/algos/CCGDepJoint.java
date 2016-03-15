/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ilcc.ccgparser.algos;

import ilcc.ccgparser.parser.SRParser;
import ilcc.ccgparser.utils.ArcAction;
import ilcc.ccgparser.utils.CCGRuleInfo;
import ilcc.ccgparser.utils.CCGSentence;
import ilcc.ccgparser.utils.CCGTreeNode;
import ilcc.ccgparser.utils.DataTypes;
import ilcc.ccgparser.utils.DataTypes.DepLabel;
import ilcc.ccgparser.utils.DepInfo;
import ilcc.ccgparser.utils.DepTreeNode;
import ilcc.ccgparser.utils.Label;
import ilcc.ccgparser.utils.Utils.SRAction;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

/**
 *
 * @author ambati
 */
public class CCGDepJoint extends SRParser{
    
    public Stack<DepTreeNode> depstack;

    public CCGDepJoint() throws IOException {
        depstack = new Stack<>();
    }
    
    @Override
    public List<ArcAction> parse(CCGSentence sent) throws Exception {
        return parseGold(sent);
    }
    
    protected List<ArcAction> parseGold(CCGSentence sent) throws Exception{
        
        List<ArcAction> gccgActs, gdepActs;
        gccgActs = parseGoldCCG(sent);
        gdepActs = parseGoldDep(sent);
        
        return shiftReduceGold(sent, gccgActs, gdepActs);
    }
    
    private DepTreeNode[] getDepTreeList(CCGSentence sent){
        DepTreeNode[] depList = new DepTreeNode[sent.getLength()+1];
        DepTreeNode root = sent.depTree;
        ArrayList<DepTreeNode> list = new ArrayList<>();
        list.add(root);
        while(!list.isEmpty()){
            DepTreeNode node = list.get(0);
            for(int i=0; i<node.getChildCount(); i++)
                list.add((DepTreeNode)node.getChildAt(i));
            depList[node.getId()] = node;
            list.remove(0);
        }
        return depList;
    }            
    
    protected List<ArcAction> parseGoldDep(CCGSentence sent) throws Exception{
        List<ArcAction> gdepActs = new ArrayList<>();
        input = sent.getNodes();
        DepTreeNode[] depList = getDepTreeList(sent);
        HashMap<String, DepInfo> cdeps = sent.conllDeps;
        depstack = new Stack<>();
        int nodeCount = input.size();
        for(int i=1; i<=nodeCount; i++){
            DepTreeNode node = new DepTreeNode(input.get(i-1).getNodeId());
            gdepActs.add(ArcAction.make(SRAction.SHIFT, null));
            //System.out.println("SHIFT "+i);
            //input.remove(0);
            depstack.push(node);
            while(depstack.size()>=2){
                DepTreeNode left = depstack.get(depstack.size()-2), right = depstack.peek();
                int lid = left.getId(), rid = right.getId();
                String key = lid+"--"+rid;
                DepInfo depinfo;
                String poskey = input.get(lid-1).getPOS().toString()+"--"+input.get(rid-1).getPOS().toString();
                if((depinfo = cdeps.get(key)) != null){
                    if(!depinfo.IsHeadLeft()){
                        right = depstack.pop(); left = depstack.pop();
                        left.setDepLabel(depinfo.getDepLabel());
                        right.add(left);
                        depstack.push(right);
                        ArcAction act = ArcAction.make(SRAction.LA, depinfo.getDepLabel().toString());
                        gdepActs.add(act);
                        updateDepRule(poskey, act);                      
                        //System.out.println("LA "+depinfo.getDepLabel()+" :: "+lid+"--"+rid);
                        depList[rid].remove(0);
                    }
                    else if( (depList[rid].getChildCount() == 0) || 
                             (depList[rid].getChildCount() > 0 && ((DepTreeNode) depList[rid].getLastChild()).getId() < right.getId()) ){
                        right = depstack.pop(); left = depstack.pop();
                        right.setDepLabel(depinfo.getDepLabel());
                        left.add(right);
                        depstack.push(left);
                        ArcAction act = ArcAction.make(SRAction.RA, depinfo.getDepLabel().toString());
                        gdepActs.add(act);
                        updateDepRule(poskey, act);
                        //System.out.println("RA "+depinfo.getDepLabel()+" :: "+lid+"--"+rid);
                        depList[lid].remove(depList[lid].getChildCount()-1);
                    }
                    else
                        break;
                }
                else
                    break;
            }
        }
        if(depstack.size()>1)
            System.err.println("\n"+sentCount+" :: "+sent.toString()+" More than one node in the stack");
        return gdepActs;
    }
    
    private void updateDepRule(String poskey, ArcAction act){
        List<ArcAction> actlist;
        if((actlist = depRules.get(poskey)) == null)
            actlist = new ArrayList<>();
        if(!actlist.contains(act))
            actlist.add(act);
        depRules.put(poskey, actlist);
    }
    
    protected List<ArcAction> parseGoldCCG(CCGSentence sent) throws Exception{
        CCGTreeNode root = sent.getDerivRoot();
        List<ArcAction> gccgActs = new ArrayList<>();        
        List<CCGTreeNode> list = new ArrayList<>();
        postOrder(root, list);
        for(CCGTreeNode node : list) {
            if(node.isLeaf()){
                gccgActs.add(ArcAction.make(SRAction.SHIFT, node.getCCGcat().toString()));
            }
            else if(node.getChildCount()==1){
                DataTypes.CCGCategory lcat = node.getLeftChild().getCCGcat();
                DataTypes.CCGCategory rescat = DataTypes.CCGCategory.make(node.getCCGcat().toString());
                CCGRuleInfo info = new CCGRuleInfo(lcat, null, rescat, true, -1);
                treebankRules.addUnaryRuleInfo(info, lcat.toString());
                gccgActs.add(ArcAction.make(SRAction.RU, node.getCCGcat().toString()));
                
            }
            else {
                CCGTreeNode left = node.getLeftChild(), right = node.getRightChild();
                DataTypes.CCGCategory lcat = left.getCCGcat(), rcat = right.getCCGcat();
                DataTypes.CCGCategory rescat = DataTypes.CCGCategory.make(node.getCCGcat().toString());
                CCGRuleInfo info = new CCGRuleInfo(lcat, rcat, rescat, (node.getHeadDir()==0), 0);
                treebankRules.addBinaryRuleInfo(info, lcat.toString()+" "+rcat.toString());
                
                if(node.getHeadDir()==0)
                    gccgActs.add(ArcAction.make(SRAction.RR, node.getCCGcat().toString()));
                else
                    gccgActs.add(ArcAction.make(SRAction.RL, node.getCCGcat().toString()));
            }
        }
        return gccgActs;
    }
    
    public void postOrder(CCGTreeNode root, List list){
        if(root == null)
            return;
        postOrder(root.getLeftChild(), list);
        postOrder(root.getRightChild(), list);
        list.add(root);
    }
    
    public List<ArcAction> shiftReduceGold(CCGSentence sent, List<ArcAction> gccgActs, List<ArcAction> gdepActs) {
        input = sent.getNodes();
        stack = new Stack<>();
        depstack = new Stack<>();
        int i=0, j=0;
        List<ArcAction> goldActs = new ArrayList<>();
        ArcAction depAct, ccgAct = null;
        while(i < gdepActs.size()){
            while(i < gdepActs.size() && (depAct = gdepActs.get(i++)).getAction() != SRAction.SHIFT) {
                applyActionGold(depAct, false, 0);
                goldActs.add(depAct);
            }
            while(j < gccgActs.size() && (ccgAct = gccgActs.get(j++)).getAction() != SRAction.SHIFT){
                applyActionGold(ccgAct, false, 0);
                goldActs.add(ccgAct);
            }
            if(j<gccgActs.size()){
                applyActionGold(ccgAct, false, 0);
                goldActs.add(ccgAct);
            }
        }
        if(stack.size()>1)
            System.err.println("More than one node in the CCG stack");
        if(depstack.size()>1)
            System.err.println("More than one node in the DEP stack");
        return goldActs;
    }
    
    public void applyActionGold(ArcAction action, boolean isTrain, double val){
        SRAction sract = action.getAction();
        if(sract == SRAction.SHIFT)
            shiftGold(action, val);
        else if(sract == SRAction.RL || sract == SRAction.RR || sract == SRAction.RU)
            reduceGold(action, val);
        else if(sract == SRAction.LA || sract == SRAction.RA)
            depArcGold(action, val);
    }
    
    public CCGTreeNode shiftGold(ArcAction action, double val) {        
        CCGTreeNode result = input.get(0);                
        stack.push(result);
        input.remove(0);
        depShiftGold(result);
        return result;
    }
    
    public void depShiftGold(CCGTreeNode inode) {
        DepTreeNode depnode = new DepTreeNode(inode.getNodeId(), null);
        depstack.push(depnode);
    }
    
    public void depArcGold(ArcAction action, double val){
        DepTreeNode right = depstack.pop(), left = depstack.pop();
        SRAction sract = action.getAction();
        DepLabel deplabel = DepLabel.make(action.getLabel().toString());
        
        if(sract == SRAction.LA){
            left.setDepLabel(deplabel);
            right.add(left);
            depstack.push(right);
        }
        else{
            right.setDepLabel(deplabel);
            left.add(right);
            depstack.push(left);
        }
    }
    
    public CCGTreeNode reduceGold(ArcAction action, double val) {
        
        boolean single_child = false, head_left = false;
        Label cat = action.getLabel();
        SRAction sract = action.getAction();
        if(sract == SRAction.RU)
            single_child = true;
        else if(sract == SRAction.RR)
            head_left = true;
        
        CCGTreeNode left, right, result;
        String rescatstr = cat.toString();
        
        if (single_child) {
            left = stack.pop();
            result = applyUnary(left, rescatstr);
            stack.push(result);
        }
        else {
            right = stack.pop();
            left = stack.pop();
            result = applyBinary(left, right, rescatstr, head_left);
            stack.push(result);
        }
        return result;
    }
    
    protected CCGTreeNode applyBinary(CCGTreeNode left, CCGTreeNode right, String rescatstr, boolean isHeadLeft){        
        DataTypes.CCGCategory rescat = DataTypes.CCGCategory.make(rescatstr);
        CCGTreeNode result = CCGTreeNode.makeBinary(rescat, isHeadLeft, left, right);
        left.setParent(result);
        right.setParent(result);
        return result;
    }
    
    protected CCGTreeNode applyUnary(CCGTreeNode left, String rescatstr){
        DataTypes.CCGCategory rescat = DataTypes.CCGCategory.make(rescatstr);
        CCGTreeNode result = CCGTreeNode.makeUnary(rescat, left);
        left.setParent(result);
        return result;
    }
}

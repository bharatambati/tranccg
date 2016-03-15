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
import ilcc.ccgparser.utils.DataTypes.CCGCategory;
import ilcc.ccgparser.utils.Label;
import ilcc.ccgparser.utils.Utils.SRAction;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 *
 * @author ambati
 */
public class NonInc extends SRParser {
    
    public NonInc() throws IOException {
        
    }
    
    @Override
    public List<ArcAction> parse(CCGSentence sent) throws Exception {
        return parseGold(sent);
    }
    
    protected List<ArcAction> parseGold(CCGSentence sent) throws Exception{
        CCGTreeNode root = sent.getDerivRoot();
        ArrayList<ArcAction> gActs = new ArrayList<>();
        ArrayList<CCGTreeNode> list = new ArrayList<>();
        postOrder(root, list);
        for(CCGTreeNode node : list) {
            if(node.isLeaf()){
                gActs.add(ArcAction.make(SRAction.SHIFT, node.getCCGcat().toString()));
            }
            else if(node.getChildCount()==1){
                CCGCategory lcat = node.getLeftChild().getCCGcat();
                CCGCategory rescat = CCGCategory.make(node.getCCGcat().toString());
                CCGRuleInfo info = new CCGRuleInfo(lcat, null, rescat, true, -1);
                treebankRules.addUnaryRuleInfo(info, lcat.toString());
                gActs.add(ArcAction.make(SRAction.RU, node.getCCGcat().toString()));
                
            }
            else {
                CCGTreeNode left = node.getLeftChild(), right = node.getRightChild();
                CCGCategory lcat = left.getCCGcat(), rcat = right.getCCGcat();
                CCGCategory rescat = CCGCategory.make(node.getCCGcat().toString());
                CCGRuleInfo info = new CCGRuleInfo(lcat, rcat, rescat, (node.getHeadDir()==0), 0);
                treebankRules.addBinaryRuleInfo(info, lcat.toString()+" "+rcat.toString());
                
                if(node.getHeadDir()==0)
                    gActs.add(ArcAction.make(SRAction.RR, node.getCCGcat().toString()));
                else
                    gActs.add(ArcAction.make(SRAction.RL, node.getCCGcat().toString()));
            }
        }
        shiftReduceGold(sent, gActs);
        addtoactlist(gActs);
        return gActs;
    }
    
    private void addtoactlist(ArrayList<ArcAction> gActs){
        for(ArcAction act : gActs){
            Integer counter = actsMap.get(act);
            actsMap.put(act, counter==null ? 1 : counter+1);
        }
    }
    
    public void postOrder(CCGTreeNode root, List list){
        if(root == null)
            return;
        postOrder(root.getLeftChild(), list);
        postOrder(root.getRightChild(), list);
        list.add(root);
    }
    
    public void shiftReduceGold(CCGSentence sent, ArrayList<ArcAction> gActs) {
        input = sent.getNodes();
        stack = new Stack<>();
        for(int i = 0; i < gActs.size(); i++){
            ArcAction action = gActs.get(i);
            applyActionGold(action, false, 0);
        }
        if(stack.size()>1)
            System.err.println("More than one node in the stack");
    }
    
    public CCGTreeNode applyActionGold(ArcAction action, boolean isTrain, double val){
        String sract = action.getAction().toString();
        if(sract.equals("SHIFT"))
            return shiftGold(action, val);
        else
            return reduceGold(action, val);
    }
    
    public CCGTreeNode shiftGold(ArcAction action, double val) {
        
        CCGTreeNode result = input.get(0);
        stack.push(result);
        input.remove(0);
        return result;
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
        CCGCategory rescat = CCGCategory.make(rescatstr);
        CCGTreeNode result = CCGTreeNode.makeBinary(rescat, isHeadLeft, left, right);
        left.setParent(result);
        right.setParent(result);
        return result;
    }
    
    protected CCGTreeNode applyUnary(CCGTreeNode left, String rescatstr){
        CCGCategory rescat = CCGCategory.make(rescatstr);
        CCGTreeNode result = CCGTreeNode.makeUnary(rescat, left);
        left.setParent(result);
        return result;
    }
}
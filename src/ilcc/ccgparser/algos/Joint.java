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
import ilcc.ccgparser.utils.DataTypes.CCGCategory;
import ilcc.ccgparser.utils.DataTypes.DepLabel;
import ilcc.ccgparser.utils.DepInfo;
import ilcc.ccgparser.utils.DepTreeNode;
import ilcc.ccgparser.utils.Label;
import ilcc.ccgparser.utils.Utils;
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
public class Joint extends SRParser{
    
    public Stack<DepTreeNode> depstack;
    boolean unlabelled;
    
    public Joint(boolean flag) throws IOException {
        depstack = new Stack<>();
        unlabelled = flag;
    }
    
    @Override
    public List<ArcAction> parse(CCGSentence sent) throws Exception {
        return parseGold(sent);
    }
    
    public List<ArcAction> parseGold(CCGSentence sent){
        List<ArcAction> gActs = new ArrayList<>();
        HashMap<String, DepInfo> conllDeps = sent.getCoNLLDeps();
        CCGTreeNode root = sent.getDerivRoot();
        ArrayList<CCGTreeNode> origlist = new ArrayList<>();
        Stack<CCGTreeNode> nodes = new Stack<>();
        Utils.postOrder(root, origlist);
        for(CCGTreeNode node : origlist) {
            CCGTreeNode left, right, nnode;
            if(node.isLeaf()){
                nnode = CCGTreeNode.makeLeaf(node.getCCGcat(), node.getConllNode());
                nodes.add(nnode);
                gActs.add(ArcAction.make(SRAction.SHIFT, node.getCCGcat().toString()));
            }
            else if(node.getChildCount()==2){
                right = nodes.pop(); left = nodes.pop();
                int lid = left.getNodeId(), rid = right.getNodeId();
                String key = lid+"--"+rid;
                DepInfo depinfo = conllDeps.get(key);
                
                boolean headIsLeft = (depinfo != null) ? depinfo.IsHeadLeft() : (node.getHeadDir()==0);
                
                DepLabel dlabel;
                if(unlabelled)
                    dlabel = DepLabel.make("null");
                else{
                    ///*
                    if(depinfo==null){
                        //dlabel = (headIsLeft) ? sent.getDepNode(rid).getDepLabel() : sent.getDepNode(lid).getDepLabel();
                        //dlabel = DepLabel.make(dlabel.toString()+"-X");
                        dlabel = DepLabel.make("null");
                    }
                    else
                        dlabel = depinfo.getDepLabel();
                    //*/
                    //DepLabel dlabel = (depinfo == null) ? DepLabel.make("null") : depinfo.getDepLabel();
                }
                
                nnode = CCGTreeNode.makeBinary(node.getCCGcat(), headIsLeft, left, right);
                left.setParent(nnode);
                right.setParent(nnode);
                nodes.add(nnode);
                
                left = nnode.getLeftChild(); right = nnode.getRightChild();
                CCGCategory lcat = left.getCCGcat(), rcat = right.getCCGcat();
                CCGCategory rescat = CCGCategory.make(nnode.getCCGcat().toString());
                CCGRuleInfo info = new CCGRuleInfo(lcat, rcat, rescat, (nnode.getHeadDir()==0), dlabel, 0);
                treebankRules.addBinaryRuleInfo(info, lcat.toString()+" "+rcat.toString());
                
                if(nnode.getHeadDir()==0)
                    gActs.add(ArcAction.make(SRAction.RRA, nnode.getCCGcat().toString(), dlabel) );
                else
                    gActs.add(ArcAction.make(SRAction.RLA, nnode.getCCGcat().toString(), dlabel) );
            }
            else{
                left = nodes.pop();
                nnode = CCGTreeNode.makeUnary(node.getCCGcat(), left);
                left.setParent(nnode);
                nodes.add(nnode);
                CCGCategory lcat = nnode.getLeftChild().getCCGcat();
                CCGCategory rescat = CCGCategory.make(nnode.getCCGcat().toString());
                CCGRuleInfo info = new CCGRuleInfo(lcat, null, rescat, true, -1);
                treebankRules.addUnaryRuleInfo(info, lcat.toString());
                gActs.add(ArcAction.make(SRAction.RU, node.getCCGcat().toString()));
            }
        }
        shiftReduceGold(sent, gActs);
        addtoactlist(gActs);
        return gActs;
    }
    
    private void addtoactlist(List<ArcAction> gActs){
        for(ArcAction act : gActs){
            Integer counter = actsMap.get(act);
            actsMap.put(act, counter==null ? 1 : counter+1);
        }
    }
    
    public List<ArcAction> shiftReduceGold(CCGSentence sent, List<ArcAction> gActs) {
        
        input = sent.getNodes();
        stack = new Stack<>(); depstack = new Stack<>();
        for(int i = 0; i < gActs.size(); i++){
            ArcAction action = gActs.get(i);
            applyActionGold(action, false);
        }
        
        if(stack.size()>1)
            System.err.println("More than one node in the CCG stack");
        if(depstack.size()>1)
            System.err.println("More than one node in the DEP stack");
        return gActs;
    }
    
    public void applyActionGold(ArcAction action, boolean isTrain){
        SRAction sract = action.getAction();
        if(sract == SRAction.SHIFT)
            shiftGold(action);
        else if(sract == SRAction.RLA || sract == SRAction.RRA || sract == SRAction.RU)
            reduceGold(action);
    }
    
    public CCGTreeNode shiftGold(ArcAction action) {
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
    
    public void depArcGold(ArcAction action){
        DepTreeNode right = depstack.pop(), left = depstack.pop();
        SRAction sract = action.getAction();
        DepLabel deplabel = action.getDepLabel();
        
        if(sract == SRAction.RLA){
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
    
    public CCGTreeNode reduceGold(ArcAction action) {
        
        boolean single_child = false, head_left = false;
        Label cat = action.getLabel();
        SRAction sract = action.getAction();
        if(sract == SRAction.RU)
            single_child = true;
        else if(sract == SRAction.RRA)
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
            depArcGold(action);
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

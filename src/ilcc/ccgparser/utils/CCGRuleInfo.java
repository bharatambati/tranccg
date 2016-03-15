/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ilcc.ccgparser.utils;

import ilcc.ccgparser.utils.DataTypes.CCGCategory;
import ilcc.ccgparser.utils.DataTypes.DepLabel;
import ilcc.ccgparser.utils.ccgCombinators.RuleType;

/**
 *
 * @author ambati
 * 
 */
public class CCGRuleInfo {
    
    private final CCGCategory leftCat;
    private final CCGCategory rightCat;
    private final CCGCategory resultCat;
    private final DepLabel depLabel;
    private final RuleType combinator;
    private final int level;
    private boolean headIsLeft;
    private int count;
    
    public CCGRuleInfo(CCGCategory lcat, CCGCategory rcat, CCGCategory rescat, boolean dir, DepLabel label, int rcount){
        leftCat = lcat;
        rightCat = rcat;
        resultCat = rescat;
        headIsLeft = dir;        
        depLabel = label;
        count = rcount;
        combinator = null;
        level = 0;
    }
    
    public CCGRuleInfo(CCGCategory lcat, CCGCategory rcat, CCGCategory rescat, boolean dir, int rcount){
        leftCat = lcat;
        rightCat = rcat;
        resultCat = rescat;
        headIsLeft = dir;
        count = rcount;
        depLabel = null;
        combinator = null;
        level = 0;
    }
    
    public void setHeadDir(boolean flag){
        headIsLeft = flag;
    }
    
    public int getRuleCount(){
        return count;
    }
    
    public boolean getHeadDir(){
            return headIsLeft;
    }
    
    public DepLabel getDepLabel(){
            return depLabel;
    }
    
    public CCGCategory getLeftCat(){
        return leftCat;
    }
    
    public CCGCategory getRightCat(){
        return rightCat;
    }
    
    public CCGCategory getResultCat(){
        return resultCat;
    }
    
    public void setRuleCount(int val){
        count = val;
    }
    
    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append(leftCat.toString()); sb.append("--");
        if (rightCat != null)
            sb.append(rightCat.toString());
        sb.append("--");
        sb.append(resultCat.toString()); sb.append("--");
        sb.append(headIsLeft); sb.append("--");
        sb.append(depLabel); sb.append("--");
        sb.append(combinator); sb.append("--");
        sb.append(level);
        
        return sb.toString();
    }
    
    public String toString2(){
        StringBuilder sb = new StringBuilder();
        sb.append(leftCat.toString()); sb.append("--");
        if(rightCat != null)
            sb.append(rightCat.toString()); sb.append("--");
        sb.append(resultCat.toString()); sb.append("--");
        sb.append(headIsLeft);
        
        return sb.toString();
    }
    
    @Override
    public int hashCode(){
        return toString().hashCode();
    }
    
    @Override
    public boolean equals(Object object2) {
        return object2 instanceof CCGRuleInfo && toString().equals(((CCGRuleInfo)object2).toString());
    }    
}
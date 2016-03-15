/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ilcc.ccgparser.utils;

import ilcc.ccgparser.utils.DataTypes.DepLabel;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 *
 * @author ambati
 */
public class DepTreeNode extends DefaultMutableTreeNode {
    int nodeid;
    DepLabel deplabel;
    
    public DepTreeNode(int id){
        nodeid = id;
        deplabel = null;
    }
    
    public DepTreeNode copy(){
        return new DepTreeNode(nodeid, deplabel);
    }
    
    public DepTreeNode(int id, DepLabel label){
        nodeid = id;
        deplabel = label;
    }
    
    public int getId(){
        return nodeid;
    }
    
    public DepLabel getDepLabel(){
        return deplabel;
    }
    
    public void setDepLabel(DepLabel label){
        deplabel = label;
    }
    
    @Override
    public String toString(){
        return String.valueOf(nodeid+"--"+deplabel);
    }
}

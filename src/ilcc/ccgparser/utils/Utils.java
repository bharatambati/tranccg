/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/

package ilcc.ccgparser.utils;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import edinburgh.ccg.deps.CCGcat;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 *
 * @author ambati
 */
public class Utils {
    
    public enum SRAction {
        SHIFT, RL, RR, RLA, RRA, LA, RA, RU, LREVEAL, RREVEAL, RAR, REDUCE;
    }
    
    public static boolean isPunct(CCGcat pct){
        if (pct.catString.equals(",") || pct.catString.equals(".")
                || pct.catString.equals(";") || pct.catString.equals(":")
                || pct.catString.equals("RRB") || pct.catString.equals("LRB")
                || pct.catString.equals("``") || pct.catString.equals("\'\'") ){
            
            return true;
        }
        else
            return false;
    }
    
    public static boolean isPunct(String str){
        return ( Pattern.matches("^\\p{Punct}$", str) || Pattern.matches("^-?[LR][CR]B-?$", str) );
        //return ( Pattern.matches("^\\p{Punct}$", str) );
    }
    
    public static SCoNLLNode scNodeFromString(int wid, String lexString){
        
        SCoNLLNode cNode;
        String wrd, cpos, pos, cat;
        ArrayList<String> items = Lists.newArrayList(Splitter.on(CharMatcher.anyOf("<> ")).trimResults().omitEmptyStrings().split(lexString));
        cat = items.get(1);
        pos = items.get(2);
        cpos = items.get(3);
        wrd = items.get(4);
        cNode = new SCoNLLNode(wid, wrd, pos, cat);
        
        return cNode;
    }
    
    public static ArrayList<String> getConll(BufferedReader conllReader) throws IOException{
        String cLine;
        ArrayList<String> cLines = new ArrayList<>();
        while (!(cLine = conllReader.readLine()).equals("")) {
            cLines.add(cLine);
        }
        return cLines;
    }
    
    
    public static String getccgDeriv(BufferedReader derivReader) throws FileNotFoundException, IOException{
        String dLine;
        while (!(dLine = derivReader.readLine()).startsWith("ID=")){
            break;
        }
        return dLine;
    }
    
    public static CCGTreeNode parseDrivString(String treeString, CCGSentence sent) {
        
        sent.setCcgDeriv(treeString);
        Stack<CCGTreeNode> nodes = new Stack<>();
        Stack<Character> cStack = new Stack<>();
        char[] cArray = treeString.toCharArray();
        boolean foundOpenLessThan = false;
        int id = 0;
        
        for (Character c : cArray) {
            if (c == '<') {
                foundOpenLessThan = true;
            } else if (c == '>') {
                foundOpenLessThan = false;
            }
            
            if (c == ')' && !foundOpenLessThan) {
                StringBuilder sb = new StringBuilder();
                Character cPop = cStack.pop();
                while (cPop != '<') {
                    sb.append(cPop);
                    cPop = cStack.pop();
                }
                sb.append(cPop);
                // pop (
                cStack.pop();
                sb.reverse();
                String nodeString = sb.toString();
                // (<L (S/S)/NP IN IN In (S_113/S_113)/NP_114>)
                if (nodeString.charAt(1) == 'L') {
                    SCoNLLNode cnode = Utils.scNodeFromString(id+1, nodeString);
                    CCGTreeNode node = CCGTreeNode.makeLeaf(cnode.getccgCat(), cnode);
                    sent.addCCGTreeNode(node);
                    nodes.add(node);
                    id++;
                }
                else if (nodeString.charAt(1) == 'T') {
                    // (<T S/S 0 2> (<L (S/S)/NP IN IN In (S_113/S_113)/NP_114>)
                    ArrayList<String> items = Lists.newArrayList(Splitter.on(CharMatcher.anyOf("<> ")).trimResults().omitEmptyStrings().split(nodeString));
                    DataTypes.CCGCategory rescat = DataTypes.CCGCategory.make(items.get(1));
                    boolean headIsLeft = (Integer.parseInt(items.get(2))==0);
                    int childrenSize = Integer.parseInt(items.get(3));
                    CCGTreeNode node, left, right;
                    int size = nodes.size();
                    
                    if(childrenSize == 2){
                        left = nodes.get(size-2);
                        right = nodes.get(size-1);
                        // NAACL2015: Change of Head rule for auxiliary
                        //if(CCGcat.ccgCatFromString(left.getCCGcat().toString()).isAux()) headIsLeft = false;
                        node = CCGTreeNode.makeBinary(rescat, headIsLeft, left, right);
                        left.setParent(node);
                        right.setParent(node);
                    }
                    else {
                        left = nodes.get(size-1);
                        node = CCGTreeNode.makeUnary(rescat, left);
                        left.setParent(node);
                    }
                    while (childrenSize > 0) {
                        nodes.pop();
                        childrenSize--;
                    }
                    nodes.add(node);
                }
            } else {
                cStack.add(c);
            }
        }
        
        Preconditions.checkArgument(nodes.size() == 1, "Bad Tree");
        CCGTreeNode root = nodes.pop();
        sent.setccgDerivTree(root);
        return root;
    }   
    
    public static void postOrder(CCGTreeNode root, List list){
        if(root == null)
            return;
        postOrder(root.getLeftChild(), list);
        postOrder(root.getRightChild(), list);
        list.add(root);
    }

    
    public static <K,V extends Comparable<? super V>>
                    SortedSet<Map.Entry<K,V>> entriesSortedByValues(Map<K,V> map) {
                        SortedSet<Map.Entry<K,V>> sortedEntries = new TreeSet<Map.Entry<K,V>>(
                                new Comparator<Map.Entry<K,V>>() {
                                    @Override public int compare(Map.Entry<K,V> e1, Map.Entry<K,V> e2) {
                                        int res = e2.getValue().compareTo(e1.getValue());
                                        return res != 0 ? res : 1;
                                    }
                                }
                        );
                        sortedEntries.addAll(map.entrySet());
                        return sortedEntries;
                    }
                    
}

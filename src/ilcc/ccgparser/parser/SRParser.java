/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/

package ilcc.ccgparser.parser;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import ilcc.ccgparser.utils.*;
import ilcc.ccgparser.utils.DataTypes.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 *
 * @author ambati
 */
public abstract class SRParser {
    
    public Stack<CCGTreeNode> stack;
    public List<CCGTreeNode> input;
    public CCGRules treebankRules;
    public HashMap<String, List<ArcAction>> depRules;
    public CCGSentence sent;
    public HashMap<String, DepInfo> goldconllDeps;
    public HashMap<Integer, DepTreeNode> gconllDeps, sconllDeps;
    public Map<ArcAction, Integer> actsMap;
    public List<ArcAction> actsList;
    
    public HashMap<String, CCGDepInfo> goldccgDeps, sysccgDeps;
    
    public int sentCount, parsedSents;
    int uGold, uSys, uCorr, lGold, lSys, lCorr, lcCorr, lcSys, lcGold, totCat, corrCat;
    int udGold, udSys, udCorr, ldGold, ldSys, ldCorr;
    public double UF, LF, UAS, LAS;
    
    abstract public List<ArcAction> parse(CCGSentence sent) throws Exception;
    
    public SRParser() throws IOException{
        init();
        treebankRules = new CCGRules();
        depRules = new HashMap<>();
        actsMap = new HashMap<>();
    }
    
    public void init() throws IOException{
        stack = new Stack<>();
        input = new ArrayList<>();
        
        goldccgDeps = new HashMap<>();
        sysccgDeps = new HashMap<>();
        
        sentCount = parsedSents = 0;
        uGold = uSys = uCorr = lGold = lSys = lCorr = lcCorr = lcSys = lcGold = totCat = corrCat = 0;
        udGold = udSys = udCorr = ldGold = ldSys = ldCorr = 0;
        
        sent = new CCGSentence();
    }
    
    public void fillData(String conllFile, String derivFile, String depsFile, Map<Integer, GoldccgInfo> goldDetails) throws IOException, Exception {
        
        BufferedReader conllReader = new BufferedReader(new FileReader(conllFile));
        BufferedReader derivReader = new BufferedReader(new FileReader(derivFile));
        //BufferedReader depReader = new BufferedReader(new FileReader(depsFile));
        String dLine;
        ArrayList<String> cLines;
        
        while (derivReader.readLine() != null) {
            updateSentCount();
            //getCCGDepsParg(depReader);
            cLines = getConll(conllReader);
            sent = new CCGSentence();
            dLine = getccgDeriv(derivReader);
            updateConllDeps(cLines);
            CCGTreeNode root = parseDrivString(dLine, sent);
            sent.updateCCGCats(cLines);
            sent.updateConllDeps(cLines);
            List<ArcAction> actslist = parse(sent);
            goldDetails.put(sentCount, new GoldccgInfo(actslist, goldccgDeps, sent));
            //evaluateParseDependenciesJulia();
            //resetVars();
        }
        //parseSents(goldDetails);
        //printResults();
        updateActMap();
    }
    
    public void updateActMap(){
        HashMap<ArcAction, Integer> map = new HashMap();
        actsList = new ArrayList<>(actsMap.size());
        int index = 0;
        for(ArcAction act : actsMap.keySet()){
            actsList.add(act);
            map.put(act, index);
            index++;
        }
        Collections.sort(actsList);
        actsMap = map;
    }
    
    public void updateConllDeps(ArrayList<String> lines){
        goldconllDeps = new HashMap<>();
        for(String line:lines){
            String[] parts = line.split("\t");
            if(parts.length >= 8){
                int cid = Integer.parseInt(parts[0]), pid = Integer.parseInt(parts[6]);
                if(pid != 0 && !parts[7].equals("punct"))
                    goldconllDeps.put((cid<pid) ? (cid+"--"+pid) : (pid+"--"+cid), new DepInfo(DepLabel.make(parts[7]), (cid>pid) ));
            }
        }
    }
    
    private void updateSentCount(){
        sentCount++;
        if(sentCount%1000 == 0)
            System.err.println(sentCount);
        else if(sentCount%100 == 0)
            System.err.print(sentCount+" ");
    }
    
    public ArrayList<String> getConll(BufferedReader conllReader) throws IOException{
        String cLine;
        ArrayList<String> cLines = new ArrayList<>();
        while (!(cLine = conllReader.readLine()).equals("")) {
            cLines.add(cLine);
        }
        return cLines;
    }
    
    public String getccgDeriv(BufferedReader derivReader) throws FileNotFoundException, IOException{
        String dLine;
        while (!(dLine = derivReader.readLine()).startsWith("ID=")){
            break;
        }
        return dLine;
    }
    
    public CCGTreeNode parseDrivString(String treeString, CCGSentence sent) {
        
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
                    CCGCategory rescat = CCGCategory.make(items.get(1));
                    boolean headIsLeft;
                    int childrenSize = Integer.parseInt(items.get(3));
                    CCGTreeNode node, left, right;
                    int size = nodes.size();
                    
                    if(childrenSize == 2){
                        left = nodes.get(size-2);
                        right = nodes.get(size-1);
                        
                        headIsLeft = (Integer.parseInt(items.get(2))==0);
                        
                        /*
                        int lid = left.getNodeId(), rid = right.getNodeId();
                        DepInfo info;
                        String key = lid+"--"+rid;
                        if((info = goldconllDeps.get(key)) != null){
                        headIsLeft = info.IsHeadLeft();
                        goldconllDeps.remove(key);
                        }
                        else
                        headIsLeft = (Integer.parseInt(items.get(2))==0);
                        //*/
                        
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
    
    public void writeDeps(BufferedWriter opWriter) throws IOException{
        for(String key : sysccgDeps.keySet()){
            StringBuilder depstr = new StringBuilder("");
            CCGDepInfo sdinfo = sysccgDeps.get(key);
            int hid = sdinfo.getHeadId();
            int aid = sdinfo.getArgId();
            depstr.append(sent.getNode(hid-1).getWrdStr()); depstr.append("_");depstr.append(hid);
            depstr.append(" (");depstr.append(sdinfo.getCat());depstr.append(") ");            
            depstr.append(sdinfo.getSlot());depstr.append(" ");
            depstr.append(sent.getNode(aid-1).getWrdStr()); depstr.append("_");depstr.append(aid);
            depstr.append(" 0");
            opWriter.write(depstr.toString()+"\n");
        }
        
        StringBuilder sb = new StringBuilder("<c> ");
        for(CCGTreeNode node : sent.getNodes()){
            CCGCategory acat = null;
            sb.append(node.getWrdStr());sb.append("|");
            sb.append(node.getPOS());sb.append("|");
            sb.append(acat);sb.append(" ");
        }
        opWriter.write(sb.toString().trim()+"\n\n");
        opWriter.flush();
    }
    
    public void updateCatAccuray(CCGSentence gsent, HashMap<Integer, CCGCategory> sysCats){
        for(CCGTreeNode node : gsent.getNodes()){
            //CCGCategory acat = depGraph.getVertex(node.getNodeId());
            CCGCategory acat = sysCats.get(node.getConllNode().getNodeId());
            CCGCategory gcat = node.getConllNode().getccgCat();
            if(gcat.equals(acat))
                corrCat++;
        }
        totCat += gsent.getNodes().size();
    }
    
    public void initevaluate(){
        
    }
    
    public void evaluateParseDependenciesJulia(boolean joint){
        int sGoldDeps, sSysDeps, sCorrDeps = 0, lsCorrDeps = 0;
        sGoldDeps = goldccgDeps.size();
        sSysDeps = sysccgDeps.size();
        //System.err.println(sentCount+"\t"+goldccgDeps+"\n"+sysccgDeps);
        for(String key : sysccgDeps.keySet()){
            CCGDepInfo sdinfo = sysccgDeps.get(key);
            if(goldccgDeps.containsKey(key)){
                //System.err.println(sentCount+" : Deps in System out: "+sdinfo.ccgDepStr());
                sCorrDeps++;
                CCGDepInfo gdinfo = goldccgDeps.get(key);
                if(sdinfo.getCat().equals(gdinfo.getCat()) && sdinfo.getSlot()==gdinfo.getSlot()){
                    lsCorrDeps++;
                    //goldccgDeps.remove(key);
                }
            }
        }
        
        if(joint)
            evalConllDeps();
        
        uGold += sGoldDeps;
        uSys += sSysDeps;
        uCorr += sCorrDeps;
        lCorr += lsCorrDeps;
        
        if(stack.size() == 1)
            parsedSents++;
        
        //goldccgDeps.clear();
        sysccgDeps.clear();
    }
    
    private void evalConllDeps(){
        for(int id : gconllDeps.keySet()){
            DepTreeNode snode = sconllDeps.get(id);
            DepTreeNode gnode = gconllDeps.get(id);
            DepLabel glabel = gnode.getDepLabel();
            DepLabel slabel = snode.getDepLabel();
            //if(!Utils.isPunct(sent.getNode(id-1).getWrdStr())){
            if(!glabel.toString().equals("punct")){
                udGold++;
                if(gnode.getId() == snode.getId()){
                    udCorr++;
                    if(glabel.equals(slabel))
                        ldCorr++;
                }
            }
        }
        gconllDeps.clear();
        sconllDeps.clear();
    }
    
    public void printResults(){
        DecimalFormat df = new DecimalFormat(".00");
        System.err.println();
        //System.err.println("Total # Sentences, Words: "+sentCount+" , "+wordCount);
        //System.err.println("Total # Nodes: "+nodeCount);
        //System.err.println("Ave. nodes per stack: "+df.format(1.0*nodeCount/sentCount));
        System.err.println("Coverage: ( "+parsedSents+" / "+sentCount+" ) "+df.format(100.00*parsedSents/sentCount));
        System.err.println("goldccgDeps, sysDeps, corrDeps : "+uGold+" "+uSys+" "+uCorr);
        double UP = 100.00*uCorr/uSys;
        double UR = 100.00*uCorr/uGold;
        UF = (2.00*UP*UR)/(UP+UR);
        
        double LP = 100.00*lCorr/uSys;
        double LR = 100.00*lCorr/uGold;
        LF = (2.00*LP*LR)/(LP+LR);
        
        double cLP = 100.00*lcCorr/lcSys;
        double cLR = 100.00*lcCorr/lcGold;
        double cLF = (2.00*cLP*cLR)/(cLP+cLR);
        
        UAS = 100.00*udCorr/udGold;
        LAS = 100.00*ldCorr/udGold;
        
        System.err.println(" Unlabelled Prec : "+df.format(UP)+" Rec : "+df.format(UR)+" F-score : "+df.format(UF));
        System.err.println(" Labelled Prec : "+df.format(LP)+"  Rec : "+df.format(LR)+" F-score : "+df.format(LF));
        System.err.println(" UAS : "+udCorr+" / "+udGold+" = "+df.format(UAS)+"  LAS : "+ldCorr+" / "+udGold+" = "+df.format(LAS));
        System.err.println(" Category Accuracy : "+df.format(corrCat)+"/"+df.format(totCat)+" = "+df.format(100.00*corrCat/totCat));
        //System.err.println("cLabelled Prec : "+df.format(cLP)+"  Rec : "+df.format(cLR)+" F-score : "+df.format(cLF));
    }
}
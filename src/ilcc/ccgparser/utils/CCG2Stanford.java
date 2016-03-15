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
import ilcc.ccgparser.utils.DataTypes.CCGCategory;
import ilcc.ccgparser.utils.DataTypes.DepLabel;
import ilcc.ccgparser.utils.DataTypes.CDKey;
import ilcc.ccgparser.utils.DataTypes.Word;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

/**
 *
 * @author ambati
 */
public class CCG2Stanford {
    public Map<String, DepInfo> conllDeps = new HashMap<>();
    public Map<Word, Integer> lexMap = new HashMap<>();
    public Map<CDKey, HashMap<DepInfo, Integer>> cdmap, cdmap3, cdmap5, cdmap7a, cdmap7b, cdmap8;
    public Map<CDKey, DepInfo> ncdmap, ncdmap3, ncdmap5, ncdmap7a, ncdmap7b, ncdmap8;
    
    public Map<Integer, DepTreeNode> cdtNodes = new HashMap<>();
    public Map<Integer, CCGTreeNode> ccgTreeNodes = new HashMap<>();
    
    public int sentCount = 0, rootid = 0, uas = 0, las = 0 , total = 0, lexThreshold = 10;
    boolean istrain = true;
    
    
    private void initVars(){
        sentCount = rootid = uas = las = total = 0;
        lexThreshold = 10;
        ncdmap = new HashMap(); ncdmap3 = new HashMap(); ncdmap5 = new HashMap();
        ncdmap7a = new HashMap(); ncdmap7b = new HashMap(); ncdmap8 = new HashMap();
    }
    
    private void updateMap(Map<CDKey, HashMap<DepInfo, Integer>> cdmap, Map<CDKey, DepInfo> ncdmap){        
        for(CDKey key : cdmap.keySet()){
            HashMap<DepInfo, Integer> dmap = cdmap.get(key);
            DepInfo info = null;
            for(Iterator it = Utils.entriesSortedByValues(dmap).iterator(); it.hasNext();) {
                Map.Entry entry = (Map.Entry) it.next();
                info = (DepInfo) entry.getKey();
                break;
            }
            ncdmap.put(key, info);
        }        
    }
    
    public void convert2stanford(String conllFile, String derivFile, String pargFile) throws IOException, Exception {        
        initVars();        
        updateMap(cdmap3, ncdmap3);
        updateMap(cdmap5, ncdmap5);
        updateMap(cdmap7a, ncdmap7a);
        updateMap(cdmap7b, ncdmap7b);
        BufferedReader conllReader = new BufferedReader(new FileReader(conllFile));
        BufferedReader derivReader = new BufferedReader(new FileReader(derivFile));
        //BufferedReader depReader = new BufferedReader(new FileReader(pargFile));
        String dLine;
        ArrayList<String> cLines;
        CCGSentence sent;
        
        while (derivReader.readLine() != null) {
            sentCount++;
            sent = new CCGSentence();
            cLines = Utils.getConll(conllReader);
            dLine = Utils.getccgDeriv(derivReader);            
            updateConllDeps(cLines);
            CCGTreeNode root = Utils.parseDrivString(dLine, sent);
            DepTreeNode droot = ccgtree2deptree(root);
            DepTreeNode nroot = updateDepTree(droot);
            //DepTreeNode nroot = droot;
            evaluate(nroot);
        }
        System.err.println("UAS: "+uas+" / "+total+" = "+(100.00*uas)/total);
        System.err.println("LAS: "+las+" / "+total+" = "+(100.00*las)/total);
        //printcdmap();
    }
    
    public void extractccg2stanrules(String conllFile, String derivFile, String pargFile) throws IOException, Exception {
        
        initVars();
        cdmap = new HashMap(); cdmap3 = new HashMap(); cdmap5 = new HashMap();
        cdmap7a = new HashMap(); cdmap7b = new HashMap(); cdmap8 = new HashMap();
        
        BufferedReader conllReader = new BufferedReader(new FileReader(conllFile));
        BufferedReader derivReader = new BufferedReader(new FileReader(derivFile));
        //BufferedReader depReader = new BufferedReader(new FileReader(pargFile));
        String dLine;
        ArrayList<String> cLines;
        CCGSentence sent;
        HashMap<String, Integer> unresmap = new HashMap<>();
        int id = 1, deps = 0, unres = 0;
        
        while (derivReader.readLine() != null) {
            sentCount++;
            sent = new CCGSentence();
            cLines = Utils.getConll(conllReader);
            dLine = Utils.getccgDeriv(derivReader);            
            updateConllDeps(cLines);
            deps += conllDeps.size();
            CCGTreeNode root = parseDrivString(dLine, sent);
            DepTreeNode droot = ccgtree2deptreetrain(root);
            DepTreeNode nroot = updateDepTree(droot);
            evaluate(nroot);
            int un = conllDeps.size();
            if(un > 0){
                for(DepInfo info : conllDeps.values()){
                    String label = info.getDepLabel().getDepLabelStr();
                    if(unresmap.containsKey(label))
                        unresmap.put(label, unresmap.get(label)+1);
                    else
                        unresmap.put(label, 1);                                
                }                    
                //System.err.println(sentCount+" : "+un+" "+conllDeps.keySet()+" "+conllDeps.values());
            }
            unres += un;
        }
        System.err.println("Un-resolved stanford deps ( "+unres+" / "+deps +" ) = "+ (100.00*unres)/deps);
        for(Iterator it = Utils.entriesSortedByValues(unresmap).iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            System.err.println(entry.getKey()+" "+entry.getValue());
        }
        System.err.println("UAS: "+uas+" / "+total+" = "+(100.00*uas)/total);
        System.err.println("LAS: "+las+" / "+total+" = "+(100.00*las)/total);
        //printcdmap();
    }
    
    private void printcdmap(){
        Map<CDKey, Integer> tmap = new HashMap<>();
        for(CDKey catkey : cdmap5.keySet()){
            int sum = 0;
            for(int val : cdmap5.get(catkey).values())
                sum += val;
            tmap.put(catkey, sum);
        }
        
        for (Iterator it = Utils.entriesSortedByValues(tmap).iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            CDKey catkey = (CDKey) entry.getKey();
        //for(String catkey : cdmap.keySet()){
            Map<DepInfo, Integer> depmap = cdmap5.get(catkey);
            System.err.println(catkey+" "+Utils.entriesSortedByValues(depmap));
        }
    }
    
    private DepTreeNode updateDepTree(DepTreeNode droot){
        DepTreeNode nroot = droot;
        
        for(CCGTreeNode node : ccgTreeNodes.values()){
            DepTreeNode dnode = cdtNodes.get(node.getNodeId());
            if(node.getCCGcat().getCatStr().equals("conj")){
                ArrayList<DepTreeNode> conjnodes = new ArrayList<>();
                dnode.setDepLabel(DepLabel.make("cc"));
                conjnodes.add(dnode);
                while(((DepTreeNode) dnode.getParent()).getDepLabel().getDepLabelStr().equals("conj")){
                    dnode = (DepTreeNode) dnode.getParent();
                    conjnodes.add(dnode);
                }
                dnode = (DepTreeNode) dnode.getParent();
                DepTreeNode ccnode = conjnodes.get(0);
                conjnodes.remove(0);
                ccnode.setParent(dnode);
                for(int i=0; i<conjnodes.size()-1;i++){
                    DepTreeNode conjnode = conjnodes.get(i);
                    conjnode.setParent(dnode);
                    conjnode.setDepLabel(DepLabel.make("conj"));
                }
            }
            if(node.getHeadWrd().getWord().matches("n[o']?t|never")){
                if(!dnode.getDepLabel().getDepLabelStr().equals("neg") && dnode.getParent() != null){
                    DepTreeNode gparent = (DepTreeNode) dnode.getParent().getParent();
                    dnode.setParent(gparent);
                    dnode.setDepLabel(DepLabel.make("neg"));
                }
            }
            if(node.getPOS().getPos().equals("CD") && dnode.getParent() != null){
                int parid = ((DepTreeNode) dnode.getParent()).getId();
                if(ccgTreeNodes.get(parid).getPOS().getPos().equals("CD") && !dnode.getDepLabel().toString().equals("number")){
                    DepTreeNode gparent = (DepTreeNode) dnode.getParent().getParent();
                    dnode.setParent(gparent);
                    dnode.setDepLabel(DepLabel.make("number"));
                }
            }
        }
        
        for(DepTreeNode dnode : cdtNodes.values()){
            if(dnode.getDepLabel().toString().equals("null")){
                DepTreeNode parent = (DepTreeNode) dnode.getParent();
                DepTreeNode par = null;
                DepLabel deplabel = null; 
                if(ccgTreeNodes.get(parent.getId()).getCCGcat().getCatStr().contains("S[ng]")){
                    String catstr = ccgTreeNodes.get(dnode.getId()).getCCGcat().getCatStr();
                    if(catstr.equals("(S\\NP)\\(S\\NP)")){                        
                        par = (DepTreeNode) dnode.getParent().getParent();
                        deplabel = DepLabel.make("advmod");
                    }
                    if(catstr.equals("N")){
                        try{
                            par = (DepTreeNode) dnode.getNextSibling();
                            deplabel = DepLabel.make("nsubj");
                        }
                        catch(java.lang.Error ex){
                            System.err.println(ex);
                        }
                    }
                }
                else if(ccgTreeNodes.get(dnode.getId()).getPOS().getPos().equals("RB")){
                    par = (DepTreeNode) dnode.getParent().getParent();
                    deplabel = DepLabel.make("advmod");
                }
                else if(ccgTreeNodes.get(dnode.getId()).getCCGcat().getCatStr().contains("S[em]")){
                    par = (DepTreeNode) dnode.getParent().getParent();
                    deplabel = DepLabel.make("dep");
                }
                else if(ccgTreeNodes.get(dnode.getId()).getPOS().getPos().equals("CC")){
                    CCGTreeNode left = ccgTreeNodes.get(dnode.getId()-1);
                    CCGTreeNode right = ccgTreeNodes.get(dnode.getId()+1);                    
                    
                    if(left != null && right != null && left.getCCGcat().equals(right.getCCGcat())){
                        par = cdtNodes.get(left.getNodeId());
                        par.add(dnode); dnode.setDepLabel(DepLabel.make("cc"));
                        DepTreeNode rnode = cdtNodes.get(right.getNodeId());
                        par.add(rnode); rnode.setDepLabel(DepLabel.make("conj"));
                        par = null;
                    }
                }
                else{
                    dnode.setDepLabel(DepLabel.make("dep"));
                }
                if(par != null){
                    par.add(dnode); 
                    //dnode.setParent(par); 
                    dnode.setDepLabel(deplabel);
                }
            }
        }
        
        return nroot;
    }
    
    private void evaluate(DepTreeNode droot){
        total += conllDeps.size();
        Enumeration bps = droot.depthFirstEnumeration();
        while(bps.hasMoreElements()){
            DepTreeNode node = (DepTreeNode) bps.nextElement();
            int pid, cid = node.getId();
            DepTreeNode parent = (DepTreeNode) node.getParent();
            pid = (parent != null) ? parent.getId() : 0;
            String key = (cid<pid) ? cid+"--"+pid : pid+"--"+cid;
            if(conllDeps.containsKey(key)){
                uas++;
                if(node.getDepLabel().equals(conllDeps.get(key).getDepLabel()))
                    las++;
                conllDeps.remove(key);
            }
        }
    }
    
    private void updateLexMap(CCGTreeNode node){
        Word wrd = node.getConllNode().getWrd();
        Word nwrd = Word.make(wrd.getWord().replaceAll("[0-9]", "0"));
        Integer count;
        if((count = lexMap.get(nwrd)) != null)
            lexMap.put(nwrd, count+1);
        else
            lexMap.put(nwrd, 1);
    }
    
    public DepTreeNode ccgtree2deptree(CCGTreeNode root){
        ArrayList<CCGTreeNode> origlist = new ArrayList<>();
        Stack<CCGTreeNode> nodes = new Stack<>();
        Utils.postOrder(root, origlist);
        for(CCGTreeNode node : origlist) {
            CCGTreeNode left, right, nnode;
            if(node.isLeaf()){
                int id = node.getNodeId();
                cdtNodes.put(id, new DepTreeNode(id, null));
                ccgTreeNodes.put(id, node);
                nnode = CCGTreeNode.makeLeaf(node.getCCGcat(), node.getConllNode());
                nodes.add(node);
            }
            else if(node.getChildCount()==2){
                
                CCGCategory rescat = node.getCCGcat();
                int headDir = node.getHeadDir();
                right = nodes.pop(); left = nodes.pop();
                int lid = left.getNodeId(), rid = right.getNodeId();
                boolean headIsLeft = (headDir==0);
                CDKey cdkey3, cdkey5, cdkey7a, cdkey7b;
                cdkey3 = CDKey.make(left.getCCGcat().getCatStr()+"--"+right.getCCGcat().getCatStr()+"::"+rescat.getCatStr()+"--"+headIsLeft);
                cdkey5 = CDKey.make(cdkey3.toString()+"--"+left.getConllNode().getPOS().getPos()+"--"+right.getConllNode().getPOS().getPos());
                
                String lwrd = left.getConllNode().getWrd().getWord().replaceAll("[0-9]", "0"), rwrd = right.getConllNode().getWrd().getWord().replaceAll("[0-9]", "0");
                cdkey7a = CDKey.make(cdkey5.toString()+"--"+lwrd+"--"+rwrd);
            
                if(lexMap.get(Word.make(lwrd)) == null || lexMap.get(Word.make(lwrd)) < lexThreshold)
                    lwrd = "NULL";
                if(lexMap.get(Word.make(rwrd)) == null || lexMap.get(Word.make(rwrd)) < lexThreshold)
                    rwrd = "NULL";
                cdkey7b = CDKey.make(cdkey5.toString()+"--"+lwrd+"--"+rwrd);
                
                DepInfo info;
                if((info = ncdmap7a.get(cdkey7a)) == null){
                    if((info = ncdmap7b.get(cdkey7a)) == null)
                        if((info = ncdmap5.get(cdkey5)) == null)
                            if((info = ncdmap3.get(cdkey3)) == null)
                                info = new DepInfo(DepLabel.make("null"), headIsLeft);
                }
                
                if(right.getCCGcat().getCatStr().endsWith("[conj]"))
                    updateDepTree(info.IsHeadLeft(), lid, rid, DepLabel.make("conj"));
                else
                    updateDepTree(info.IsHeadLeft(), lid, rid, info.getDepLabel());
                nnode = CCGTreeNode.makeBinary(node.getCCGcat(), info.IsHeadLeft(), left, right);
                left.setParent(nnode);
                right.setParent(nnode);
                nodes.add(nnode);
            }
            else{
                left = nodes.pop();
                nnode = CCGTreeNode.makeUnary(node.getCCGcat(), left);
                left.setParent(nnode);
                nodes.add(nnode);
            }
            rootid = nnode.getConllNode().getNodeId();
        }
        cdtNodes.get(rootid).setDepLabel(DepLabel.make("root"));
        return cdtNodes.get(rootid);
    }
    
    public DepTreeNode ccgtree2deptreetrain(CCGTreeNode root){
        ArrayList<CCGTreeNode> origlist = new ArrayList<>();
        Stack<CCGTreeNode> nodes = new Stack<>();
        Utils.postOrder(root, origlist);
        for(CCGTreeNode node : origlist) {
            CCGTreeNode left, right, nnode;
            if(node.isLeaf()){
                int id = node.getNodeId();
                cdtNodes.put(id, new DepTreeNode(id, null));                
                ccgTreeNodes.put(id, node);
                updateLexMap(node);
                nnode = CCGTreeNode.makeLeaf(node.getCCGcat(), node.getConllNode());
                nodes.add(nnode);
            }
            else if(node.getChildCount()==2){
                DepInfo info;
                right = nodes.pop(); left = nodes.pop();
                int lid = left.getNodeId(), rid = right.getNodeId();
                String key = lid+"--"+rid;
                boolean headIsLeft;
                if((info = conllDeps.get(key)) != null){                    
                    headIsLeft = updatecdmap(left, right, node.getCCGcat(), node.getHeadDir());
                    updateDepTree(headIsLeft, lid, rid, info.getDepLabel());
                }
                else {
                    headIsLeft = (node.getHeadDir()==0);
                    //if(!Utils.isPunct(left.getWrdStr()) && !Utils.isPunct(right.getWrdStr()))
                    //    System.err.println(sentCount+" Dep not in conlldeps " + left.getWrdStr()+"  "+right.getWrdStr());
                    if(right.getCCGcat().getCatStr().endsWith("[conj]"))
                        updateDepTree(headIsLeft, lid, rid, DepLabel.make("conj"));
                    else
                        updateDepTree(headIsLeft, lid, rid, DepLabel.make("null"));
                }
                nnode = CCGTreeNode.makeBinary(node.getCCGcat(), headIsLeft, left, right);
                left.setParent(nnode);
                right.setParent(nnode);
                nodes.add(nnode);
            }
            else{
                left = nodes.pop();
                nnode = CCGTreeNode.makeUnary(node.getCCGcat(), left);
                left.setParent(nnode);
                nodes.add(nnode);
            }
            rootid = nnode.getConllNode().getNodeId();
        }
        cdtNodes.get(rootid).setDepLabel(DepLabel.make("root"));
        return cdtNodes.get(rootid);
    }
    
    public void updateDepTree(boolean headIsLeft, int lid, int rid, DepLabel label){
        
        if(headIsLeft){
            cdtNodes.get(rid).setDepLabel(label);
            cdtNodes.get(lid).add(cdtNodes.get(rid));
        }
        else{
            cdtNodes.get(lid).setDepLabel(label);
            cdtNodes.get(rid).insert(cdtNodes.get(lid), 0);
        }
    }
    
    private boolean updatecdmap(CCGTreeNode left, CCGTreeNode right, CCGCategory rescat, int headDir){
        boolean headIsLeft;
        ///*
        int lid = left.getNodeId(), rid = right.getNodeId();
        DepInfo info;
        String key = lid+"--"+rid;
        if((info = conllDeps.get(key)) != null){
            headIsLeft = info.IsHeadLeft();
            CDKey cdkey, cdkey3, cdkey5, cdkey7a, cdkey7b, cdkey8;
            cdkey3 = CDKey.make(left.getCCGcat().getCatStr()+"--"+right.getCCGcat().getCatStr()+"::"+rescat.getCatStr()+"--"+(headDir==0));
            cdkey5 = CDKey.make(cdkey3.toString()+"--"+left.getConllNode().getPOS().getPos()+"--"+right.getConllNode().getPOS().getPos());
            String lwrd = left.getConllNode().getWrd().getWord().replaceAll("[0-9]", "0"), rwrd = right.getConllNode().getWrd().getWord().replaceAll("[0-9]", "0");
            cdkey7a = CDKey.make(cdkey5.toString()+"--"+lwrd+"--"+rwrd);
            
            if(lexMap.get(Word.make(lwrd)) == null || lexMap.get(Word.make(lwrd)) < lexThreshold)
                lwrd = "NULL";
            if(lexMap.get(Word.make(rwrd)) == null || lexMap.get(Word.make(rwrd)) < lexThreshold)
                rwrd = "NULL";
            cdkey7b = CDKey.make(cdkey5.toString()+"--"+lwrd+"--"+rwrd);
            //cdkey8 = CDKey.make(cdkey7.toString()+"--"+(right.getConllNode().getNodeId()-left.getConllNode().getNodeId()));
            DepInfo depkey = new DepInfo(info.getDepLabel(), headIsLeft);
            updatemap(cdkey3, cdmap3, depkey);
            updatemap(cdkey5, cdmap5, depkey);
            updatemap(cdkey7a, cdmap7a, depkey);
            updatemap(cdkey7b, cdmap7b, depkey);
        }
        else {
            headIsLeft = (headDir==0);
            //if(!Utils.isPunct(left.getWrdStr()) && !Utils.isPunct(right.getWrdStr()))
            //    System.err.println(sentCount+" Dep not in conlldeps " + left.getWrdStr()+"  "+right.getWrdStr());
        }
        //*/
        return headIsLeft;
    }
    
    private void updatemap(CDKey key, Map<CDKey, HashMap<DepInfo, Integer>> map, DepInfo depkey){
        
            HashMap<DepInfo, Integer> depmap;
            if((depmap = map.get(key))!=null){
                if(depmap.containsKey(depkey))
                    depmap.put(depkey, depmap.get(depkey)+1);
                else
                    depmap.put(depkey, 1);
            }
            else{
                depmap = new HashMap<>();
                depmap.put(depkey, 1);
            }
            map.put(key, depmap);
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
                    id++;
                    SCoNLLNode cnode = Utils.scNodeFromString(id, nodeString);
                    CCGTreeNode node = CCGTreeNode.makeLeaf(cnode.getccgCat(), cnode);
                    sent.addCCGTreeNode(node);
                    nodes.add(node);
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
    
    public void updateConllDeps(ArrayList<String> lines){
        conllDeps.clear(); cdtNodes.clear(); ccgTreeNodes.clear();
        for(String line:lines){
            String[] parts = line.split("\t");
            if(parts.length >= 8){                
                int cid = Integer.parseInt(parts[0]), pid = Integer.parseInt(parts[6]);
                if(!parts[7].equals("punct"))
                    conllDeps.put((cid<pid) ? (cid+"--"+pid) : (pid+"--"+cid), new DepInfo(DepLabel.make(parts[7]), (cid>pid)));
            }
        }
    }
    
    public static void main(String[] args) throws IOException, Exception {
        
        String autoFile, conllFile, pargFile, tautoFile, tconllFile, tpargFile;
        
        if(args.length==6){
            tautoFile = args[0];
            tconllFile = args[1];
            tpargFile = args[2];
            autoFile = args[3];
            conllFile = args[4];
            pargFile = args[5];
        }
        else{
            System.err.println("java -jar CCGParser.jar <auto-file> <conll-file> <parg-file>");
            String home = "/home/ambati/ilcc/projects/parsing/experiments/english/ccg/";
            tautoFile = home+"data/final/train.gccg.auto";
            tconllFile = home+"data/final/train.accg.conll";
            tpargFile = home+"data/final/train.gccg.parg";
            autoFile = home+"data/final/devel.gccg.auto";
            conllFile = home+"data/final/devel.accg.conll";
            pargFile = home+"data/final/devel.gccg.parg";
        }
        CCG2Stanford c2s = new CCG2Stanford();
        c2s.extractccg2stanrules(conllFile, autoFile, pargFile);
        //c2s.extractccg2stanrules(tconllFile, tautoFile, tpargFile);
        c2s.istrain = false;
        c2s.convert2stanford(conllFile, autoFile, pargFile);
    }
}

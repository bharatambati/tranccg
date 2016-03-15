/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ilcc.ccgparser.parser;

import ilcc.ccgparser.utils.CCGTreeNode;
import ilcc.ccgparser.utils.SCoNLLNode;
import ilcc.ccgparser.utils.DataTypes.*;
import ilcc.ccgparser.learn.Feature;
import ilcc.ccgparser.learn.Feature.FeatPrefix;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;


/**
 *
 * @author ambati
 */

public class Context {
   
   private final CCGTreeNode s0, s1, s2, s3;
   private final CCGTreeNode s0l, s0r, s0u, s0h;
   private final CCGTreeNode s1l, s1r, s1u, s1h;
   private final CCGTreeNode q0, q1, q2, q3;
   private final DTreeNode ds0, ds1, ds0l, ds0r, ds1l, ds1r;
   private final int s0ld, s0rd;
   private final int s1ld, s1rd;
   private final Word s0wrd, s1wrd, s2wrd, s3wrd, q0wrd, q1wrd, q2wrd, q3wrd, ds0wrd, ds1wrd;
   private final POS s0pos, s1pos, s2pos, s3pos, q0pos, q1pos, q2pos, q3pos, ds0pos, ds1pos, ds0lpos, ds0rpos, ds1lpos, ds1rpos;
   private final CCGCategory s0cat, s1cat, s2cat, s3cat;
   
   private final List<CCGTreeNode> sentNodes;
   private final HashMap<Feature, Double> featList;
      
   public Context(PStateItem state, List<CCGTreeNode> list, boolean djoint){
        PStateItem curState = state;
        sentNodes = list;
        
        int stacksize = curState.stacksize();
        s0 = stacksize<1 ? null : curState.getNode();
        s1 = stacksize<2 ? null : curState.getStackPtr().getNode();
        s2 = stacksize<3 ? null : curState.getStackPtr().getStackPtr().getNode();
        s3 = stacksize<4 ? null : curState.getStackPtr().getStackPtr().getStackPtr().getNode();
        
        
        q0 = curState.getCurrentWrd() >= sentNodes.size() ? null : list.get(curState.getCurrentWrd());
        q1 = curState.getCurrentWrd()+1 >= sentNodes.size() ? null : list.get(curState.getCurrentWrd()+1);
        q2 = curState.getCurrentWrd()+2 >= sentNodes.size() ? null : list.get(curState.getCurrentWrd()+2);
        q3 = curState.getCurrentWrd()+3 >= sentNodes.size() ? null : list.get(curState.getCurrentWrd()+3);
        
        CCGTreeNode[] s0nodes = new CCGTreeNode[4];
        CCGTreeNode[] s1nodes = new CCGTreeNode[4];
        
        String[] nvars;
        
        if(s0 != null)
            s0nodes = getNodeVariable(s0);
        
        s0l = s0nodes[0]; s0r = s0nodes[1]; s0u = s0nodes[2]; s0h = s0nodes[3];
        
        if(s1 != null)
            s1nodes = getNodeVariable(s1);
        
        s1l = s1nodes[0]; s1r = s1nodes[1]; s1u = s1nodes[2]; s1h = s1nodes[3];
        
        List<Word> wlst = Arrays.asList(new Word[8]);
        List<POS> plst = Arrays.asList(new POS[8]);
        List<CCGCategory> clst = Arrays.asList(new CCGCategory[4]);
        getStackVariables(wlst, plst, clst);        
        getInputVariables(wlst, plst);
        s0wrd = wlst.get(0); s1wrd = wlst.get(1); s2wrd = wlst.get(2); s3wrd = wlst.get(3);
        s0pos = plst.get(0); s1pos = plst.get(1); s2pos = plst.get(2); s3pos = plst.get(3);
        s0cat = clst.get(0); s1cat = clst.get(1); s2cat = clst.get(2); s3cat = clst.get(3);
        q0wrd = wlst.get(4); q1wrd = wlst.get(5); q2wrd = wlst.get(6); q3wrd = wlst.get(7);
        q0pos = plst.get(4); q1pos = plst.get(5); q2pos = plst.get(6); q3pos = plst.get(7);
        
        s0ld = s0rd = s1ld = s1rd = -1;
        featList = new HashMap<>();
        int size = calculateCapacity();
                
        if(djoint){
            int dstacksize = curState.depstacksize();
            if(dstacksize>=1){
               ds0 =  curState.getDepNode();
               ds0l = curState.getLDepNode();
               ds0r = curState.getRDepNode();
               CCGTreeNode ds0node = list.get(ds0.getId()-1);
               ds0wrd = ds0node.getHeadWrd();
               ds0pos = ds0node.getPOS();
               ds0lpos = (ds0l!=null) ? list.get(ds0l.getId()-1).getPOS() : null;
               ds0rpos = (ds0r!=null) ? list.get(ds0r.getId()-1).getPOS() : null;
            }
            else{
                ds0 = ds0l = ds0r = null;
                ds0wrd = null; ds0pos = ds0lpos = ds0rpos = null;
            }
            
            if(dstacksize>=2){
                PStateItem prev = curState.getDStackPtr();
                ds1 = prev.getDepNode();
                ds1l = prev.getLDepNode();
                ds1r = prev.getLDepNode();                
               CCGTreeNode ds1node = list.get(ds1.getId()-1);
               ds1wrd = ds1node.getHeadWrd();
               ds1pos = ds1node.getPOS();               
               ds1lpos = (ds1l!=null) ? list.get(ds1l.getId()-1).getPOS() : null;
               ds1rpos = (ds1r!=null) ? list.get(ds1r.getId()-1).getPOS() : null;
            }
            else{
                ds1 = ds1l = ds1r = null;
                ds1wrd = null; ds1pos = ds1lpos = ds1rpos = null;
            }
        }
        else{
            ds0 = ds1 = ds0l = ds0r = ds1l = ds1r = null;
            ds0wrd = ds1wrd = null;
            ds0pos = ds1pos = ds0lpos = ds0rpos =ds1lpos = ds1rpos = null;
        }
   }   
   
   private void getStackVariables(List<Word> wordlist, List<POS> poslist, List<CCGCategory> catlist){
       if(s0!=null){
           SCoNLLNode cnode = s0.getConllNode();
           wordlist.set(0, cnode.getWrd());
           poslist.set(0, cnode.getPOS());
           catlist.set(0, CCGCategory.make(s0.getCCGcat().toString()) );
       }
       
       if(s1!=null){
           SCoNLLNode cnode = s1.getConllNode();
           wordlist.set(1, cnode.getWrd());
           poslist.set(1, cnode.getPOS());
           catlist.set(1, CCGCategory.make(s1.getCCGcat().toString()) );
       }
       
       if(s2!=null){
           SCoNLLNode cnode = s2.getConllNode();
           wordlist.set(2, cnode.getWrd());
           poslist.set(2, cnode.getPOS());
           catlist.set(2, CCGCategory.make(s2.getCCGcat().toString()) );
       }
       
       if(s3!=null){
           SCoNLLNode cnode = s3.getConllNode();
           wordlist.set(3, cnode.getWrd());
           poslist.set(3, cnode.getPOS());
           catlist.set(3, CCGCategory.make(s3.getCCGcat().toString()) );
       }
   }
   
   private void getInputVariables(List<Word> wordlist, List<POS> poslist){
       if(q0!=null){
           SCoNLLNode cnode = q0.getConllNode();
           wordlist.set(4, cnode.getWrd());
           poslist.set(4, cnode.getPOS());
       }
       
       if(q1!=null){
           SCoNLLNode cnode = q1.getConllNode();
           wordlist.set(5, cnode.getWrd());
           poslist.set(5, cnode.getPOS());
       }
       
       if(q2!=null){
           SCoNLLNode cnode = q2.getConllNode();
           wordlist.set(6, cnode.getWrd());
           poslist.set(6, cnode.getPOS());
       }
       
       if(q3!=null){
           SCoNLLNode cnode = q3.getConllNode();
           wordlist.set(7, cnode.getWrd());
           poslist.set(7, cnode.getPOS());
       }
   }
   
   
   private int calculateCapacity(){
       int size = 0;
       if(s3!=null) size+=16;
       else if(s2!=null) size+=14;
       else if(s1!=null) size+=12;
       else if(s0!=null) size+=6;
       
       if(q3!=null) size+=12;
       else if(q2!=null) size+=9;
       else if(q1!=null) size+=6;
       else if(q0!=null) size+=3;
       
       return size;       
   }
   
   private CCGTreeNode[] getNodeVariable(CCGTreeNode node){
       CCGTreeNode[] nodes = new CCGTreeNode[4];
       if(node.isUnary()){
           nodes[2] = node.getLeftChild();
           nodes[3] = nodes[2];
       }
       else if(node.isBinary()){           
           nodes[0] = node.getLeftChild();
           nodes[1] = node.getRightChild();
           if(node.getHeadDir()==0)
               nodes[3] = nodes[0];
           else
               nodes[3] = nodes[1];
       }
       return nodes;
   }
    
    public HashMap<Feature, Double> getFeatureList(boolean djoint){
        // 1st set
        fillStackBase();
        
        //2nd Set
        fillInputBase();
        
        //3rd Set
        if(s0l!=null)
            fillStackChild(FeatPrefix.s0Lwc, FeatPrefix.s0Lpc, s0l.getConllNode().getNodeId(), s0l.getCCGcat());
        if(s0r!=null)
            fillStackChild(FeatPrefix.s0Rwc, FeatPrefix.s0Rpc, s0r.getConllNode().getNodeId(), s0r.getCCGcat());
        if(s0u!=null)
            fillStackChild(FeatPrefix.s0Uwc, FeatPrefix.s0Upc, s0u.getConllNode().getNodeId(), s0u.getCCGcat());
        
        if(s1l!=null)
            fillStackChild(FeatPrefix.s1Lwc, FeatPrefix.s1Lpc, s1l.getConllNode().getNodeId(), s1l.getCCGcat());
        if(s1r!=null)
            fillStackChild(FeatPrefix.s1Rwc, FeatPrefix.s1Rpc, s1r.getConllNode().getNodeId(), s1r.getCCGcat());
        if(s1u!=null)
            fillStackChild(FeatPrefix.s1Uwc, FeatPrefix.s1Upc, s1u.getConllNode().getNodeId(), s1u.getCCGcat());
        
        //4th Set
        fills0s1InputBigrams();
        
        //5th Set
        fillSimpleTrigrams();

        //6th Set
        fillTrigrams();
        
        if(djoint)
            //getDepFeatures();
            getDepFeaturesHS();
        
        //return Collections.unmodifiableList(featList);
        return featList;
    }
    
    //HuangNSagae2010
    private void getDepFeaturesHS(){

        //unigram
        if(ds0!=null){
            featList.put(Feature.make(FeatPrefix.sS0w, Arrays.asList(ds0wrd)), 1.0);
            featList.put(Feature.make(FeatPrefix.sS0t, Arrays.asList(ds0pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.sS0wt, Arrays.asList(ds0wrd, ds0pos)), 1.0);
        }
        if(ds1!=null){
            featList.put(Feature.make(FeatPrefix.sS1wt, Arrays.asList(ds1wrd, ds1pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.sS1w, Arrays.asList(ds1wrd)), 1.0);
            featList.put(Feature.make(FeatPrefix.sS1t, Arrays.asList(ds1pos)), 1.0);
        }
        if(q0!=null){
            featList.put(Feature.make(FeatPrefix.sQ0wt, Arrays.asList(q0wrd, q0pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.sQ0w, Arrays.asList(q0wrd)), 1.0);
            featList.put(Feature.make(FeatPrefix.sQ0t, Arrays.asList(q0pos)), 1.0);
        }
        
        //Brigram
        if(ds0!=null){
            if(ds1!=null){
                featList.put(Feature.make(FeatPrefix.sS0wS1w, Arrays.asList(ds0wrd, ds1wrd)), 1.0);
                featList.put(Feature.make(FeatPrefix.sS0tS1t, Arrays.asList(ds0pos, ds1pos)), 1.0);
                featList.put(Feature.make(FeatPrefix.sS0wS0tS1t, Arrays.asList(ds0wrd, ds0pos, ds1pos)), 1.0);
                featList.put(Feature.make(FeatPrefix.sS0tS1wS1t, Arrays.asList(ds0pos, ds1wrd, ds1pos)), 1.0);
                featList.put(Feature.make(FeatPrefix.sS0wS1wS1t, Arrays.asList(ds0wrd, ds1wrd, ds1pos)), 1.0);
                featList.put(Feature.make(FeatPrefix.sS0wS0tS1w, Arrays.asList(ds0wrd, ds0pos, ds1wrd)), 1.0);
                featList.put(Feature.make(FeatPrefix.sS0wS0tS1wS1t, Arrays.asList(ds0wrd, ds0pos, ds1wrd, ds1pos)), 1.0);
            }
            if(q0!=null){
                featList.put(Feature.make(FeatPrefix.sS0tQ0t, Arrays.asList(ds0wrd, q0pos)), 1.0);
                /*
                featList.put(Feature.make(FeatPrefix.sQ0wS0w, Arrays.asList(q0wrd, ds0wrd)), 1.0);
                featList.put(Feature.make(FeatPrefix.sQ0wS0t, Arrays.asList(q0wrd, ds0pos)), 1.0);
                if(ds1 != null){
                    featList.put(Feature.make(FeatPrefix.sQ0wS1w, Arrays.asList(q0wrd, ds1wrd)), 1.0);
                    featList.put(Feature.make(FeatPrefix.sQ0wS1t, Arrays.asList(q0wrd, ds1pos)), 1.0);
                }
                //*/
            }
        }
        
        //Trigram
        if(ds0!=null && q0 != null){
            if(ds1!=null){
                featList.put(Feature.make(FeatPrefix.sS1tS0tQ0t, Arrays.asList(ds1pos, ds0pos, q0pos)), 1.0);
                featList.put(Feature.make(FeatPrefix.sS1tS0wQ0t, Arrays.asList(ds1pos, ds0wrd, q0pos)), 1.0);
            }
            if(q1!=null){
                featList.put(Feature.make(FeatPrefix.sS0wQ0tQ1t, Arrays.asList(ds0wrd, q0pos, q1pos)), 1.0);
                featList.put(Feature.make(FeatPrefix.sS0tQ0tQ1t, Arrays.asList(ds0pos, q0pos, q1pos)), 1.0);
            }
        }
        
        
        //Modifier
        if(ds0!=null && ds1 != null){
            if(ds1l!=null){
                featList.put(Feature.make(FeatPrefix.sS1tS1ltS0t, Arrays.asList(ds1pos, ds1lpos, ds0pos)), 1.0);                
                featList.put(Feature.make(FeatPrefix.sS1tS1ltS0w, Arrays.asList(ds1pos, ds1lpos, ds0wrd)), 1.0);
                featList.put(Feature.make(FeatPrefix.sS1ld, Arrays.asList(ds1l.getDepLabel().toString())), 1.0);
            }
            if(ds1r!=null){
                featList.put(Feature.make(FeatPrefix.sS1tS1rtS0t, Arrays.asList(ds1pos, ds1rpos, ds0pos)), 1.0);
                featList.put(Feature.make(FeatPrefix.sS1tS1rtS0w, Arrays.asList(ds1pos, ds1rpos, ds0wrd)), 1.0);
                featList.put(Feature.make(FeatPrefix.sS1rd, Arrays.asList(ds1r.getDepLabel().toString())), 1.0);
            }
            if(ds0r!=null){
                featList.put(Feature.make(FeatPrefix.sS1tS0tS0rt, Arrays.asList(ds1pos, ds0pos, ds0rpos)), 1.0);
                featList.put(Feature.make(FeatPrefix.sS0rd, Arrays.asList(ds0r.getDepLabel().toString())), 1.0);
            }
            if(ds0l!=null){
                featList.put(Feature.make(FeatPrefix.sS1tS0wS0lt, Arrays.asList(ds1pos, ds0wrd, ds0lpos)), 1.0);
                featList.put(Feature.make(FeatPrefix.sS1tS0tS0lt, Arrays.asList(ds1pos, ds0pos, ds0lpos)), 1.0);
                featList.put(Feature.make(FeatPrefix.sS0ld, Arrays.asList(ds0l.getDepLabel().toString())), 1.0);
            }
        }
    }
    
    private void getDepFeatures(){
        
        //from single words
        if(ds1!=null){
            featList.put(Feature.make(FeatPrefix.dS0wp, Arrays.asList(ds1wrd, ds1pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.dS0w, Arrays.asList(ds1wrd)), 1.0);
            featList.put(Feature.make(FeatPrefix.dS0p, Arrays.asList(ds1pos)), 1.0);
        }
        if(ds0!=null){
            featList.put(Feature.make(FeatPrefix.dN0wp, Arrays.asList(ds0wrd, ds0pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.dN0w, Arrays.asList(ds0wrd)), 1.0);
            featList.put(Feature.make(FeatPrefix.dN0p, Arrays.asList(ds0pos)), 1.0);
        }
        if(q0!=null){
            featList.put(Feature.make(FeatPrefix.dN1wp, Arrays.asList(q0wrd, q0pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.dN1w, Arrays.asList(q0wrd)), 1.0);
            featList.put(Feature.make(FeatPrefix.dN1p, Arrays.asList(q0pos)), 1.0);
        }
        if(q1!=null){
            featList.put(Feature.make(FeatPrefix.dN2wp, Arrays.asList(q1wrd, q1pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.dN2w, Arrays.asList(q1wrd)), 1.0);
            featList.put(Feature.make(FeatPrefix.dN2p, Arrays.asList(q1pos)), 1.0);
        }
        
        //from word pairs
        if(ds0!=null){
            if(ds1!=null){
                featList.put(Feature.make(FeatPrefix.dS0wpN0wp, Arrays.asList(ds1wrd, ds1pos, ds0wrd, ds0pos)), 1.0);
                featList.put(Feature.make(FeatPrefix.dS0wpN0w, Arrays.asList(ds1wrd, ds1pos, ds0wrd)), 1.0);
                featList.put(Feature.make(FeatPrefix.dS0wN0wp, Arrays.asList(ds1wrd, ds0wrd, ds0pos)), 1.0);
                featList.put(Feature.make(FeatPrefix.dS0wpN0p, Arrays.asList(ds1wrd, ds1pos, ds0pos)), 1.0);
                featList.put(Feature.make(FeatPrefix.dS0pN0wp, Arrays.asList(ds1pos, ds0wrd, ds0pos)), 1.0);
                featList.put(Feature.make(FeatPrefix.dS0wN0w, Arrays.asList(ds1wrd, ds0wrd)), 1.0);
                featList.put(Feature.make(FeatPrefix.dS0pN0p, Arrays.asList(ds1pos, ds0pos)), 1.0);
            }
            if(q0!=null)
                featList.put(Feature.make(FeatPrefix.dN0pN1p, Arrays.asList(ds0pos, q0pos)), 1.0);
        }
        
        //from three words
        if(ds0!=null && q0!=null && q1!= null)
            featList.put(Feature.make(FeatPrefix.dN0pN1pN2p, Arrays.asList(ds0pos, q0pos, q1pos)), 1.0);
        if(ds1!=null && ds0!=null){
            if(q0!=null)
                featList.put(Feature.make(FeatPrefix.dS0pN0pN1p, Arrays.asList(ds1pos, ds0pos, q0pos)), 1.0);
            if(ds1l!=null)
                featList.put(Feature.make(FeatPrefix.dS0pS0lpN0p, Arrays.asList(ds1pos, ds0pos, ds1lpos)), 1.0);
            if(ds1r!=null)
                featList.put(Feature.make(FeatPrefix.dS0pS0rpN0p, Arrays.asList(ds1pos, ds0pos, ds1rpos)), 1.0);
            if(ds0l!=null)
                featList.put(Feature.make(FeatPrefix.dS0pN0pN0lp, Arrays.asList(ds1pos, ds0pos, ds0lpos)), 1.0);
        }
    }
    
    private void fillTrigrams(){
        if(s0!=null){
            if(s0l!=null){
                CCGCategory s0lcat = CCGCategory.make(s0l.getCCGcat().toString());
                if(s0h!=null){
                    CCGCategory s0hcat = CCGCategory.make(s0h.getCCGcat().toString());
                    featList.put(Feature.make(FeatPrefix.s0cs0hcs0lc, Arrays.asList(s0cat, s0hcat, s0lcat)), 1.0);
                }
                if(s1!=null){
                    featList.put(Feature.make(FeatPrefix.s0cs0lcs1c, Arrays.asList(s0cat, s0lcat, s1cat)), 1.0);
                    featList.put(Feature.make(FeatPrefix.s0cs0lcs1w, Arrays.asList(s0cat, s0lcat, s1wrd)), 1.0);
                }
            }
            if(s0r!=null){
                CCGCategory s0rcat = CCGCategory.make(s0r.getCCGcat().toString());
                if(s0h!=null){
                    CCGCategory s0hcat = CCGCategory.make(s0h.getCCGcat().toString());
                    featList.put(Feature.make(FeatPrefix.s0cs0hcs0rc, Arrays.asList(s0cat, s0rcat, s0hcat)), 1.0);
                }
                if(q0!=null){
                    featList.put(Feature.make(FeatPrefix.s0cs0rcq0p, Arrays.asList(s0cat, s0rcat, q0pos)), 1.0);
                    featList.put(Feature.make(FeatPrefix.s0cs0rcq0w, Arrays.asList(s0cat, s0rcat, q0wrd)), 1.0);
                }
            }            
        }
        if(s1r!=null){
            CCGCategory s1rcat = CCGCategory.make(s1r.getCCGcat().toString());
            CCGCategory s1hcat = CCGCategory.make(s1h.getCCGcat().toString());
            featList.put(Feature.make(FeatPrefix.s0cs1cs1rc, Arrays.asList(s0cat, s1cat, s1rcat)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0ws1cs1rc, Arrays.asList(s0wrd, s1cat, s1rcat)), 1.0);
            featList.put(Feature.make(FeatPrefix.s1cs1hcs1rc, Arrays.asList(s1cat, s1hcat, s1rcat)), 1.0);
        }
    }
    
    private void fillSimpleTrigrams(){
        if(s0!=null && s1!=null && s2!=null){
            featList.put(Feature.make(FeatPrefix.s0wcs1cs2c, Arrays.asList(s0wrd, s0cat, s1cat, s2cat)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0cs1wcs2c, Arrays.asList(s0cat, s1wrd, s1cat, s2cat)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0cs1cs2wc, Arrays.asList(s0cat, s1cat, s2wrd, s2cat)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0cs1cs2c, Arrays.asList(s0cat, s1cat, s2cat)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0ps1ps2p, Arrays.asList(s0pos, s1pos, s2pos)), 1.0);
        }
        if(s0!=null && s1!=null && q0!=null){
            featList.put(Feature.make(FeatPrefix.s0wcs1cq0p, Arrays.asList(s0wrd, s0cat, s1cat, q0pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0cs1wcq0p, Arrays.asList(s0cat, s1wrd, s1cat, q0pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0cs1cq0wp, Arrays.asList(s0cat, s1cat, q0wrd, q0pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0cs1cq0p, Arrays.asList(s0cat, s1cat, q0pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0ps1pq0p, Arrays.asList(s0pos, s1pos, q0pos)), 1.0);
        }
        if(s0!=null && q0!=null && q1!=null){
            featList.put(Feature.make(FeatPrefix.s0wcq0pq1p, Arrays.asList(s0wrd, s0cat, q0pos, q1pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0cq0wpq1p, Arrays.asList(s0cat, q0wrd, q0pos, q1pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0cq0pq1wp, Arrays.asList(s0cat, q0pos, q1wrd, q1pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0cq0pq1p, Arrays.asList(s0cat, q0pos, q1pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0pq0pq1p, Arrays.asList(s0pos, q0pos, q1pos)), 1.0);
        }
    }
    
    private void fills0s1InputBigrams(){
        
        if(s0!=null && s1!=null){
            //featList.put(Feature.make(FeatPrefix.s0wcs1wc, Arrays.asList(s0wrd, s0cat, s1wrd, s1cat)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0cs1w, Arrays.asList(s0cat, s1wrd)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0ws1c, Arrays.asList(s0wrd, s1cat)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0cs1c, Arrays.asList(s0cat, s1cat)), 1.0);
        }
        
        if(s0!=null && q0!=null){
            //featList.put(Feature.make(FeatPrefix.s0wcq0wp, Arrays.asList(s0wrd, s0cat, q0wrd, q0pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0cq0wp, Arrays.asList(s0cat, q0wrd, q0pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0wcq0p, Arrays.asList(s0wrd, s0cat, q0pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0cq0p, Arrays.asList(s0cat, q0pos)), 1.0);
        }
        
        if(s1!=null && q0!=null){
            //featList.put(Feature.make(FeatPrefix.s1wcq0wp, Arrays.asList(s1wrd, s1cat, q0wrd, q0pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.s1cq0wp, Arrays.asList(q0wrd, q0pos, s1cat)), 1.0);
            featList.put(Feature.make(FeatPrefix.s1wcq0p, Arrays.asList(s1wrd, q0pos, s1cat)), 1.0);
            featList.put(Feature.make(FeatPrefix.s1cq0p, Arrays.asList(q0pos, s1cat)), 1.0);
        }        
    }
        
    private void fillStackBase(){
        
        if(s0!=null){
            //featList.put(Feature.make(FeatPrefix.s0w, Arrays.asList(s0wrd)), 1.0);
            //featList.put(Feature.make(FeatPrefix.s0p, Arrays.asList(s0pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0wp, Arrays.asList(s0wrd, s0pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0c, Arrays.asList(s0cat)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0pc, Arrays.asList(s0pos, s0cat)), 1.0);
            featList.put(Feature.make(FeatPrefix.s0wc, Arrays.asList(s0wrd, s0cat)), 1.0);
            //featList.put(new Feature("s0wp-"+s0wrd, s0pos), 1.0); featList.put(new Feature("s0c-"+s0cat), 1.0); 
            //featList.put(new Feature("s0w-"+s0wrd), 1.0); featList.put(new Feature("s0p-"+s0pos), 1.0); 
            //featList.put(new Feature("s0pc-"+s0pos, s0cat), 1.0); featList.put(new Feature("s0wc-"+s0wrd, s0cat), 1.0);
        }
        if(s1!=null){
            featList.put(Feature.make(FeatPrefix.s1wp, Arrays.asList(s1wrd, s1pos)), 1.0);
            featList.put(Feature.make(FeatPrefix.s1c, Arrays.asList(s1cat)), 1.0);
            featList.put(Feature.make(FeatPrefix.s1pc, Arrays.asList(s1pos, s1cat)), 1.0);
            featList.put(Feature.make(FeatPrefix.s1wc, Arrays.asList(s1wrd, s1cat)), 1.0);
        }
        if(s2!=null){            
            featList.put(Feature.make(FeatPrefix.s2pc, Arrays.asList(s2pos, s2cat)), 1.0);
            featList.put(Feature.make(FeatPrefix.s2wc, Arrays.asList(s2wrd, s2cat)), 1.0);
        }        
        if(s3!=null){            
            featList.put(Feature.make(FeatPrefix.s3pc, Arrays.asList(s3pos, s3cat)), 1.0);
            featList.put(Feature.make(FeatPrefix.s3wc, Arrays.asList(s3wrd, s3cat)), 1.0);
        }
    }
    
    private void fillInputBase(){
        
        if(q0!=null){
            featList.put(Feature.make(FeatPrefix.q0wp, Arrays.asList(q0wrd, q0pos)), 1.0);
        }
        if(q1!=null){
            featList.put(Feature.make(FeatPrefix.q1wp, Arrays.asList(q1wrd, q1pos)), 1.0);
        }
        if(q2!=null){
            featList.put(Feature.make(FeatPrefix.q2wp, Arrays.asList(q2wrd, q2pos)), 1.0);
        }
        if(q3!=null){
            featList.put(Feature.make(FeatPrefix.q3wp, Arrays.asList(q3wrd, q3pos)), 1.0);
        }        
    }
    
    private void fillStackChild(FeatPrefix pre1, FeatPrefix pre2, int id, CCGCategory ccgCat){
        
        SCoNLLNode cnode = sentNodes.get(id-1).getConllNode();
        Word wrd = cnode.getWrd();
        POS pos = cnode.getPOS();
        CCGCategory cat = CCGCategory.make(ccgCat.toString());
        
        featList.put(Feature.make(pre1, Arrays.asList(wrd, cat)), 1.0);
        featList.put(Feature.make(pre2, Arrays.asList(pos, cat)), 1.0);
    }
}

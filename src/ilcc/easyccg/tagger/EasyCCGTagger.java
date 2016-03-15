package ilcc.easyccg.tagger;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Map;
import ilcc.easyccg.tagger.SyntaxTreeNode.SyntaxTreeNodeFactory;
import uk.co.flamingpenguin.jewel.cli.ArgumentValidationException;
import uk.co.flamingpenguin.jewel.cli.CliFactory;
import uk.co.flamingpenguin.jewel.cli.Option;
import ilcc.ccgparser.utils.DataTypes.ScoredLabel;
import ilcc.easyccg.tagger.InputReader.InputWord;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;

public class EasyCCGTagger
{
  
    private Tagger tagger;
    private double corr, ncorr, total;
    
    public EasyCCGTagger(File model, int maxLen, double beam, boolean nolookahead){
        tagger = new Tagger(model, maxLen, beam, nolookahead);
        corr = ncorr = total = 0.0;
    }
    
  /**
   * Command Line Interface
   */
  public interface CommandLineArguments  
  {
    @Option(shortName="m", description = "Path to the parser model")
    File getModel();    

    @Option(shortName="f", defaultValue="", description = "(Optional) Path to the input text file. Otherwise, the parser will read from stdin.")
    File getInputFile();
    
    @Option(shortName="o", defaultValue="", description = "(Optional) Path to the output file.")
    File getOutputFile();

    @Option(shortName="i", defaultValue="tokenized", description = "(Optional) Input Format: one of \"tokenized\", \"POStagged\", \"POSandNERtagged\", \"gold\" or \"supertagged\"")
    String getInputFormat();
    
    @Option(shortName="l", defaultValue="70", description = "(Optional) Maximum length of sentences in words. Defaults to 70.")
    int getMaxLength();

    @Option(shortName="n", defaultValue="1", description = "(Optional) Number of parses to return per sentence. Defaults to 1.")
    int getNbest();    

    @Option(shortName="r", defaultValue={"S[dcl]", "S[wq]", "S[q]", "S[qem]", "NP"}, description = "(Optional) List of valid categories for the root node of the parse. Defaults to: S[dcl] S[wq] S[q] NP")
    List<String> getRootCategories();

    @Option(shortName="s", description = "(Optional) Allow rules not involving category combinations seen in CCGBank. Slows things down by around 20%.")
    boolean getUnrestrictedRules();

    @Option(shortName="b", defaultValue="0.0001", description = "(Optional) Prunes lexical categories whose probability is less than this ratio of the best category. Defaults to 0.0001.")
    double getSupertaggerbeam();
    
    @Option(defaultValue="0.0", description = "(Optional) If using N-best parsing, filter parses whose probability is lower than this fraction of the probability of the best parse. Defaults to 0.0")
    double getNbestbeam();

    @Option(helpRequest = true, description = "Display this message", shortName = "h")
    boolean getHelp();
    
    @Option(description = "(Optional) Make a tag dictionary")
    boolean getMakeTagDict();
        
    @Option(shortName="e", description = "Evaluate the output")
    boolean getEval();
    
    @Option(description = "Use lookahead")
    boolean getNolookahead();

  }
  
  public enum InputFormat {
      TOKENIZED, GOLD, SUPERTAGGED, POSTAGGED, POSANDNERTAGGED
  }
  
  public static List<List<InputWord>> readInputFile(File input) throws IOException, Exception {
        
      List<List<InputWord>> fwordlist = new ArrayList<>();
        BufferedReader iReader = new BufferedReader(new FileReader(input));
        String line = iReader.readLine();
        if(line.startsWith("#")){
            line = iReader.readLine(); 
            line = iReader.readLine();
            line = iReader.readLine();
        }
        List<InputWord> swordlist;
        while ( line != null) {
            String[] words = line.trim().split(" ");
            swordlist = new ArrayList<>(words.length);
            for(String part : words){
                String[] wrd = part.split("\\|");      
                String pos = wrd.length > 1 ? wrd[wrd.length-1] : null;
                swordlist.add(new InputWord(wrd[0], pos, null));
            }
            fwordlist.add(swordlist);
            line = iReader.readLine();
        }
        return fwordlist;
    }

  private String printSuperTags(List<List<ScoredLabel>> result, List<InputWord> words, BufferedWriter oWriter){
      DecimalFormat df = new DecimalFormat(".000000");
      StringBuilder out = new StringBuilder();
      for(int id = 0 ; id<words.size(); id++){
          InputWord wrd = words.get(id);
          List<ScoredLabel> tagList = result.get(id);
          out.append(wrd.word); out.append("\t");
          out.append(wrd.pos); out.append("\t");
          out.append(tagList.size()); 
          for(ScoredLabel node : tagList){
              out.append("\t" + node.getLabel() + "\t");
              out.append(df.format(node.getScore()));
          }
          out.append("\n");
      }
      return out.toString();
  }
  
  private void eval(List<List<ScoredLabel>> result, List<InputWord> words){
      for(int id = 0 ; id<words.size(); id++){
          InputWord wrd = words.get(id);
          List<ScoredLabel> tagList = result.get(id);
          String olabel = tagList.get(0).getLabel().toString();
          if(wrd.pos.equals(olabel))
              corr++;
          boolean found = false;
          for(ScoredLabel node : tagList){
              if(wrd.pos.equals(node.getLabel().toString())){
                  found = true;
                  break;
              }
          }
          if(found)
              ncorr++;
          total++;
      }
  }
  
  public void tagFile(File ifile, File ofile, boolean eval) throws Exception{
      BufferedWriter out = new BufferedWriter(new FileWriter(ofile));
      List<List<InputWord>> fwordslist = readInputFile(ifile);
      for(List<InputWord> swrds : fwordslist){
          List<List<ScoredLabel>> result = tagger.tag(swrds);
          out.write(printSuperTags(result, swrds, out)+"\n");
          if(eval)
            eval(result, swrds);
      }
      if(eval){
          System.err.println("Accuracy is: "+100.00*corr/total);
          System.err.println("N-best Accuracy is: "+100.00*ncorr/total);
      }
      out.close();
  }
  
  public static void main(String[] args) throws IOException, ArgumentValidationException, Exception {    
      
    CommandLineArguments parsedArgs = CliFactory.parseArguments(CommandLineArguments.class, args);
    InputFormat input = InputFormat.valueOf(parsedArgs.getInputFormat().toUpperCase());
    
    if (parsedArgs.getMakeTagDict()) {
      InputReader reader = InputReader.make(input, new SyntaxTreeNodeFactory(parsedArgs.getMaxLength(), 0));
      Map<String, Collection<Category>> tagDict = TagDict.makeDict(reader.readFile(parsedArgs.getInputFile()));
      TagDict.writeTagDict(tagDict, parsedArgs.getModel());
      System.exit(0);
    }
    
    if (!parsedArgs.getModel().exists()) throw new InputMismatchException("Couldn't load model from from: " + parsedArgs.getModel());    
    
    boolean eval = parsedArgs.getEval();
    System.err.println("Loading model...");
    
    EasyCCGTagger etagger = new EasyCCGTagger(parsedArgs.getModel(), parsedArgs.getMaxLength(), parsedArgs.getSupertaggerbeam(), parsedArgs.getNolookahead());
    etagger.tagFile(parsedArgs.getInputFile(), parsedArgs.getOutputFile(), eval);
  }
}

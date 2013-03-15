package chatbot;
import com.aliasi.cluster.LatentDirichletAllocation;
import com.aliasi.symbol.MapSymbolTable;
import com.aliasi.symbol.SymbolTable;
import com.aliasi.tokenizer.EnglishStopTokenizerFactory;
import com.aliasi.tokenizer.LowerCaseTokenizerFactory;
import com.aliasi.tokenizer.ModifyTokenTokenizerFactory;
import com.aliasi.tokenizer.RegExTokenizerFactory;
import com.aliasi.tokenizer.StopTokenizerFactory;
import com.aliasi.tokenizer.TokenizerFactory;

import com.google.appengine.api.xmpp.JID;
import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.MessageBuilder;
import com.google.appengine.api.xmpp.MessageType;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;

import de.l3s.boilerpipe.extractors.ArticleExtractor;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.Set;



import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Poor amateur coding style followed
 */
/**POS Tagger**/
class tagger{
	MaxentTagger mtagger;
	public tagger(){
		try{
		 mtagger= new MaxentTagger("wsj-0-18-left3words.tagger");
		}
		catch(Exception e){
			
		}
	}
	/**Answer should contain a verb and no html tags **/
	public boolean isValid(String tagst){
		tagst=tagst.toLowerCase();
		if(!tagst.contains("|")&&tagst.contains("_v")&&!tagst.contains("url")&&!tagst.contains("html")&&!tagst.contains("php")&&!tagst.contains("?")&&!tagst.contains("following")&&!tagst.contains("listed")&&!tagst.contains("steps")&&!tagst.contains("here")&&!tagst.contains("list")&&!tagst.contains("rss")&&!tagst.contains("sitemap")&&!tagst.contains("click"))
			
			return true;
		return false;
	}
	public int evaluate(String st,String query){
		
		query=query.toLowerCase();
		String tagst=mtagger.tagString(st);
		tagst=" "+tagst+" ";
		/**tried some rules for question words like who should always be answered with proper noun.. 
		 * Not Formalised though
		 */
		if(query.startsWith("who")){
			if(tagst.contains("_NNP"))
				if(isValid(tagst))
				return 1;
		}
		else if(query.startsWith("where")){
	    
	    	if(st.contains(" in ")||st.contains(" on ")||(st.contains(" at "))||st.contains(" near ")||st.contains(" from ")||st.contains(" to "))
	    		if(isValid(tagst))
	    		return 1;
	    }
	    else if(query.startsWith("when"))
	    {
	    	if(st.contains(" in ")||st.contains(" on ")||(st.contains(" at "))||st.contains(" before ")||(st.contains(" after ")))
	    		if(isValid(tagst))
	    		return 1;
	    }
		
	    else if(isValid(tagst)){
		
			return 1;
		}
		
			
		return 0;
	}
}
/**Handles Google Xmpp Service 
 * Refer App Engine guidelines to make it running
 * @author kalyan
 *
 */
public class XmppReceiverServlet extends HttpServlet {
	private static tagger t=new tagger();
	
  private static final XMPPService xmppService =
      XMPPServiceFactory.getXMPPService();

  public void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    Message message = xmppService.parseMessage(req);

  handle(message);
  }
  
  /**
   * Handles /tellme requests, asking the Guru a question.
   */
  String perans="";
  int isGreeting(String st){
	  /**Since people start with Greeting an expect a proper response
	   * Added some rules
	   */
	  if(st.endsWith("?")||st.endsWith("!")||st.endsWith("."))
	        st=st.substring(0,st.length()-1);
	  String st1=st+" ";
	  if(st1.startsWith("hi ")||st1.startsWith("hey ")||st1.startsWith("how are you ")||st1.startsWith("hello "))
		  return 1;
	  if(st1.contains("your name")){
		  perans="chitti";
		  return 2;
	  }
      if(st1.contains("your age")||st1.contains("how old are you")){
    	  perans="just born";
		  return 2;
      }
	  return 0;
  }
  private void handle(Message message) {
    try{
      // Asking a question
	  String st=message.getBody().toLowerCase();
	  CharSequence []c=readDoc(st);
	  int flag=isGreeting(st);
	  if(flag==1)
		replyToMessage(message,"Hey, How do you do? \"I am too good\", my master said. ");
	  else if(flag==2)
		  replyToMessage(message,perans);
	  else  {
		  /** for non standard ones.. search Google**/
       String replymsg=cluster(c,st);  
       replyToMessage(message,replymsg);
	  }
      }
    catch(Exception e){
    	String replymsg="Something went terribly wrong.. we are fixing it :( ";  
        
   	 replyToMessage(message,replymsg);
    }
  }

  // ...

  private void replyToMessage(Message message, String body) {
    Message reply = new MessageBuilder()
        .withRecipientJids(message.getFromJid())
        .withMessageType(MessageType.NORMAL)
        .withBody(body)
        .build();

    xmppService.sendMessage(reply);
  }
  
  /**searches the query in google
   * Extracts plain text from each url 
   * CLusters them(LDA Clustering Refer-Some LDA Based Summarization Paper)
   * Picks the sentence least divergent from the query as answer
   */
  public String cluster(CharSequence art[],String query){
	  SymbolTable sym=new MapSymbolTable();
	  int [][]docTokens=LatentDirichletAllocation.tokenizeDocuments(art,WORMBASE_TOKENIZER_FACTORY,sym,1);
	  unnecessaryReportingHandler handler= new unnecessaryReportingHandler(sym);
		short numTopics=6;
		double alpha=0.1;
		double beta=0.01;
		int numIterations=100;
		Random randomseed=new Random(35L);
		LatentDirichletAllocation.GibbsSample sample = LatentDirichletAllocation.gibbsSampler(docTokens, 
				          numTopics, alpha,  beta, 0, 1, numIterations, randomseed, handler);
		
		int docTopic=findmaxTopic(sample,query,sym);
		
		String ans=extractMax(art,sample,docTopic,sym,query);
		if(!ans.equals("-1"))
			return ans;
	  return null;
  }
  
  String extractMax(CharSequence c[],LatentDirichletAllocation.GibbsSample sample,int docTopic,SymbolTable sym,String query)
  {
	  ArrayList<String>sentences=new ArrayList<String>();
	  for(int i=0;i<c.length;i++)
	  {
		  String sentence=c[i].toString();
		  BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
	    	 iterator.setText(sentence);
	    	 int start = iterator.first();
	    	 for (int end = iterator.next();end != BreakIterator.DONE;start = end, end = iterator.next()) {
	    	   sentences.add(sentence.substring(start,end));
	    	 }
	  }
	  double maxscore=0.0;
	  int sentenceind=-1;
	  for(int i=0;i<sentences.size();i++){
		  String st=sentences.get(i).toLowerCase();
		  double score=0.0;
		  int flag=t.evaluate(st,query);
		  String sp[]=st.split(" ");
		  
		  if(flag==0||st.contains(query))
			  continue;
		  int cnt=0;
		  for(int j=0;j<sp.length;j++){
			  
			  if(sym.symbolToID(sp[j])!=-1){
				  cnt++;
				  score+=sample.topicWordProb(docTopic, sym.symbolToID(sp[j]));
			  }
		  }
	  score/=cnt;
	  if(score>maxscore)
	  {
		  maxscore=score;
		  sentenceind=i;
	  }
	  }
	  if((sentenceind)!=-1)
	      return sentences.get(sentenceind);
	  else
		  return "-1";
  }
  
  
  int findmaxTopic(	LatentDirichletAllocation.GibbsSample sample, String query,SymbolTable sym){
	  
	  String sp[]=query.split(" ");
	  double array[]=new double[sample.numTopics()];
	  
	  for(int i=0;i<sp.length;i++){
		  if(sym.symbolToID(sp[i])!=-1){
			  
			  for(int j=0;j<sample.numTopics();j++){
				 
				  
			    array[j]+=sample.topicWordProb(j,sym.symbolToID(sp[i]));
			  }
		  }
	  }
	  int max=0;
	  double maxval=array[0];
	  for(int i=1;i<array.length;i++)
	  {
		  if(maxval<array[i])
		  {
			  maxval=array[i];
			  max=i;
		  }
	  }
     return max;
  }
  
  
  
  public CharSequence[] readDoc(String msg){
	  try{
		
		    String query=msg.replace(" ", "%20");
			URL url=new URL("http://ajax.googleapis.com/ajax/services/search/web?v=1.0&q="+query);
		    BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
		    ArrayList <String> list1=new ArrayList <String>();
		        String con="";
		        String inputLine;
		        while ((inputLine = in.readLine()) != null)
		            con+=(inputLine);
		        in.close();
		       
		        int startind=con.indexOf("\"url\":\"");
		       
		       while(startind!=-1){
		        con=con.substring(startind+7);
		        int endind=con.indexOf("\"");
		        String urlst=con.substring(0,endind);
		        if(!urlst.contains("youtube")&&!urlst.contains("quora")&&!urlst.contains("facebook")){
		        URL url1=new URL(urlst);
		        String con1=ArticleExtractor.INSTANCE.getText(url1);
		        list1.add(con1);
		        }
		       startind=con.indexOf("\"url\":\"");
		}
		       CharSequence []   c=list1.toArray(new CharSequence[list1.size()]);
		      
		    return c;
			}
			catch(Exception e){
				return null;
			}
			
		       
		}
		  static final TokenizerFactory wormbaseTokenizerFactory() {
		        TokenizerFactory factory = BASE_TOKENIZER_FACTORY;
		        factory = (TokenizerFactory) new NonAlphaStopTokenizerFactory(factory);
		        factory = new LowerCaseTokenizerFactory(factory);
		       
		        return factory;
		    }


	

		    static final TokenizerFactory BASE_TOKENIZER_FACTORY
		        = new RegExTokenizerFactory("[\\x2Da-zA-Z0-9]+"); // letter or digit or hyphen (\x2D)


	

		    static final TokenizerFactory WORMBASE_TOKENIZER_FACTORY
		        = wormbaseTokenizerFactory();


		    // removes tokens that have no letters
		    static class NonAlphaStopTokenizerFactory extends ModifyTokenTokenizerFactory {
		        static final long serialVersionUID = -3401639068551227864L;
		        public NonAlphaStopTokenizerFactory(TokenizerFactory factory) {
		            super(factory);
		        }
		        public String modifyToken(String token) {
		            return stop(token) ? null : token;
		        }
		        public boolean stop(String token) {
		            if (token.length() < 2) return true;
		            for (int i = 0; i < token.length(); ++i)
		                if (Character.isLetter(token.charAt(i)))
		                    return false;
		            return true;
		        }
		    }

		  
}
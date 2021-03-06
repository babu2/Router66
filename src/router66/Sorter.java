package router66;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.util.Iterator;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jpcap.packet.Packet;
import jpcap.packet.TCPPacket;
import jpcap.packet.UDPPacket;

public class Sorter{
	final static Pattern googleSearchPattern = Pattern.compile("\\&q\\=(.*?)(\\s|\\#|\\&)");
	final static Pattern urlStrip = Pattern.compile("GET\\s(.*?)\\s");
	final static Pattern amazonSearchPattern = Pattern.compile("field-keywords\\=(.*?)\\&");
	final static Pattern amazonProductPattern = Pattern.compile("\\/(.*?)\\/dp");
	//final static Pattern mdnsNamePattern = Pattern.compile("\\\0+(.*?)"); 
	//final static Pattern mdnsNamePattern = Pattern.compile(".*?([A-Za-z0-9]+?)[\\\t*?|\\\0*?]");
	static Vector<String> blackUrlList = new Vector<String>();
	static {
		blackUrlList.add("ytimg");
		blackUrlList.add("twimg");
		blackUrlList.add("gstatic");
//		blackUrlList.add("doubleclick");
		blackUrlList.add("wikimedia");
		blackUrlList.add("chartbeat");
		blackUrlList.add("cloudfront");
		blackUrlList.add("turn");
		blackUrlList.add("adsfac");
		blackUrlList.add("2mdn");
	}
	
	private MsgWriter msgWriter;
	private PacketReceiverImpl pri;
	
	public Sorter(MsgWriter msgWriter){
		this.msgWriter = msgWriter;
	}
	public void sortPacket(Packet packet){
		if(packet instanceof TCPPacket ){
			TCPPacket thePacket = ((TCPPacket)packet);
			String host = extractHost(thePacket);
			String client = HostDict.getHost(((TCPPacket) packet).src_ip.getHostAddress());
			switch (((TCPPacket)packet).dst_port) {
				/**
				 * 	http package
				 */
				case 80:
					if(!validateIPAddress(host)){
					/**
					 * Google Search
					 */
					 if(host.indexOf("google")!=-1){
							String googleReturn=getGoogleSearchString(thePacket);
							String gUrl = extractURL(thePacket);
							try {
								msgWriter.wSearchGoogle(new SortMsg(translateLocalHost(((TCPPacket) packet).src_ip.getHostAddress()), "", URLDecoder.decode(googleReturn.replace("+", " "),"UTF-8"),gUrl));
							} catch (UnsupportedEncodingException e) {
								e.printStackTrace();
							}
						}						
						/**
						 * dropbox Web
						 */
						else if(host.indexOf("dropbox")!=-1){
							msgWriter.wDropboxWeb(new SortMsg(client, "",thePacket.toString()));
						}
						/**
						 *  Youtube Web
						 */
						else if(host.indexOf("youtube")!=-1){
							String yUrl = extractURL(thePacket);
							
							if(yUrl.indexOf("watch?v")!=-1){
								msgWriter.wYoutubeWatch(new SortMsg(client, "", yUrl));
							}
							
						}	
					 	/**
						 * Advertising
						 */
						else if(host.indexOf("doubleclick")!=-1){
							msgWriter.wAdvertising(new SortMsg(client, ""));
						}
					 	/**
						 * Wikipedia
						 */
						else if(host.indexOf("wikipedia")!=-1){
							try {
								msgWriter.wWikipedia(new SortMsg(client, "", URLDecoder.decode(extractWikipediaPage(thePacket).replace("_", " "),"UTF-8")));
							} catch (UnsupportedEncodingException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					 /**
						 * Facebook
						 */
						else if(host.indexOf("facebook")!=-1){
								msgWriter.wFacebook(new SortMsg(client, ""));							
						}
					 /**
						 * Amazon
						 */
						else if(host.indexOf("amazon")!=-1){
							
							if(extractURL(thePacket).indexOf("field-keywords")!=-1){
								String searchString = getAmazonSearchString(thePacket);
								if(searchString!=null){
									msgWriter.wAmazon(new SortMsg(client, "","0",searchString));	
								}
							}else if(extractURL(thePacket).indexOf("/dp/")!=-1){
								String productString = getAmazonProductString(thePacket);
								if(productString!=null){
									if(productString.indexOf("amazon")!=-1){
										productString = productString.replaceAll("\\/(.*?)\\/", "");
									}
									try {
										productString = URLDecoder.decode(productString,"UTF-8");
									} catch (UnsupportedEncodingException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
									msgWriter.wAmazon(new SortMsg(client, "", "1", productString));
								}
							}else {
								msgWriter.wAmazon(new SortMsg(client, "", "2"));
							}
															
						}
						/**
						 * Standard Website
						 */
						else{
							/**
							 * Check if Website is Blacklisted 
							 */
							Boolean blackListed = false;
							Iterator<String> itr = blackUrlList.iterator();
							while(itr.hasNext()){
							 	if(host.indexOf(itr.next())!=-1){
							 		blackListed = true;
							 	}
							}
							if(!blackListed){
								msgWriter.wWebDomain(new SortMsg(client, host));
							}
						}
					}
				/**
				 * SSL Port
				 */
				case 443:
					/**
					 * 	Encrypted Stuff
					 */
					String sslHost = thePacket.dst_ip.getHostName();
					if(!validateIPAddress(sslHost)){				// host is no IP Adress
						if(sslHost.indexOf("1e100")!=-1){
							//System.out.println("google ssl");
						}
						/**
						 * Evernote SSL
						 */
						else if(sslHost.indexOf("evernote")!=-1){
							msgWriter.wEvernote(new SortMsg(client,""));
						}
						/**
						 * Facebook SSL
						 */
						else if(sslHost.indexOf("facebook")!=-1){
								msgWriter.wFacebook(new SortMsg(client, ""));							
						}
						/**
						 * Standard SSL
						 */
						else{
							msgWriter.wSSLDomain(new SortMsg(client,host));
						}
					}
					//System.out.println(dst.indexOf("facebook"));
					break;
				case 1515:
					System.out.println("PORT 1515: "+thePacket.toString());
					break;
				// CUPS
				case 631:
					System.out.println("CUPS: "+packet.toString());
					break;
				// IMAP SSL:
				case 993:
					msgWriter.wIMAP(new SortMsg(client, thePacket.dst_ip.getCanonicalHostName()));
					break;
				default:
					break;
				}
				}
			if(packet instanceof UDPPacket){
				UDPPacket udpPacket = (UDPPacket)packet;
			if(udpPacket.protocol==17){
				//System.out.println("UPD: "+packet);
				switch (udpPacket.dst_port) {
				// DROPBOX
				case 17500:
					msgWriter.wDropboxLan(new SortMsg(HostDict.getHost(((TCPPacket) packet).src_ip.getHostAddress()), ""));
					break;
				// BROWSER
				case 138:
					System.out.println(pri.translateNetbios(packet));
					//System.out.println("BROWSER: "+ ((UDPPacket)packet).);
					break;
				// MDNS aka Bonjour
//				case 5353:
//					String header = convertData(udpPacket);
//					Matcher m = mdnsNamePattern.matcher(header);
//					String mdnsName=null;
//					while (m.find()) {
//						mdnsName = m.group(1);
//					    // s now contains "BAR"
//					}
//					System.out.println("MDNS: Aus "+convertData(udpPacket)+" wird "+mdnsName);
//					break;
				default:
					break;
				}
				// 
			}
		//	System.out.println(convertHeader(packet));
			//System.out.println(packet.);
		}else{
			if(packet.toString().substring(0, 11)=="ARP REQUEST"){
				System.out.println("ARP REQUEST: "+packet.toString());
			}else if(packet.toString().substring(0, 9)=="ARP REPLY"){
				System.out.println("ARP REPLY: "+packet.toString());
			}
		}
	
				  
	
	}
	
	
	public final static String getHostName(InetAddress ip){

	        // Get the host name
	        return ip.getHostName();
	        
		    // Get canonical host name
	   //     String canonicalhostname = ip.getCanonicalHostName();
	        
	    	
	}
	
	public final static String convertPacket(Packet p){
		byte[] bytes=new byte[p.header.length+p.data.length]; 
        System.arraycopy(p.header,0,bytes,0,p.header.length); 
        System.arraycopy(p.data,0,bytes,p.header.length,p.data.length); 
        StringBuffer buf=new StringBuffer(); 
        for(int i=0,j;i<bytes.length;){ 
                for(j=0;j<8 && i<bytes.length;j++,i++){ 
                        String d=Integer.toHexString((int)(bytes[i]&0xff)); 
                        buf.append((d.length()==1?"0"+d:d)+" "); 
                 if(bytes[i]<32 || bytes[i]>126) bytes[i]=46; //avoid the garbage characters 
                } 
                buf.append("["+new String(bytes,i-j,j)+"]\n");
        }
		return buf.toString();
	}
	public final static String convertData(Packet p){ 
        byte[] bytes=new byte[p.data.length]; 
        System.arraycopy(p.data,0,bytes,0,p.data.length); 
        String text = new String(bytes); 
        return text; 
	}
	
	public final static String convertHeader(Packet p){ 
		byte[] bytes=new byte[p.header.length]; 
		System.arraycopy(p.header,0,bytes,0,p.header.length); 
		String text = new String(bytes); 
		return text; 
	} 
	
	public final static String getGoogleSearchString(Packet p){
		String header = convertData(p);
		Matcher m = googleSearchPattern.matcher(header);
		String searchString=null;
		while (m.find()) {
		     searchString = m.group(1);
		    // s now contains "BAR"
		}
		//String searchString = header.split("\n")[0];
		//String searchString = headergoogleSearchPattern.replaceAll(pattern, "$2");
		
			return searchString;
	}
	public final static String getAmazonSearchString(Packet p){
		String header = convertData(p);
		Matcher m = amazonSearchPattern.matcher(header);
		String searchString=null;
		while (m.find()) {
		     searchString = m.group(1);
		}
			return searchString.replaceAll("\\+", " ");
	}
	public final static String getAmazonProductString(Packet p){
		String header = convertData(p);
		Matcher m = amazonProductPattern.matcher(header);
		String searchString=null;
		while (m.find()) {
		     searchString = m.group(1);
		}
		
			return searchString.replaceAll("-", " ");
	}
	
	/**
	 * Checks if it's an IP Adress or a hostname
	 * @param ipAddress
	 * @return true if this is a real IP Adress
	 */
	public final static boolean validateIPAddress( String  ipAddress )
	{
	    String[] parts = ipAddress.split( "\\." );
	    if ( parts.length != 4 )
	    {
	        return false;
	    }

	    for ( String s : parts )
	    {
	        int i = Integer.parseInt( s );

	        if ( (i < 0) || (i > 255) )
	        {
	            return false;
	        }
	    }

	    return true;
	}
	
	public final static String extractHost(TCPPacket p){
		String hostname=p.dst_ip.getHostAddress();
		String[] lines = convertData(p).split("\n");
		if(lines[1].substring(0,4).equals("Host")){
			hostname = lines[1].substring(6,lines[1].length()-1);
		}
		hostname = shortUrl(hostname);
		return hostname;
	}
	/**
	 * Reduces a Domain String to domain.tld, ignores IP Adresses
	 * @param hostname
	 * @return
	 */
	public final static String shortUrl(String hostname){
		String[] hostnameParts = hostname.split("\\.");
		if(hostnameParts.length>2 && !validateIPAddress(hostname)){
			hostname = hostnameParts[hostnameParts.length-2]+"."+hostnameParts[hostnameParts.length-1];
		}
		return hostname;
	}
	public final static String extractURL(TCPPacket p){
		String url = null;
		String get=null;
		String[] lines = convertData(p).split("\n");
		if(lines[0].substring(0,3).equals("GET")){
			get = lines[0];//.substring(4);
			Matcher m = urlStrip.matcher(get);
			while (m.find()) {
			     get = m.group(1);
			}
		}
		
		url = extractHost(p)+get;
		return url;
	}
	
	public final static String extractWikipediaPage(TCPPacket p){
		String pagename=null;
		String[] lines = convertData(p).split("\n");
		if(lines[6].substring(0,7).equals("Referer")){
			String[] words = lines[6].split("wiki/");
			pagename = words[1].substring(0, words[1].length()-1);
		}
		return pagename;
	}
	
	public String translateLocalHost(String ip){
		String host = HostDict.getHost(ip);
		if(host==null){
			return ip;
		}else{
			return host;	
		}
	}
}

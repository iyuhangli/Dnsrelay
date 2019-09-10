import java.io.IOException; 
import java.net.DatagramPacket; 
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class DNSRelay {
	//set dns port and local port and data len
	public static final int DNS_PORT  = 53;
	public static final int LOCAL_PORT = 53;	//local listen port
	private static final int DATA_LEN = 4096;	//package max length
	@SuppressWarnings("unused")
	private static final CharSequence NULL = null;
	byte[] inBuff = new byte[DATA_LEN];
	Check _check;
	DatagramSocket socket;
	private DatagramPacket  inPacket = new  DatagramPacket(inBuff,  inBuff.length);	//receive package
	private DatagramPacket  outPacket;	//send out packet
	private byte[] sendData;
	byte[] finalData;
	
	private String domainNameStr;	//domain name
	private byte[] domainNamebytes;
	private InetAddress  resolverAddress;

	private int resolverPort;
	private boolean IPv6_Flag = false;//is ipv6?
	int udpCursor;	//to locate of packet to resolve
	int ansCursor;
	boolean timeFlag = true;
	private Map<Integer ,  IDTransition> idMap = new HashMap<Integer , IDTransition>(); 
	SimpleDateFormat time=new  SimpleDateFormat("yyyy/MM/dd-HH:mm:ss:SSS");
	Calendar cal;
	
	//To control how many threads, now is 4
    ExecutorService servicePool = Executors.newFixedThreadPool(1);

	public DNSRelay( ){
	}	
	public DNSRelay( Check check){
		_check = check;
	}
	
	public String getDomainName() {
		String domainName  = "";
		udpCursor = 12;
		//level domain length
		int length = Convert.byte2Int( sendData, udpCursor);
		//if 0, jump out
		while (length != 0) {
			udpCursor++;
			domainName = domainName+Convert.byte2String( sendData, udpCursor, length ) + ".";
			udpCursor += length;
			length = Convert.byte2Int( sendData, udpCursor);
		}
		domainNamebytes = new byte[udpCursor-11];
		System.arraycopy(sendData, 12, domainNamebytes, 0, udpCursor-11);
		udpCursor++;
		//check is ipv6? and set flag if it is ipv6
		if ( sendData[udpCursor] == 0x00  && sendData[udpCursor + 1] == 0x1c)  IPv6_Flag = true;
		udpCursor += 4;
		//return domain name and delete "."
		return domainName.substring(0, domainName.length() - 1);
	}
	
	public void init() {
		try {
			//bind 53
			socket = new  DatagramSocket(LOCAL_PORT);
			//listen all the time
			listener( );
		} catch (Exception e) {
			//close socket
			socket.close();
			e.printStackTrace();
		}
	}
	
	//add the situation after data overtime
	public void timer2(int key) {
	        final int keyy = key;
	        Timer timer = new Timer();
	        timer.schedule(new TimerTask() {
	            public void run() {
	                if(timeFlag){
	                	if(Main.debugLevel) {
	                		cal = Calendar.getInstance();
	                		Date nowtime = cal.getTime();
	                		System.out.println("Time at " + time.format(nowtime)+ " ID "+keyy+" from "+resolverAddress+" to "+Main.DNS_IP_ADD+" the data is timeout.");		                    
		                    System.out.println("Values before remove: "+  idMap);
	                	}
	                	idMap.remove(keyy);
	                	if(Main.debugLevel) {		                    
		                    System.out.println("Values after remove: "+  idMap);
	                	}
	                }
	            }
	        }, 5000);//delay after 5000ms
	    }
	
	public void listener( ) throws IOException{

		while (true)  {			
			socket.receive(inPacket);	//receive udp padcket
			sendData = inPacket.getData();	//get dns info
			//Use this to trans DNSRelay object.
			//servicePool.execute(new ThreadsControl(inPacket,this));

			if (isQuery())
				handleQuery(); //query
			else 
				handleResponse();//response		
		}	
	}
	
	public void handleQuery( ) throws IOException{
		cal = Calendar.getInstance();
		Date receiveTime = cal.getTime();
		domainNameStr = getDomainName( );//get domain name
		System.out.println("\nReceive time: " + time.format(receiveTime));
		System.out.print("Domain name: " + domainNameStr);
		byte[] type = new byte[2];
		type[0] = sendData[udpCursor-4];
		type[1] = sendData[udpCursor-3];
		System.out.print(",TYPE:" + Convert.byte2Short(type));
		type[0] = sendData[udpCursor-2];
		type[1] = sendData[udpCursor-1];
		System.out.println(",CLASS:" + Convert.byte2Short(type));
		resolverAddress  = inPacket.getAddress(); //save source address and port
		resolverPort  = inPacket.getPort();
		System.out.println("Client ip address: " + resolverAddress);
		if (_check.ipTable.containsKey(domainNameStr))
			localDNS( );//find at local domain resolve table
		else
			remoteDNS( );	
	}
	
	public void handleResponse( ) throws IOException{
		int responseID = Convert.byte2Short( sendData );
		if (idMap.containsKey(responseID)){ 
			timeFlag = false;
			IDTransition id = idMap.get(responseID);
			outPacket = new DatagramPacket(sendData, sendData.length, id.getAddr(),  id.getPort());	//   ת���յ���Զ�� DNS �� response
			System.out.println("Function:response");
			if(Main.debugLevel) {
				String[] print = new String[outPacket.getLength()/2];
				int count = 0;
				int flag;
				System.out.println("Send to :" + id.getAddr());
				System.out.println("Packet:");
				for(int i = 0 ;i < outPacket.getLength()/2;i++) {
					flag = 0;
					print[i] = Convert.bytesToHex(outPacket.getData()).substring(count, count + 2);
					count = count +2;
					System.out.print(print[i] + " ");
					if((i+1)%35 == 0)
						System.out.println();
					for(int j = 0; j < 20 ;j++) {
						if(Convert.rightTrim(Convert.bytesToHex(outPacket.getData())).charAt(count+j)=='0')
							flag++;
					}
					if(flag==20)
					{
						break;
					}
				}
				System.out.println("\n(" + count/2 + "bytes)");
				System.out.print("ID:" + (int) Convert.byte2Short( sendData ));
				System.out.print(" QR:" + Convert.hexString2binaryString(print[2] + print[3]).substring(0, 1));
				System.out.print(" opcode:" + Integer.valueOf(Convert.hexString2binaryString(print[2] + print[3]).substring(1, 5),2).toString());
				System.out.print(" AA:" + Convert.hexString2binaryString(print[2] + print[3]).substring(5, 6));
				System.out.print(" TC:" + Convert.hexString2binaryString(print[2] + print[3]).substring(6, 7));
				System.out.print(" RD:" + Convert.hexString2binaryString(print[2] + print[3]).substring(7, 8));
				System.out.print(" RA:" + Convert.hexString2binaryString(print[2] + print[3]).substring(8, 9));
				System.out.print(" rcode:" + Integer.valueOf(Convert.hexString2binaryString(print[2] + print[3]).substring(12, 16),2).toString());
				System.out.println();
				byte[] temp = new byte[2];
				temp[0] = outPacket.getData()[4];
				temp[1] = outPacket.getData()[5];
				System.out.print("QDCOUNT:" + Convert.byte2Short(temp));
				temp[0] = outPacket.getData()[6];
				temp[1] = outPacket.getData()[7];
				System.out.print(" ANCOUNT:" + Convert.byte2Short(temp));
				temp[0] = outPacket.getData()[8];
				temp[1] = outPacket.getData()[9];
				System.out.print(" NSCOUNT:" + Convert.byte2Short(temp));
				temp[0] = outPacket.getData()[10];
				temp[1] = outPacket.getData()[11];
				System.out.print(" ARCOUNT:" + Convert.byte2Short(temp));
				System.out.println();
			}
			socket.send(outPacket);
		}
	}
	
	public boolean isQuery( ){
		return ( ( sendData[2] & 0x80 ) == 0x00 );
	}
	
	public void localDNS( ) throws IOException{
		//Get ip address
		String LocalDNSipAddress = _check.ipTable.get(domainNameStr);
		
		if (LocalDNSipAddress.equals("0.0.0.0"))
			shield( );
		else
			relay( );	//if not 0.0.0.0, control at local and send back to resolver
	}
	
	public void shield( ) throws IOException{
		System.out.println("function: " + "shield"); //shield
		sendData[2]  = (byte) (sendData[2] | 0x81);	//fix flag respose(flag=0x8183)
		sendData[3]  = (byte) (sendData[3] | 0x83);
		outPacket  =  new  DatagramPacket(sendData, sendData.length,  resolverAddress, resolverPort);//send
		if(Main.debugLevel) {
			String[] print = new String[outPacket.getLength()/2];
			int count = 0;
			int flag;
			System.out.println("Send to :" + resolverAddress);
			System.out.println("Packet:");
			for(int i = 0 ;i < outPacket.getLength()/2;i++) {
				flag = 0;
				print[i] = Convert.bytesToHex(outPacket.getData()).substring(count, count + 2);
				count = count +2;
				System.out.print(print[i] + " ");
				if((i+1)%35 == 0)
					System.out.println();
				for(int j = 0; j < 20 ;j++) {
					if(Convert.rightTrim(Convert.bytesToHex(outPacket.getData())).charAt(count+j)=='0')
						flag++;
				}
				if(flag==20) {
					break;
				}
			}
			System.out.println("\n(" + count/2 + "bytes)");
			System.out.print("ID:" + (int) Convert.byte2Short( sendData ));
			System.out.print(" QR:" + Convert.hexString2binaryString(print[2] + print[3]).substring(0, 1));
			System.out.print(" opcode:" + Integer.valueOf(Convert.hexString2binaryString(print[2] + print[3]).substring(1, 5),2).toString());
			System.out.print(" AA:" + Convert.hexString2binaryString(print[2] + print[3]).substring(5, 6));
			System.out.print(" TC:" + Convert.hexString2binaryString(print[2] + print[3]).substring(6, 7));
			System.out.print(" RD:" + Convert.hexString2binaryString(print[2] + print[3]).substring(7, 8));
			System.out.print(" RA:" + Convert.hexString2binaryString(print[2] + print[3]).substring(8, 9));
			System.out.print(" rcode:" + Integer.valueOf(Convert.hexString2binaryString(print[2] + print[3]).substring(12, 16),2).toString());
			System.out.println();
			byte[] temp = new byte[2];
			temp[0] = outPacket.getData()[4];
			temp[1] = outPacket.getData()[5];
			System.out.print("QDCOUNT:" + Convert.byte2Short(temp));
			temp[0] = outPacket.getData()[6];
			temp[1] = outPacket.getData()[7];
			System.out.print(" ANCOUNT:" + Convert.byte2Short(temp));
			temp[0] = outPacket.getData()[8];
			temp[1] = outPacket.getData()[9];
			System.out.print(" NSCOUNT:" + Convert.byte2Short(temp));
			temp[0] = outPacket.getData()[10];
			temp[1] = outPacket.getData()[11];
			System.out.print(" ARCOUNT:" + Convert.byte2Short(temp));
			System.out.println();
		}
		socket.send(outPacket); 
		IPv6_Flag = false;
	}
	public void relay( ) throws IOException{
		if (IPv6_Flag){
			remoteDNS( );
		}
		//ipv4
		else {
			finalData = new byte[udpCursor + 14 + domainNamebytes.length];
			ansCursor = 0; //answer cursor
			setAnswerCount( );
			setName(domainNamebytes);
			setType( );
			setClass( );
			setTTL( );
			setIPLength( );
			setResponseIP( );
			outPacket  =  new  DatagramPacket(finalData, finalData.length,  resolverAddress, resolverPort);
			if(Main.debugLevel) {
				String[] print = new String[2048];
				int count = 0;
				System.out.println("Send to :" + resolverAddress);
				System.out.println("Packet: ");
				for(int i = 0 ;i < Convert.bytesToHex(outPacket.getData()).length()/2;i++) {
					print[i] = Convert.bytesToHex(outPacket.getData()).substring(count, count + 2);
					count = count +2;
					System.out.print(print[i] + " ");
					if((i+1)%35 == 0)
						System.out.println();
				}
				System.out.println("\n(" + count/2 + "bytes)");
				System.out.print("ID:" + (int) Convert.byte2Short( sendData ));
				System.out.print(" QR:" + Convert.hexString2binaryString(print[2] + print[3]).substring(0, 1));
				System.out.print(" opcode:" + Integer.valueOf(Convert.hexString2binaryString(print[2] + print[3]).substring(1, 5),2).toString());
				System.out.print(" AA:" + Convert.hexString2binaryString(print[2] + print[3]).substring(5, 6));
				System.out.print(" TC:" + Convert.hexString2binaryString(print[2] + print[3]).substring(6, 7));
				System.out.print(" RD:" + Convert.hexString2binaryString(print[2] + print[3]).substring(7, 8));
				System.out.print(" RA:" + Convert.hexString2binaryString(print[2] + print[3]).substring(8, 9));
				System.out.print(" rcode:" + Integer.valueOf(Convert.hexString2binaryString(print[2] + print[3]).substring(12, 16),2).toString());
				System.out.println();
				byte[] temp = new byte[2];
				temp[0] = outPacket.getData()[4];
				temp[1] = outPacket.getData()[5];
				System.out.print("QDCOUNT:" + Convert.byte2Short(temp));
				temp[0] = outPacket.getData()[6];
				temp[1] = outPacket.getData()[7];
				System.out.print(" ANCOUNT:" + Convert.byte2Short(temp));
				temp[0] = outPacket.getData()[8];
				temp[1] = outPacket.getData()[9];
				System.out.print(" NSCOUNT:" + Convert.byte2Short(temp));
				temp[0] = outPacket.getData()[10];
				temp[1] = outPacket.getData()[11];
				System.out.print(" ARCOUNT:" + Convert.byte2Short(temp));
				System.out.println();
			}
			socket.send(outPacket);
		}		
	}
	public void setAnswerCount( ){
		
		System.out.println("Function: " + "IPV4 local response");
		//set flag response(flag=0x8180)
		//set answer count to 1
		sendData[2]  = (byte) (sendData[2] | 0x85); 
		sendData[3]  = (byte) (sendData[3] | 0x80); 
		sendData[6]  = (byte) (sendData[6] | 0x00); 
		sendData[7]  = (byte) (sendData[7] | 0x01); 
		System.arraycopy(sendData,  0, finalData, ansCursor, udpCursor);
	}
	
	//save name
	public void setName(byte[] name){
		ansCursor = udpCursor;		
		System.arraycopy(name,  0, finalData, ansCursor, name.length);
		ansCursor += name.length;
	}
	
	//save typeA
	public void setType( ){
		short typeA = (short) 0x0001; 
		System.arraycopy(Convert.short2Byte(typeA),  0, finalData, ansCursor, 2);
		ansCursor += 2;
	}
	
	//save classA
	public void setClass( ){
		short classA = (short) 0x0001; 
		System.arraycopy(Convert.short2Byte(classA),  0, finalData, ansCursor, 2);
		ansCursor += 2;
	}
	
	//save timeLive 
	public void setTTL( ){
		int timeLive = 0x00015180; 
		System.arraycopy(Convert.int2Byte(timeLive),  0, finalData, ansCursor, 4);
		ansCursor += 4;
	}
	
	//save responseIPLen 
	public void setIPLength( ){
		short responseIPLen = (short) 0x0004; 
		System.arraycopy( Convert.short2Byte(responseIPLen),  0,finalData, ansCursor, 2);
		ansCursor += 2;
	}
	
	//save responseIP
	public void setResponseIP( ) throws UnknownHostException  {
		byte[] responseIP = InetAddress.getByName( _check.ipTable.get(domainNameStr)).getAddress(); 
		System.arraycopy(responseIP, 0, finalData, ansCursor, 4);	
		ansCursor += 4; 
	}

	public void remoteDNS( ) throws IOException{
		cal = Calendar.getInstance();
		Date ztime = cal.getTime();
		System.out.println("Function: " + "send to remote DNS");
		System.out.println("Send time: "  +  time.format(ztime));
		outPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(Main.DNS_IP_ADD), DNS_PORT);
		if(Main.debugLevel) {
			String[] print = new String[outPacket.getLength()/2];
			int count = 0;
			int flag;
			System.out.println("Send to :" + InetAddress.getByName(Main.DNS_IP_ADD));
			System.out.println("Packet:");
			for(int i = 0 ;i < outPacket.getLength()/2;i++) {
				flag = 0;
				print[i] = Convert.bytesToHex(outPacket.getData()).substring(count, count + 2);
				count = count +2;
				System.out.print(print[i] + " ");
				if((i+1)%35 == 0)
					System.out.println();
				for(int j = 0; j < 20 ;j++) {
					if(Convert.rightTrim(Convert.bytesToHex(outPacket.getData())).charAt(count+j)=='0')
						flag++;
				}
				if(flag==20) {
					break;
				}
			}
			System.out.println("\n(" + count/2 + "bytes)");
			System.out.print("ID:" + (int) Convert.byte2Short( sendData ));
			System.out.print(" QR:" + Convert.hexString2binaryString(print[2] + print[3]).substring(0, 1));
			System.out.print(" opcode:" + Integer.valueOf(Convert.hexString2binaryString(print[2] + print[3]).substring(1, 5),2).toString());
			System.out.print(" AA:" + Convert.hexString2binaryString(print[2] + print[3]).substring(5, 6));
			System.out.print(" TC:" + Convert.hexString2binaryString(print[2] + print[3]).substring(6, 7));
			System.out.print(" RD:" + Convert.hexString2binaryString(print[2] + print[3]).substring(7, 8));
			System.out.print(" RA:" + Convert.hexString2binaryString(print[2] + print[3]).substring(8, 9));
			System.out.print(" rcode:" + Integer.valueOf(Convert.hexString2binaryString(print[2] + print[3]).substring(12, 16),2).toString());
			System.out.println();
			byte[] temp = new byte[2];
			temp[0] = outPacket.getData()[4];
			temp[1] = outPacket.getData()[5];
			System.out.print("QDCOUNT:" + Convert.byte2Short(temp));
			temp[0] = outPacket.getData()[6];
			temp[1] = outPacket.getData()[7];
			System.out.print(" ANCOUNT:" + Convert.byte2Short(temp));
			temp[0] = outPacket.getData()[8];
			temp[1] = outPacket.getData()[9];
			System.out.print(" NSCOUNT:" + Convert.byte2Short(temp));
			temp[0] = outPacket.getData()[10];
			temp[1] = outPacket.getData()[11];
			System.out.print(" ARCOUNT:" + Convert.byte2Short(temp));
			System.out.println();
		}
		socket.send(outPacket);
		System.out.println("Number of response: " + idMap.size());
		timeFlag = true;
        timer2((int) Convert.byte2Short( sendData ));//SocketTimeoutException can catch the timeout, but we can control time in function timer2
        IPv6_Flag = false;
		IDTransition idTransition = new IDTransition( (int) Convert.byte2Short( sendData ), resolverPort,  resolverAddress);
		idMap.put(idTransition.getOldID(), idTransition);
	}
}
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
public class DNSRelay {
// DNS ip 地址  和端口
	public static final int DNS_PORT  = 53;
	public static final int LOCAL_PORT = 53;	//   本地监听端口
	private static final int DATA_LEN = 4096;	//   包的最大容量
	@SuppressWarnings("unused")
	private static final CharSequence NULL = null;

	byte[] inBuff = new byte[DATA_LEN];
	
	Check _check;
	DatagramSocket socket;
	private DatagramPacket  inPacket = new  DatagramPacket(inBuff,  inBuff.length);	//   接受包

	private DatagramPacket  outPacket;	//   转发包

	
	private byte[] sendData;
	byte[] finalData;
	
	private String domainNameStr;	//   解析的域名
	private byte[] domainNamebytes;
	private InetAddress  resolverAddress;	// resolver ip 地址和端口

	private int resolverPort;
	private boolean IPv6_Flag = false;	//   包类型为 ipv6 标志

	int udpCursor;	//   指针，指向当前要解析的包的位置

	int ansCursor;
	boolean timeFlag = true;
	// <key , value> = <packet.id,  packet.socket>
	private Map<Integer ,  IDTransition> idMap = new HashMap<Integer , IDTransition>(); 
	SimpleDateFormat time=new  SimpleDateFormat("yyyy/MM/dd-HH:mm:ss:SSS");
	Calendar cal;
	
	public DNSRelay( ){
		
	}	
	
	public DNSRelay( Check check){
		
		_check = check;
		
	}
	
	public String getDomainName() {
		
		//   解析的域名 
		String domainName  = "";
		udpCursor = 12;
		
		//   某个层级域名的长度
		int length = Convert.byte2Int( sendData, udpCursor);
		
		//   当标志位下一分级域名长度为 0 时跳出
		while (length != 0) {
			
			udpCursor++;
			domainName = domainName+Convert.byte2String( sendData, udpCursor, length ) + ".";
			udpCursor += length;
			length = Convert.byte2Int( sendData, udpCursor);
		}
		domainNamebytes = new byte[udpCursor-11];
		System.arraycopy(sendData, 12, domainNamebytes, 0, udpCursor-11);
		udpCursor++;
		//   判断数据包类型是否为 IPv6 类型。  若是将 IPv6 的 flag 设置为 T rue 
		if ( sendData[udpCursor] == 0x00  && sendData[udpCursor + 1] == 0x1c)  IPv6_Flag = true;
		udpCursor += 4;
		
		//   返回域名并去除末尾的'.'
		return domainName.substring(0, domainName.length() - 1);
	}
	
	public void init() {
		
		try {
			//   绑定到 53 号端口
			socket = new  DatagramSocket(LOCAL_PORT);
			
			//   持续监听
			listener( );
		} catch (Exception e) {
			//   关闭套接字
			socket.close();
			e.printStackTrace();
		}
	}
	
	 public void timer2(int key) {
	        final int keyy = key;
	        Timer timer = new Timer();
	        timer.schedule(new TimerTask() {
	            public void run() {
	                if(timeFlag){
	                	if(Main.debugLevel) {
	                		cal = Calendar.getInstance();
	                		Date nowtime = cal.getTime();
	                		System.out.println(time.format(nowtime)+"时确定id为"+keyy+"，由"+resolverAddress+"发往"+Main.DNS_IP_ADD+"的数据包超时啦");		                    
		                    System.out.println("Values before remove: "+  idMap);
	                	}
	                	idMap.remove(keyy);
	                	if(Main.debugLevel) {
	                				                    
		                    System.out.println("Values after remove: "+  idMap);
	                	}
	                    
	                }


	            }
	        }, 5000);// 指定延迟5000毫秒后执行
	    }
	
	public void listener( ) throws IOException{
		while (true)  {			
			socket.receive(inPacket);	//	接收 UDP 报文
			sendData = inPacket.getData();	//   获得 DNS 数据
			if ( isQuery( ) ) handleQuery( ); 	//	query
			else handleResponse( );		//	response		
		}	
	}
	
	public void handleQuery( ) throws IOException{
		cal = Calendar.getInstance();
		Date receiveTime = cal.getTime();
		domainNameStr = getDomainName( );	// 	获得域名
		System.out.println("\n接受时间：" + time.format(receiveTime));
		System.out.print("域名: " + domainNameStr);
		byte[] type = new byte[2];
		type[0] = sendData[udpCursor-4];
		type[1] = sendData[udpCursor-3];
		System.out.print(",TYPE:" + Convert.byte2Short(type));
		type[0] = sendData[udpCursor-2];
		type[1] = sendData[udpCursor-1];
		System.out.println(",CLASS:" + Convert.byte2Short(type));
		resolverAddress  = inPacket.getAddress(); // 	存储报的来源地址和端口号
		resolverPort  = inPacket.getPort();
		System.out.println("客户端IP地址:" + resolverAddress);
		if (_check.ipTable.containsKey(domainNameStr)) localDNS( );	// 	本地域名解析表中找到
		else remoteDNS( );

		
	}
	
	public void handleResponse( ) throws IOException{
		
		int responseID = Convert.byte2Short( sendData );

		if (idMap.containsKey(responseID)){ 
			timeFlag = false;
			IDTransition id = idMap.get(responseID);
			outPacket = new DatagramPacket(sendData, sendData.length, id.getAddr(),  id.getPort());	//   转发收到的远端 DNS 的 response
			System.out.println("功能:响应");
			if(Main.debugLevel) {
				String[] print = new String[outPacket.getLength()/2];
				int count = 0;
				int flag;
				System.out.println("send to :" + id.getAddr());
				System.out.println("报文:");
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
		}
	}
	
	public boolean isQuery( ){
		
		return ( ( sendData[2] & 0x80 ) == 0x00 );
	}
	
	public void localDNS( ) throws IOException{
		
		//   得到域名对应的 IP 地址
		String LocalDNSipAddress = _check.ipTable.get(domainNameStr);
		
		if (LocalDNSipAddress.equals("0.0.0.0")) shield( );//   如果 IP 为 0.0.0.0
		else relay( );	//   如果不为 0.0.0.0   本地组装 UDP 报文并发回 resolver 响应
	}
	
	public void shield( ) throws IOException{
		
		System.out.println("功能：" + "屏蔽"); //   屏蔽
		sendData[2]  = (byte) (sendData[2] | 0x81);	//   修改标志位 response (flag=0x8183)
		sendData[3]  = (byte) (sendData[3] | 0x83);
		
		outPacket  =  new  DatagramPacket(sendData, sendData.length,  resolverAddress, resolverPort);	//   包装数据并发送
		if(Main.debugLevel) {
			String[] print = new String[outPacket.getLength()/2];
			int count = 0;
			int flag;
			System.out.println("send to :" + resolverAddress);
			System.out.println("报文:");
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
		
		//   新组装的包

		if (IPv6_Flag){
			remoteDNS( );

		}
		// IPv4 组包返回
		else {
			/*String tempString = "";
			String finalString = "";
			String name = domainNameStr + ".";
			for(int i = 0; i < name.length() ; i++) {
				if(name.charAt(i) != '.')
					tempString = tempString + name.charAt(i);
				else {
					finalString = finalString + tempString.length() + tempString;
					tempString = "";
				}
			}
			//System.out.println("测试：" + finalString);
			byte[] namebytes = finalString.getBytes();*/
			finalData = new byte[udpCursor + 14 + domainNamebytes.length];
			ansCursor = 0; // answer cursor
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
				System.out.println("send to :" + resolverAddress);
				System.out.println("报文:");
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
		//   响应请求，发送 UDP 报文

		
	}
	public void setAnswerCount( ){
		
		System.out.println("功能：" + "IPV4 本地响应");
		//   修改标志位 response (flag=0x8180)
		//   设置 Answer count   为 1
		sendData[2]  = (byte) (sendData[2] | 0x85); 
		sendData[3]  = (byte) (sendData[3] | 0x80); 
		sendData[6]  = (byte) (sendData[6] | 0x00); 
		sendData[7]  = (byte) (sendData[7] | 0x01); 
	
		System.arraycopy(sendData,  0, finalData, ansCursor, udpCursor);
	}
	
	//   保存 name
	public void setName(byte[] name){
		
		ansCursor = udpCursor;		
		System.arraycopy(name,  0, finalData, ansCursor, name.length);
		ansCursor += name.length;
	}
	
	//   保存 typeA
	public void setType( ){
		
		short typeA = (short) 0x0001; 
		System.arraycopy(Convert.short2Byte(typeA),  0, finalData, ansCursor, 2);
		ansCursor += 2;
	}
	
	//   保存 classA
	public void setClass( ){
		
		short classA = (short) 0x0001; 
		System.arraycopy(Convert.short2Byte(classA),  0, finalData, ansCursor, 2);
		ansCursor += 2;
	}
	
	//   保存 timeLive 
	public void setTTL( ){
		
		int timeLive = 0x00015180; 
		System.arraycopy(Convert.int2Byte(timeLive),  0, finalData, ansCursor, 4);
		ansCursor += 4;
	}
	
	//   保存 responseIPLen 
	public void setIPLength( ){
		
		short responseIPLen = (short) 0x0004; 
		System.arraycopy( Convert.short2Byte(responseIPLen),  0,finalData, ansCursor, 2);
		ansCursor += 2;
	}
	
	//   保存 responseIP
	public void setResponseIP( ) throws UnknownHostException  {
		
		byte[] responseIP = InetAddress.getByName( _check.ipTable.get(domainNameStr)).getAddress(); 
		System.arraycopy(responseIP, 0, finalData, ansCursor, 4);	
		ansCursor += 4; 
	}

	public void remoteDNS( ) throws IOException{
		cal = Calendar.getInstance();
		Date ztime = cal.getTime();
		System.out.println("功能：" + "转发到远端 DNS");
		System.out.println("转发时间 ："  +  time.format(ztime));
		outPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(Main.DNS_IP_ADD), DNS_PORT);	//   发送到远端 DNS 请求
		if(Main.debugLevel) {
			String[] print = new String[outPacket.getLength()/2];
			int count = 0;
			int flag;
			System.out.println("send to :" + InetAddress.getByName(Main.DNS_IP_ADD));
			System.out.println("报文:");
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
		System.out.println("未响应: " + idMap.size());
		timeFlag = true;
        timer2((int) Convert.byte2Short( sendData ));
        IPv6_Flag = false;
		IDTransition idTransition = new IDTransition( (int) Convert.byte2Short( sendData ), resolverPort,  resolverAddress);	// id  存储
		idMap.put(idTransition.getOldID(), idTransition);
	}
}
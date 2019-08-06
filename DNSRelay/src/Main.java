import java.io.IOException;
import java.net.UnknownHostException;

public class Main {
	public static String DNS_IP_ADD;
	public static boolean debugLevel;
	public static final int LOCAL_PORT = 53;
	public static void main(String[] args) throws UnknownHostException {
		String first,second,third;
		if(args.length!=3) {
			System.out.println("Error, please try again");
			Main.main(args);
			return;
		}
		first=args[0];
		second=args[1];
		third=args[2];
		if(first.equals("-dd")){
			debugLevel=true;
		}
		else {
			debugLevel=false;
		}
		DNS_IP_ADD=second;
		String filename="src/"+third;
		Check checkObject=new Check();
		try {
			checkObject.readData(filename);
		}catch(IOException err) {
			err.printStackTrace();
		}
		System.out.println("Name Sever: "+DNS_IP_ADD);
		if(debugLevel) {
			System.out.println("Debug level: 1");
		}
		else {
			System.out.println("Debug level: 0");
		}
		System.out.println("Try to bind UDP port "+LOCAL_PORT);
		System.out.println("OK!");
		System.out.println("Try to load data from "+filename);
		System.out.println("OK!");
		System.out.println(checkObject.ipTable.size()+" names,"+"occupy " + checkObject.fileSize +" bytes memory");
		System.out.println("========================================");
		(new DNSRelay(checkObject)).init();
	}
}

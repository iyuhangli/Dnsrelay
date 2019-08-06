import java.net.InetAddress;
public class IDTransition {
	private int id;
	private int port;
	private InetAddress addr;
	public IDTransition(int oldID, int port, InetAddress addr) {
		
		this.id = oldID;
		this.port = port;
		this.addr = addr;
	}
	public int getOldID() {
		
		return id;
	}
	public int getPort() {
		
		return port;
	}
	public InetAddress getAddr() {
		
		return addr;
	}
}

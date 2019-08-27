/*
 * Add muti-threads
 * @author Li Yuhang
 */
import java.io.IOException;
import java.net.DatagramPacket;

public class ThreadsControl implements Runnable{
	DNSRelay b=new DNSRelay();
	ThreadsControl(DatagramPacket packet,DNSRelay a) {
		b=a;
    }
	@Override
	public void run() {
		// TODO Auto-generated method stub
		if(b.isQuery()){
			try {
				b.handleQuery();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		else {
			try {
				b.handleResponse();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}//response		
		}	
	}
}

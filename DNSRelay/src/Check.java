import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Check {
	Map<String, String> ipTable=new HashMap<String,String>();
	long fileSize;
	File filename;
	public void readData(String filepath) throws IOException{
		filename = new File(filepath);
		fileSize = filename.length();
		if(!filename.exists()){ 	
			System.out.println("File does not exist");
		}
		else readNameAdress();	
	}
	
	public void readNameAdress() throws IOException{
		String line="";
		FileInputStream inputStream = new FileInputStream(filename);
		InputStreamReader inputStreamR = new InputStreamReader(inputStream);
		BufferedReader bufferReader = new BufferedReader(inputStreamR);
		while( (line = bufferReader.readLine())!=null ) {
			String[] get_ip = line.split(" ");
			String IPaddress = get_ip[0];
			String DomainName = get_ip[1];
			ipTable.put(DomainName,IPaddress);
		} 
		inputStream.close();
		inputStreamR.close();
		bufferReader.close();
	}
}

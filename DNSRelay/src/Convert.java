public class Convert {
	private static final char[] HEX_CHAR = {'0', '1', '2', '3', '4', '5', 
            '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
	public static int byte2Int( byte[] buf, int udpCursor){
		
		return buf[ udpCursor ] & 0xFF;
	}

	public static String byte2String( byte[] buf, int udpCursor, int length ){
		
		return new String( buf, udpCursor, length ); 
	}
	
	public static byte[] short2Byte( short s ){
		
        byte[] targets = new byte[2];  
        for (int i = 0; i < 2; i++) {  
            int offset = (targets.length - 1 - i) * 8;  
            targets[i] = (byte) ((s >>> offset) & 0xff);  
        }  
        return targets; 		
	}
	
	public static byte[] int2Byte( int s ){
		
        byte[] targets = new byte[4];  
        for (int i = 0; i < 4; i++) {  
            int offset = (targets.length - 1 - i) * 8;  
            targets[i] = (byte) ((s >>> offset) & 0xff);  
        }  
        return targets;
	}
	
	public static short byte2Short( byte[] buf ){
		
        int targets = (buf[1] & 0xff) | ((buf[0] << 8) & 0xff00);
        return ( short )targets;  
	}
	public static String bytesToHex(byte[] bytes) {
        char[] buf = new char[bytes.length * 2];
        int index = 0;
        for(byte b : bytes) {
            buf[index++] = HEX_CHAR[b >>> 4 & 0xf];
            buf[index++] = HEX_CHAR[b & 0xf];
        }

        return new String(buf);
    }
	public static String hexString2binaryString(String hexString)
    {
        if (hexString == null || hexString.length() % 2 != 0)
            return null;
        String bString = "", tmp;
        for (int i = 0; i < hexString.length(); i++)
        {
            tmp = "0000"
                    + Integer.toBinaryString(Integer.parseInt(hexString
                            .substring(i, i + 1), 16));
            bString += tmp.substring(tmp.length() - 4);
        }
        return bString;
    }

public static String rightTrim(String str) {
		if (str == null) {
			return "";
		}
		int length = str.length();
		for (int i = length - 1; i >= 0; i--) {
			if (str.charAt(i) != 0x20) {
				break;
			}
			length--;
		}
		return str.substring(0, length);
	}
}

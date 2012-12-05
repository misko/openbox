import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class MD5 {
	
	public static String MD5(byte[] bytes) {
		MessageDigest m;
		try {
			m = MessageDigest.getInstance("MD5");
			m.update(bytes,0,bytes.length);
			return new BigInteger(1,m.digest()).toString(16); 
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}

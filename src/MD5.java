import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class MD5 {
	private static MessageDigest md5;
    
    static {
            try {
                    md5 = MessageDigest.getInstance("md5");
            } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                    System.exit(1);
            }
    }
    
    
	public static String MD5string(final byte[] bytes) {
		md5.reset();
		return new BigInteger(1,md5.digest(bytes)).toString(16); 
	}
}

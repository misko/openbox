import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A wrapper to compute the SHA1 checksum of a file
 *
 */
public class SHA1 {
	public static String ChecksumFile(String filename) throws IOException {
		MessageDigest md = null; 
		FileInputStream fis = null; 
		String sum=null;
		try { 
			md=MessageDigest.getInstance("SHA1");
			fis=new FileInputStream(filename);

			byte[] dataBytes = new byte[1024];
			
			int nread = 0;

			while ((nread = fis.read(dataBytes)) != -1) {
				md.update(dataBytes, 0, nread);
			};
			

			byte[] mdbytes = md.digest();

			// convert the byte to hex format
			StringBuffer sb = new StringBuffer("");
			for (int i = 0; i < mdbytes.length; i++) {
				sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16)
						.substring(1));
			}

			//System.out.println("Digest(in hex format):: " + sb.toString());
			
			sum=sb.toString();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (fis!=null) {
				try {
					fis.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		return sum;
	}
	
	
	
}

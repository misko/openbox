import java.io.FileNotFoundException;


public class openbox {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		System.out.println("test");
		String filename = "/Users/miskodzamba/test.c";
		Checksum.ChecksumFile(filename);
		try {
			RollingChecksum rl = new RollingChecksum(filename,30);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}

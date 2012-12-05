import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

/**
 * This class is used to compute the adler64 checksums of a file.
 * 
 */
public class RollingChecksum {
	private static long modp=4294967291l;
	FileInputStream fis=null;
	long a=0;
	long b=0;
	long mask = 0xffffffff;
	LinkedList<Byte> data;
	boolean initialized=false;
	
	/** 
	 * Initialize the rolling checksum
	 * @param filename The filename to use
	 * @throws FileNotFoundException If we cant find the file...
	 */
	public RollingChecksum(String filename) throws FileNotFoundException {
		fis=new FileInputStream(filename);
		
		data=new LinkedList<Byte>();
		
		// initialize the rolling checksum
		update(OpenBox.blocksize);
		//System.out.println("INITIALIZED WITH "+data.size());
		initialized=true;
	}
	
	/**
	 * Returns the adler64 checksum of consecutive non-overlapping windows of size OpenBox.blocksize
	 * @return The checksum of consecutive data blocks
	 */
	public long[][] blocks() {
		LinkedList<long[]> ll=new LinkedList<long[]>();
		do {
			long h[] = hash();
			ll.add(h);
			//System.out.println(""+ h[0]+ " " +h[1]);
		} while (update(OpenBox.blocksize)>0);
		
		return ll.toArray(new long[0][0]);
	}
	
	/**
	 * Return the size of the current window and the hash
	 * @return (size,adler64 hash)
	 */
	public long[] hash() {
		long h[] =new long[2];
		h[0]=data.size();
		h[1]=((b&mask)<<32)+(a & mask );
		//assert(h[1]==raw_checksum());
		return h;
	}
	
	/**
	 * Move the window by one byte
	 * @return True if the window has been moved
	 */
	public boolean update() {
		return update(1)>0  ? true : false;
	}
	
	/**
	 * Compute the checksum from the data in the window, not using the rolling variables.
	 * Useful for debugging.
	 * @return The adler64 checksum of data in the current window
	 */
	public long raw_checksum() {
		long ap=0;
		long bp=0;
		for (Byte y : data) {
			ap=(ap-y)%modp;
			bp=(bp-OpenBox.blocksize*y)%modp;
		}
		return ((bp&mask)<<32)+(ap&mask);
	}
	
	/** 
	 * Move the rolling window by d bytes
	 * @param d The number of bytes to move the window by
	 * @return The number of bytes that the window has been moved by
	 */
	public int update(int d) {
		try {
			int bytes_read=0;
			for (int i=0; i<d; i++) {

				if (initialized && data.size()>0) {
					// remove the old byte and update
					//System.out.println("removing old byte");
					Byte y = data.poll();
					a = (a - y) % modp;
					b = (b - OpenBox.blocksize * y) % modp;
				}
				
				Byte z = (byte) fis.read();
				if (z!=-1) {
					bytes_read++;
					//System.out.println("read new byte");
					data.offer(z);
					a=(a+z)%modp;
					b=(b+a)%modp;
				}
				//means we have data update

			}
			return bytes_read;
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}
	
}

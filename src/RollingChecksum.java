import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;


public class RollingChecksum {
	private static long modp=4294967291l;
	FileInputStream fis=null;
	long a=1;
	long b=0;
	int blocksize=0;
	LinkedList<Byte> data;
	boolean initialized=false;
	public RollingChecksum(String filename, int blocksize) throws FileNotFoundException {
		fis=new FileInputStream(filename);
		this.blocksize=blocksize;
		
		data=new LinkedList<Byte>();
		
		// initialize the rolling checksum
		update(blocksize);
		initialized=true;
	}
	
	public long[][] blocks() {
		LinkedList<long[]> ll=new LinkedList<long[]>();
		do {
			long h[] = hash();
			ll.add(h);
			//System.out.println(""+ h[0]+ " " +h[1]);
		} while (update(blocksize)>0);
		
		return ll.toArray(new long[0][0]);
	}
	
	public long[] hash() {
		long h[] =new long[2];
		h[0]=data.size();
		h[1]=(b<<32)+(a & ( (1<<32)-1));
		return h;
	}
	
	public boolean update() {
		return update(1)>0  ? true : false;
	}
	
	public int update(int d) {
		int i=0;
		try {
			int bytes_read=0;
			for (i=0; i<d; i++) {

				if (initialized && data.size()>0) {
					// remove the old byte and update
					Byte y = data.poll();
					a = (a - y) % modp;
					b = (b - blocksize * y) % modp;
				}
				
				Byte z = (byte) fis.read();
				if (z!=-1) {
					bytes_read++;
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
		return i;
	}
	
}

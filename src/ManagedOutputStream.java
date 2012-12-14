import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;


public class ManagedOutputStream extends OutputStream {

	OutputStream os;
	public int bytes_per_second=100000;
	public long total_bytes_sent=0;
	
	public ManagedOutputStream(OutputStream os) {
		this.os=os;
	}
	
	public void set_bw_limit(int in) {
		bytes_per_second=in;
	}
	
	
	@Override
	public void write(int b) throws IOException {

		long start_time = System.currentTimeMillis();
		os.write(b);

		
		if (bytes_per_second>0) {
			long wait_time = (long) (((double)1000)/bytes_per_second);
			long already_waited_time = System.currentTimeMillis() - start_time;
			if (already_waited_time<wait_time) {
				try {
						Thread.sleep(wait_time-already_waited_time);
				} catch (InterruptedException e) {
					OpenBox.log(0, "Critical error in sending data...");
					e.printStackTrace();
				}
			}
		}
		
		total_bytes_sent++;
	}
	
	@Override
	public void write(byte[] b) throws IOException {
		if (bytes_per_second>0) { 
			int bytes_sent=0;
			while (bytes_sent<b.length) {
	
				long start_time = System.currentTimeMillis();
				assert(bytes_per_second<(1<<30));
				int from=bytes_sent;
				int to=bytes_sent+java.lang.Math.min(bytes_per_second/4, b.length-bytes_sent);
				byte[] b2 = Arrays.copyOfRange(b, from, to );
			
				os.write(b2);
				
				long wait_time = (long) (((double)b2.length*1000)/bytes_per_second);
				long already_waited_time = System.currentTimeMillis() - start_time;
				if (already_waited_time<wait_time) {
					try {
							Thread.sleep(wait_time-already_waited_time);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				
				bytes_sent+=b2.length;
				total_bytes_sent+=b2.length;
			}
		} else {
			os.write(b);
			total_bytes_sent+=b.length;
		}
	}
	
	@Override
	public void write(byte[] b, int off, int len) throws IOException  {
		write(Arrays.copyOfRange(b, off, off+len));
	}

}

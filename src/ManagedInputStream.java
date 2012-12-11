import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;


public class ManagedInputStream extends InputStream {

	InputStream is;
	public int bytes_per_second=100000;
	public long total_bytes_recv=0;
	
	
	public ManagedInputStream(InputStream is) {
		this.is=is;
	}
	
	@Override
	public int read() throws IOException {
		long start_time = System.currentTimeMillis();
		int r = is.read();
		
		long wait_time = (long) (((double)1000)/bytes_per_second);
		long already_waited_time = System.currentTimeMillis() - start_time;
		if (already_waited_time<wait_time) {
			try {
					Thread.sleep(wait_time-already_waited_time);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		total_bytes_recv++;
		return r;
	}
	
	@Override
	public int read(byte[] b) throws IOException  {
		return read(b,0,b.length);
	}
	
	@Override
	public int read(byte[] b, int off, int len) throws IOException  {

		//OpenBox.log(0, "Recving " + len);
		int bytes_recv=0;
		int r=1;
		while (r>=0 && bytes_recv<len) {

			long start_time = System.currentTimeMillis();
			assert(bytes_per_second<(1<<30));
			int read_len=java.lang.Math.min(bytes_per_second/4, len-bytes_recv);
		
			r = is.read(b,off+bytes_recv,read_len);

			if (r>0) {
				long wait_time = (long) (((double)r*1000)/bytes_per_second);
				long already_waited_time = System.currentTimeMillis() - start_time;
				if (already_waited_time<wait_time) {
					try {
							Thread.sleep(wait_time-already_waited_time);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				
				bytes_recv+=r;
				total_bytes_recv+=r;
			}
		}

		//OpenBox.log(0, "Recvd " + bytes_recv);
		return bytes_recv;
	}

}

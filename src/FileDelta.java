import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.LinkedList;


public class FileDelta {
	LinkedList<Block> ll;
	
	public FileDelta(String filename, long[][] sums) {
		//lets make a hash table from the sums
		try {
			//the hard case lets match these up
			HashSet<Long> have_these= new HashSet<Long>();
			for (int i=0; i<sums.length; i++) {
				have_these.add(sums[i][1]);
			}
			//now lets see if we roll what we get
			long last_offset=0;
			long current_offset=0;
			RollingChecksum rl = new RollingChecksum(filename);
			long h[] = rl.hash();
			current_offset+=h[0]-1;
			System.out.println("HAVE "+current_offset);
			while (true) {
				int moved=0;
				h=rl.hash();
				if (have_these.contains(h[1])) {
					if (last_offset+h[0]-1<current_offset) {
						System.out.println("Missing " + last_offset + " " + (current_offset-h[0]));
					}
					System.out.println("Adding hash! "+h[1]+ " " + (current_offset-h[0]+1) + " to "+current_offset);
					last_offset=current_offset+1;
					moved=rl.update(OpenBox.blocksize); //move ahead how many bytes we read
				} else {
					moved=rl.update(1);
				}

				current_offset+=moved;
				//System.out.println(h[1]);
				if (moved==0) {
					break;
				}
			}
			if (last_offset!=current_offset) {
				System.out.println("Mistmatch "+last_offset + " " +current_offset);
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			// the easy case, return all the blocks to request
			e.printStackTrace();
		}
	}
	
}

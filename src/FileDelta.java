import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;


public class FileDelta {
	String repo_filename;
	String local_filename;
	LinkedList<Block> ll;
	
	public void init(String local_filename, String repo_filename, long[][] sums) {
		this.local_filename=local_filename;
		this.repo_filename=repo_filename;
		//lets make a hash table from the sums
		ll = new LinkedList<Block>();
		
		try {
			//the hard case lets match these up
			HashSet<Long> need_these= new HashSet<Long>(); //TODO check the SHA1 of data!!
			for (int i=0; i<sums.length; i++) {
				need_these.add(sums[i][1]);
				System.out.println("Check sum " + sums[i][1]);
			}
			
			HashMap<Long,Block> have_these =new HashMap<Long, Block>();
			long current_offset=0; //the 
			RollingChecksum rl = new RollingChecksum(local_filename);
			long h[] = rl.hash();
			long moved=h[0];
			while (moved>0) {
				//System.out.println("X"+h[1]);
				if (need_these.contains(h[1])) {
					if (!have_these.containsKey(h[1])) {
						//need to add the block to map
						have_these.put(h[1],new Block(repo_filename,current_offset,true,h[1],h[0]));	
					}
					//moved=rl.update(OpenBox.blocksize);
					moved=rl.update(1);
				} else {
					moved=rl.update(1);
				}
				h = rl.hash();
				current_offset+=moved;
			}
			
			
			//should know all the chunks we have, lets make a list
			current_offset=0;
			for (long other_h[] : sums) {
				Block b;
				if (have_these.containsKey(other_h[1])) {
					//lets just use our block
					b = have_these.get(other_h[1]).copy();
				} else {
					//need to request this block
					b = new Block(repo_filename,current_offset,false,other_h[1],other_h[0]);
				}
				b.dest_offset = current_offset;
				ll.add(b);
				current_offset+=other_h[0];
			}
			
			/*
			
			//now lets see if we roll what we get
			long last_offset=0; //the byte after the last one we processed
			
			
			//lets find out which of the requested hashes we have
			
			
			current_offset+=h[0];
			while (true) {
				int moved=0;
				h=rl.hash();
				if (have_these.contains(h[1])) {
					if (last_offset+h[0]<current_offset) {
						ll.add(new Block(repo_filename,last_offset,false,0,current_offset-h[0]-last_offset));
						last_offset=current_offset-h[0];
						//System.out.println("Missing " + last_offset + " " + (current_offset-h[0]));
					}
					ll.add(new Block(repo_filename,last_offset,true,h[1],h[0]));
					//System.out.println("Adding hash! "+h[1]+ " " + (current_offset-h[0]+1) + " to "+current_offset);
					last_offset=current_offset;
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
				ll.add(new Block(repo_filename,last_offset,false, 0,current_offset-last_offset+1));
			}*/
		} catch (FileNotFoundException e) {
			//cant find the file this means we need to request everything
			long file_size = 0;
			//create one large request block
			for (long[] h : sums) {
				file_size+=h[0];
			}
			Block b = new Block(repo_filename, 0,false, 0, file_size);
			ll.add(b);
		}
	}
	
	public FileDelta(FileState fs, long [][] sums) {
		init(fs.local_filename,fs.repo_filename,sums);
	}
	
	public FileDelta(String local_filename , String repo_filename, long[][] sums) {
		init(local_filename,repo_filename,sums);
	}
	
	public String toString() {
		String r = "FD: " + repo_filename+"\n";
		if (ll!=null) {
			for (Block b: ll ) {
				r+=b+"\n";
			}
		}
		return r;	
	}
	
	
	//TODO should not be calling this for big files, need to call incrementally otherwise files fill up RAM!
	public long assemble_to(String local_filename_out ) {
		long out_bytes=0;
		//we have all the bytes we need!
		try {
			FileOutputStream fos = new FileOutputStream(local_filename_out);

			for (Block b : ll) {
				assert(b.data!=null);
				fos.write(b.data);
				out_bytes+=b.data.length;
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return out_bytes;
	}
	
}

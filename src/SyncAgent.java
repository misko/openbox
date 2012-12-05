import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;


public class SyncAgent {
	Socket sckt;
	ObjectOutputStream oos;
	ObjectInputStream ois;
	
	State state;
	String repo_root;
	
	public final int blocksize = 1024;
	
	
	public SyncAgent(Socket sckt, String repo_root) throws IOException {
		this.sckt=sckt;
		this.repo_root=repo_root;
		oos=new ObjectOutputStream(sckt.getOutputStream());
		ois=new ObjectInputStream(sckt.getInputStream());
		state = new State(repo_root);
		state.update_state();
		System.out.println("MY STATE\n"+state);
	}
	
	public void send(Object o) throws IOException {
		oos.writeObject(o);
	}
	
	public Block request_block(Block b) {
		if (b.use_adler) {
			fetch_data(b);
			return b;
		}
		try {
			send(ControlMessage.RBlock(b));
			send(b);
			Block r = (Block)recieve();
			assert(r.data!=null);
			b.data=r.data; //link up the new data
			return b;
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public long [][] request_fcheck(FileState fs) {
    	try {
    		send(ControlMessage.RFCheck(fs));
    		ControlMessage response_cm = (ControlMessage) recieve();
    		assert(response_cm.type==ControlMessage.FCHECK);
	    	long [][] checksums = (long[][]) recieve();
	    	return checksums;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return null;
	}
	
	public Object recieve() throws IOException, ClassNotFoundException {
		return ois.readObject();
	}
	
	public void sync() {
		//lets send the other the state!
		state.update_state();
		try {
			send(new ControlMessage(ControlMessage.STATE));
			send(state);
			System.out.println("Sent state!");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public void rsync() {
		try {
			State other_state = (State)recieve();
			state.diff(other_state);
			
			//check what we need to get, and ask for fchecks

		    Iterator<Entry<String, FileState>> it = other_state.m.entrySet().iterator();
		    while (it.hasNext()) {
		        Entry<String,FileState> pair = it.next();
		        String repo_filename = pair.getKey();
		        FileState other_filestate = pair.getValue();
		        FileState our_filestate = state.m.get(repo_filename);
		        //ok lets figure out if the server should send it over to us!
		        if (other_filestate.send) {
		        	System.out.println("Requesting FCHECK, "+ other_filestate);
		        	long [][] checksums = request_fcheck(other_filestate);
		        	if (checksums==null) {
		        		System.out.println("A serious error has occured. FCHECK recieved is null!");
		        	} else {
		        		FileDelta fd = new FileDelta(our_filestate,checksums);
		        		System.out.println(fd);
		        		//System.exit(1);
		        		//now we have the file delta lets request to fill the blocks we need
		        		for (Block b : fd.ll) {
		        			b.data=null;
		        			request_block(b);
		        			assert(b.data!=null);
		        		}
		        		//lets try to assemble it now
		        		fd.assemble_to(our_filestate.local_filename);
		        	}
		        }
		        
		    }
			
			//now lets figure out what to request
			//there are two cases, either we are missing a file, or we have a file that needs to be 
			//lets first check which files we are missing and request fchecks for those
			
			/*//find out what files we need to check
			HashSet<String> filenames_to_fcheck = new HashSet<String>();
		    Iterator<Entry<String, String>> it = other_state.m.entrySet().iterator();
		    while (it.hasNext()) {
		        Entry<String,String> pairs = it.next();
		        String repo_filename = pairs.getKey();
		        if (!state.m.containsKey(repo_filename)) {
		        	//we dont have the file
		        	System.out.println("Missing file "+repo_filename);
		        	filenames_to_fcheck.add(repo_filename);
		        } else {
		        	//we have the file but maybe it changed?
		        	if (pairs.getValue().equals(state.m.get(repo_filename))) {
		        		System.out.println("Hash is the same for " + repo_filename);
		        	} else {
		        		System.out.println("Something has changed about " + repo_filename);
			        	filenames_to_fcheck.add(repo_filename);
		        	}
		        }
		        //it.remove(); // avoids a ConcurrentModificationException
		    }

		    //check the required files
		    for (String repo_filename : filenames_to_fcheck) {
		    	System.out.println("Requesting FCHECK of " + repo_filename);
		    	send(new ControlMessage(ControlMessage.RFCHECK));
		    	send(new FileChecksums(repo_filename,null));
		    	//get response
		    	listen_and_handle();
		    }
		    
		    //find out if we have any files the other side does not
		    it = state.m.entrySet().iterator();
		    while (it.hasNext()) {
		        Entry<String,String> pairs = it.next();
		        String repo_filename = pairs.getKey();
		        if (!other_state.m.containsKey(repo_filename)) {
		        	System.out.println("Recieving repo from other side, but other missnig file "+repo_filename);
		        }
		    }
			//can be in any order and any number RFCHECK and RBLOCK*/
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void handle_rfcheck(ControlMessage cm) {
		try {
			assert(cm.repo_filename!=null);
			String filename = state.repo_path+File.separatorChar+cm.repo_filename;
			RollingChecksum rc = new RollingChecksum(filename);
			send(new ControlMessage(ControlMessage.FCHECK));
			send(rc.blocks());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1); //TODO RECOVER!
		}
	}
	
	public byte[] fetch_data(Block b) {
		RandomAccessFile raf =null;
		byte by[]=null;
		try {
			raf = new RandomAccessFile(state.repo_path + File.separatorChar + b.repo_filename,"r");
			assert(b.size<(1>>15)); //casting to int, just make sure
			by = new byte[(int) b.size];
			raf.seek(b.src_offset);
			int r = raf.read(by);
			assert(r==by.length);
			b.data=by;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (raf!=null) {
				try {
					raf.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return by;
	}
	
	public void handle_rblock(ControlMessage cm) {
		try {
			Block b = (Block)recieve();
			assert(!b.use_adler);
			//we need to now fill the block
			fetch_data(b);
			//filled the request sending it back!
			send(b);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/*public void handle_fcheck() {
		try {
			FileChecksum fc = (FileChecksum)recieve();
			System.out.print(fc);
			FileDelta fd = new FileDelta(repo_root+fc.repo_filename, fc.checksums);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}*/
	
	//return ControlMessage if could not handle it
	public boolean listen_and_handle() {
		try {
			ControlMessage cm = (ControlMessage)recieve();
			if (cm.type==ControlMessage.STATE) {
				System.out.println("GOT STATE");
				rsync();
			} else if (cm.type==ControlMessage.RFCHECK) {
				System.out.println("GOT RFCHECK");
				handle_rfcheck(cm);
			} else if (cm.type==ControlMessage.PULL) {
				System.out.println("GOT PULL");
				sync();
			} else if (cm.type==ControlMessage.CLOSE) {
				return false;
			} else if (cm.type==ControlMessage.FCHECK) {
				//need to compare to local file ad update
				System.out.println("Recieved FCHECK");
				//handle_fcheck();
			} else if (cm.type==ControlMessage.RBLOCK) {
				System.out.println("Recieved RBLOCK");
				handle_rblock(cm);
			} else {
				System.out.println("Failed to handle!!!");
				return false;
			}
			return true;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
		
	}
}

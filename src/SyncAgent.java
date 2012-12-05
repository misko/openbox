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
	}
	
	public void send(Object o) throws IOException {
		oos.writeObject(o);
	}
	

	
	public Object recieve() throws IOException, ClassNotFoundException {
		return ois.readObject();
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
	
	
	public void pull() {
		try {
			//send a pull to other side
			send(ControlMessage.pull()); //just for debuggin purposes
			//send a request for state
			send(ControlMessage.rstate());
			//get back the other state
			State other_state = (State)recieve();
			state.reconsolidate(other_state);
			
			System.out.println("MY STATE:\n" + state);
			System.out.println("OTHER STATE:\n"+ other_state);
			
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
		        		File f = new File(our_filestate.local_filename);
		        		f.setLastModified(other_filestate.last_modified);
		        	}
		        }
		        
		    }
		    
		    System.out.println("Client finished pull successfully!");
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			send(ControlMessage.yourturn());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public Block request_block(Block b) {
		if (b.use_adler) {
			fetch_data(b);
			return b;
		}
		try {
			send(ControlMessage.rblock(b));
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
    		send(ControlMessage.rfcheck(fs));
    		ControlMessage response_cm = (ControlMessage) recieve();
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
	
	public void handle_rfcheck(ControlMessage cm) {
		try {
			assert(cm.repo_filename!=null);
			String filename = state.repo_path+File.separatorChar+cm.repo_filename;
			RollingChecksum rc = new RollingChecksum(filename);
			send(rc.blocks());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1); //TODO RECOVER!
		}
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
	
	public void handle_rstate(ControlMessage cm) {
		try {
			send(state);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public ControlMessage listen() {
		try {
			while(true) {
				ControlMessage cm = (ControlMessage)recieve();
				if (cm.type==ControlMessage.RSTATE) {
					System.out.println("GOT RSTATE");
					handle_rstate(cm);
				} else if (cm.type==ControlMessage.RFCHECK) {
					System.out.println("GOT RFCHECK");
					handle_rfcheck(cm);
				} else if (cm.type==ControlMessage.CLOSE) {
					System.out.println("GOT CLOSE");
					send(ControlMessage.close());
					System.out.println("SENT CLOSE");
					return cm;
				} else if (cm.type==ControlMessage.YOUR_TURN) {
					System.out.println("GOT YOUR TURN");
					return cm;
				} else if (cm.type==ControlMessage.RBLOCK) {
					System.out.println("GOT RBLOCK");
					handle_rblock(cm);
				} else if (cm.type==ControlMessage.PULL) {
					System.out.println("GOT PULL Request");
				} else {
					System.out.println("Failed to handle! state while in listen, state="+ cm.type);
					return null;
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
		
	}
	
	public boolean close() {
		try {
			send(ControlMessage.close());
			ControlMessage cm = (ControlMessage)recieve();
			assert(cm.type==ControlMessage.CLOSE);
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

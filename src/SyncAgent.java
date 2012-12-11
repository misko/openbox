import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.vfs2.FileChangeEvent;
import org.apache.commons.vfs2.FileListener;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.impl.DefaultFileMonitor;


public abstract class SyncAgent {
	
	
	Socket sckt;
	ObjectOutputStream oos;
	ObjectInputStream ois;
	
	ManagedOutputStream mos;
	ManagedInputStream mis;
	
	State state;
	String repo_root;
	
	public final int blocksize = 1024;
	
	Timer status_timer;
	

	long last_status_update=0;
	long last_bytes_sent=0;
	long last_bytes_recv=0;
	
	
	public void status() {
		status(last_status_update,last_bytes_sent,last_bytes_recv);
	}
	
	public void status(long last_status_update, long last_bytes_sent, long last_bytes_recv) {
		long now = System.currentTimeMillis();
		long total_bytes_sent=mos.total_bytes_sent;
		long total_bytes_recv=mis.total_bytes_recv;
		double tx_rate =  (((double)total_bytes_sent)-last_bytes_sent)/(now - last_status_update);
		double rx_rate =  (((double)total_bytes_recv)-last_bytes_recv)/(now - last_status_update);
		last_status_update=now;
		OpenBox.log(0, "TX: " + String.format("%.2f",tx_rate) + "kb/s\tRX: " +String.format("%.2f",rx_rate) + "kb/s\tTotal-TX: " +mos.total_bytes_sent/1000 + "kb\tTotal-RX: " +mis.total_bytes_recv +"kb" );
	}
	
	public SyncAgent(String repo_root, State state) throws IOException {
		this.repo_root=repo_root;
		this.state=state;
		last_status_update=System.currentTimeMillis();
		//state.update_state(); //TODO could share this among multiple connections
	}
	
	public void set_socket(Socket sckt) {
		this.sckt=sckt;
		try {
			mos = new ManagedOutputStream(sckt.getOutputStream());
			mis = new ManagedInputStream(sckt.getInputStream());
			oos=new ObjectOutputStream(mos);
			ois=new ObjectInputStream(mis);
			status_timer = new Timer("Status");
			StatusTask st = new StatusTask(this);
			status_timer.schedule(st, 0, 15000);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	
	public void close_socket() {
		try {
			status_timer.cancel();
			sckt.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
			//System.out.println(b);
			assert(b.size<(1<<15)); //casting to int, just make sure
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
	
	public void delete_file(File f ) {
		if (f.isDirectory()) {
			for (File c : f.listFiles()) {
				delete_file(c);
			}
		}
		f.delete();
	}
	
	public State pull() {
		State our_state=null;
		try {
			//send a pull to other side
			send(ControlMessage.pull()); 
			ControlMessage cm = (ControlMessage)recieve();
			if (cm.type==ControlMessage.TRY_LATER) {
				return our_state;
			}
			
			OpenBox.log(0, "Preparing to pull");
			
			//send a request for state
			send(ControlMessage.rstate());
			//get back the other state
			State other_state = (State)recieve();
			our_state = new State(state);
			//System.out.println("MY STATE:\n" + our_state);
			
			//System.out.println("OTHER STATE:\n"+ other_state);
			our_state.reconsolidate(other_state);
			//System.out.println("MY STATE AFTER:\n" + our_state);

			OpenBox.log(0, "Starting to pull");
			
			//delete everything we should delete
		    Iterator<Entry<String, FileState>> it = other_state.m.entrySet().iterator();
		    while (it.hasNext()) {
		        Entry<String,FileState> pair = it.next();
		        String repo_filename = pair.getKey();
		        FileState other_filestate = pair.getValue();
		        FileState our_filestate = our_state.m.get(repo_filename);
		        File f = new File(our_filestate.local_filename);
		        if (our_filestate.deleted && f.exists()) {
		        	OpenBox.log(0, "Trying to delete file "+ our_filestate.repo_filename);
		        	delete_file(f);
		        	f = new File(our_filestate.local_filename);
		        	if (f.exists()) {
		        		OpenBox.log(0, "\t->Failed to delete file " + our_filestate.local_filename);
		        	}
		        }
		    }
		  //check what we need to get, and ask for fchecks
		    it = other_state.m.entrySet().iterator();
		    while (it.hasNext()) {
		        Entry<String,FileState> pair = it.next();
		        String repo_filename = pair.getKey();
		        FileState other_filestate = pair.getValue();
		        FileState our_filestate = our_state.m.get(repo_filename);
		        //ok lets figure out if the server should send it over to us!
		        if (other_filestate.send) {
		        	assert(other_filestate.deleted==false);
		        	if (!other_filestate.directory) {
			        	//System.out.println("Requesting FCHECK, "+ other_filestate);
			        	FileChecksum checksums[] = request_fcheck(other_filestate);
			        	if (checksums==null) {
			        		OpenBox.log(0,"ERROR: Skipping file, checksums are not avaliable! " + other_filestate.repo_filename);
			        	} else {
			        		FileDelta fd = new FileDelta(our_filestate,checksums);
			        		//System.out.println(fd);
			        		//System.exit(1);
			        		//make sure the path here exists
			        		File f = new File(our_filestate.local_filename);
			        		f.getParentFile().mkdirs();
			        		f.setLastModified(0);
			        		//now we have the file delta lets request to fill the blocks we need
			        		final Lock file_lock = new ReentrantLock();
			        		LinkedList<Block> remote_blocks=new LinkedList<Block>();
			        		for (Block b : fd.ll) {
			        			if (b.local_block) {
				        			b.data=null;
				        			request_block(b);
				        			file_lock.lock();
				        			b.write_to_file(our_filestate.local_filename);
				        			f.setLastModified(our_filestate.last_modified);
				        			file_lock.unlock();
				        			assert(b.data!=null);
			        			} else {
			        				remote_blocks.add(b);
			        			}
			        		}
			        		//TODO can multi-thread here?
			        		boolean abort_file=false;
			        		for (Block b : remote_blocks){
			        			b.data=null;
			        			request_block(b);
			        			if (b.data==null) {
			        				abort_file=true;
			        				OpenBox.log(0, "Aborted file sync!");
			        				break;
			        			} else {
				        			file_lock.lock();
				        			b.write_to_file(our_filestate.local_filename);
				        			f.setLastModified(our_filestate.last_modified);
				        			file_lock.unlock();
				        			assert(b.data!=null);
				        		}
			        		}
			        		//lets try to assemble it now
			        		//fd.assemble_to(our_filestate.local_filename);

			        		state.walk_file(f);
			        		if (!abort_file) {
			        			FileState fs = state.m.get(repo_filename);
			        			if (fs.sha1.equals(other_filestate.sha1)) {
					        		f.setLastModified(other_filestate.last_modified);
			        			}
				        		OpenBox.log(0, "Failed to transfer " +repo_filename);
			        		}
			        	}
		        	} else {
		        		//its a directory
		        		File f = new File(our_filestate.local_filename);
		        		if (!f.exists()) {
		        			f.mkdir();
		        		}
		        		f.setLastModified(other_filestate.last_modified);
		        	}
		        }
		        
		    }
		    
		    //System.out.println("Client finished pull successfully!");
			
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
		return our_state;
	}
	
	
	public Block request_block(Block b) {
		if (b.local_block) {
			fetch_data(b);
			return b;
		}
		try {
			send(ControlMessage.rblock(b));
			send(b);
			Block r = (Block)recieve();
			//assert(r.data!=null);
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
	
	public FileChecksum[] request_fcheck(FileState fs) {
    	try {
    		send(ControlMessage.rfcheck(fs));
	    	FileChecksum checksums[] = (FileChecksum[]) recieve();
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
		OpenBox.log(0, "Handling RFCHECK");
		try {
			assert(cm.repo_filename!=null);
			String filename = state.repo_path+File.separatorChar+cm.repo_filename;
			RollingChecksum rc = new RollingChecksum(filename);
			send(rc.blocks());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			try {
				send(null);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				OpenBox.log(0, "file error and socket error, we are done!");
				System.exit(1);
			}
		}
		OpenBox.log(0, "Handled RFCHECK");
	}
	
	
	public void handle_rblock(ControlMessage cm) {
		try {
			Block b = (Block)recieve();
			assert(!b.local_block);
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
	
	
	public ControlMessage listen(boolean accept_pull) {
		try {
			while(true) {
				ControlMessage cm = (ControlMessage)recieve();
				if (cm.type==ControlMessage.RSTATE) {
					//System.out.println("GOT RSTATE");
					handle_rstate(cm);
				} else if (cm.type==ControlMessage.RFCHECK) {
					//System.out.println("GOT RFCHECK");
					handle_rfcheck(cm);
				} else if (cm.type==ControlMessage.CLOSE) {
					OpenBox.log(1,"GOT CLOSE");
					send(ControlMessage.close());
					OpenBox.log(1,"SENT CLOSE");
					return cm;
				} else if (cm.type==ControlMessage.YOUR_TURN) {
					OpenBox.log(1,"GOT YOUR TURN");
					return cm;
				} else if (cm.type==ControlMessage.RBLOCK) {
					//System.out.println("GOT RBLOCK");
					handle_rblock(cm);
				} else if (cm.type==ControlMessage.PULL) {
					OpenBox.log(1,"GOT PULL Request");
					
					//need to respond with YOUR TURN
					if (accept_pull) {
						send(ControlMessage.yourturn());
						OpenBox.log(1,"->accepted");
					} else {
						send(ControlMessage.try_later());
						OpenBox.log(1,"->rejected");
						return cm;
					}
				} else {
					OpenBox.log(0,"Failed to handle! state while in listen, state="+ cm.type);
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

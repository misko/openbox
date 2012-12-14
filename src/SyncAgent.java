import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;



public abstract class SyncAgent {
	SyncAgent parent_sa; //set if this sync agent is a worker
	LinkedList<SyncAgent> workers;
	final Lock worker_lock = new ReentrantLock();
	final Condition work_to_do  = worker_lock.newCondition();
	final Condition work_done  = worker_lock.newCondition();
	final Condition workers_ready  = worker_lock.newCondition();
	final Condition workers_procced = worker_lock.newCondition();
	LinkedList<Block> worker_blocks;
	int total_worker_blocks=0;
	int worker_blocks_done=0;
	FileState worker_filestate;
	RandomAccessFile worker_raf;
	int workers_busy=0;
	int workers_ready_to_pull=0;
	int workers_ready_to_listen=0;
	
	int bytes_in_per_second=0;
	int bytes_out_per_second=0;
	
	boolean abort_file=false;
	
	boolean workers_pulling=false;
	
	String session_id;
	
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
	
	public void add_worker(SyncAgent sa) {
		worker_lock.lock();
		workers.add(sa);
		worker_lock.unlock();
	}
	
	public void zero_worker_state() {
		total_worker_blocks=0;
		worker_blocks_done=0;
		workers_busy=0;
		workers_ready_to_pull=0;
		workers_ready_to_listen=0;
		abort_file=false;
		workers_pulling=false;
	}
	
	public void worker_pull(LinkedList<Block> remote_blocks, FileState our_filestate) {
		//set up the multi-threading....
		worker_lock.lock();
		assert(worker_filestate==null);
		assert(worker_raf==null);
		
		try {
			worker_raf = new RandomAccessFile(our_filestate.local_filename,"rw");
		} catch (FileNotFoundException e) {
			worker_raf=null;
			OpenBox.log(0, "Failed to open random access file for : " + our_filestate.local_filename + " ,  " +e);
			return;
		}
		worker_filestate=our_filestate;
		
		
		abort_file=false;
		workers_busy=0;
		
		
		
		worker_blocks=(LinkedList<Block>) remote_blocks.clone();
		total_worker_blocks=remote_blocks.size();
		assert(worker_blocks_done==0);
		//run the requesters
		work_to_do.signalAll();
		//wait for the done
		try {
			while(!abort_file && total_worker_blocks!=worker_blocks_done) {
				work_done.await();
			}
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		
		worker_filestate=null;
		try {
			worker_raf.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		worker_raf=null;
		
		worker_lock.unlock();
	}
	
	public void worker_help_pull() {

		//OpenBox.log(0, "Worker start ");
		assert(parent_sa!=null);
		parent_sa.worker_lock.lock();
		File f = null;
		while (parent_sa.workers_pulling) {
			while(parent_sa.workers_pulling && (parent_sa.abort_file || parent_sa.worker_blocks.size()==0)) {
				try {
					parent_sa.work_to_do.await();
				} catch (InterruptedException e) {
					OpenBox.log(0, "Worker thread was interrupted!");
					parent_sa.worker_lock.unlock();
					return;
				}
			}

			while(parent_sa.workers_pulling && (!parent_sa.abort_file && parent_sa.worker_blocks.size()>0)) {

				
				//do some work, worker!
				Block b = parent_sa.worker_blocks.poll();

				parent_sa.workers_busy++;
				parent_sa.worker_lock.unlock();
				
				if (f==null) {
					f = new File(parent_sa.worker_filestate.local_filename);
				}
				
				//process the block
				//OpenBox.log(0, b.toString());
				b.data=null;
				request_block(b);
				//OpenBox.log(3, "GOT " +b);
				parent_sa.worker_lock.lock();
				parent_sa.workers_busy--;

				if (b.data==null) {
					parent_sa.abort_file=true;
				} else {
					//b.write_to_file(parent_sa.worker_filestate.local_filename);
					b.write_to_file(parent_sa.worker_raf);
					f.setLastModified(parent_sa.worker_filestate.last_modified);
					b.data=null; //free the memory!
				}

				parent_sa.worker_blocks_done++;
				
				if (parent_sa.abort_file || parent_sa.worker_blocks_done==parent_sa.total_worker_blocks) {
					parent_sa.work_done.signal();
				}
			}
		}
		parent_sa.worker_lock.unlock();
		try {
			send(ControlMessage.yourturn());
		} catch (IOException e) {
			OpenBox.log(0, "Worker failed to send control message correctly : " + e);
		}
		//OpenBox.log(0, "Worker done ");
	}
	
	public void status() {
		status(last_status_update,last_bytes_sent,last_bytes_recv);
	}
	
	public void status(long last_status_update, long last_bytes_sent, long last_bytes_recv) {
		long now = System.currentTimeMillis();
		long total_bytes_sent=mos.total_bytes_sent;
		long total_bytes_recv=mis.total_bytes_recv;
		for (SyncAgent sa : workers ) {
			total_bytes_sent+=sa.mos.total_bytes_sent;
			total_bytes_recv+=sa.mis.total_bytes_recv;
		}
		double tx_rate =  (((double)total_bytes_sent)-last_bytes_sent)/(now - last_status_update);
		double rx_rate =  (((double)total_bytes_recv)-last_bytes_recv)/(now - last_status_update);
		OpenBox.log(0, "TX: " + String.format("%.2f",tx_rate) + "kb/s\tRX: " +String.format("%.2f",rx_rate) + "kb/s\tTotal-TX: " +total_bytes_sent/1000 + "kb\tTotal-RX: " +total_bytes_recv/1000 +"kb");
		this.last_bytes_recv=total_bytes_recv;
		this.last_bytes_sent=total_bytes_sent;
		this.last_status_update=now;
	}
	
	public SyncAgent(String repo_root, State state,int bytes_in_per_second, int bytes_out_per_second) throws IOException {
		this.repo_root=repo_root;
		this.state=state;
		
		this.bytes_in_per_second=bytes_in_per_second;
		this.bytes_out_per_second=bytes_out_per_second;
		
		workers=new LinkedList<SyncAgent>();
		worker_blocks=new LinkedList<Block>();
		last_status_update=System.currentTimeMillis();
		//state.update_state(); //TODO could share this among multiple connections
	}
	
	public void set_socket(Socket sckt) {
		this.sckt=sckt;
		try {
			mos = new ManagedOutputStream(sckt.getOutputStream());
			mos.set_bw_limit(bytes_in_per_second);
			mis = new ManagedInputStream(sckt.getInputStream());
			mis.set_bw_limit(bytes_out_per_second);
			oos=new ObjectOutputStream(mos);
			ois=new ObjectInputStream(mis);
			/*oos=new ObjectOutputStream(sckt.getOutputStream());
			ois=new ObjectInputStream(sckt.getInputStream());*/
			status_timer = new Timer("Status");
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	public void turn_on_status(long delta) {
		last_status_update=0;
		last_bytes_sent=0;
		last_bytes_recv=0;
		StatusTask st = new StatusTask(this);
		status_timer.schedule(st, 0, delta);
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
		oos.reset();
	}
	

	
	public Object recieve() throws IOException, ClassNotFoundException {
		Object o = ois.readObject();
		return o;
	}

	public void fetch_data(Block b) {
		RandomAccessFile raf =null;
		byte by[]=null;
		try {
			raf = new RandomAccessFile(repo_root + File.separatorChar + b.repo_filename,"r");
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
		return;
	}
	
	public void delete_file(File f ) {
		if (f.isDirectory()) {
			for (File c : f.listFiles()) {
				delete_file(c);
			}
		}
		f.delete();
	}
	
	
	public void wait_for_pull_workers() {
		worker_lock.lock();
		while(this.workers_ready_to_pull<OpenBox.num_workers) {
			try {
				workers_ready.await();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		worker_lock.unlock();
	}
	
	public void wait_for_listen_workers() {
		worker_lock.lock();
		while(this.workers_ready_to_listen<OpenBox.num_workers) {
			try {
				workers_ready.await();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		OpenBox.log(0, "Got all listeners!");
		worker_lock.unlock();
	}

	
	public void worker_ready_to_listen() {
		parent_sa.worker_lock.lock();
		parent_sa.workers_ready_to_listen++;
		if (parent_sa.workers_ready_to_listen==OpenBox.num_workers) {

			parent_sa.workers_ready.signalAll();
		}
		try {
			parent_sa.workers_procced.await();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		parent_sa.worker_lock.unlock();
	}
	public void worker_ready_to_pull() {
		parent_sa.worker_lock.lock();
		parent_sa.workers_ready_to_pull++;
		if (parent_sa.workers_ready_to_pull==OpenBox.num_workers) {
			parent_sa.workers_ready.signalAll();
		}
		try {
			parent_sa.workers_procced.await();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		parent_sa.worker_lock.unlock();
	}
	
	public void workers_to_pull() {
		workers_pulling=true;
	}
	
	public void workers_to_listen() {
		worker_lock.lock();
		workers_pulling=false;
		abort_file=false;
		work_to_do.signalAll();
		worker_lock.unlock();
	}
	
	public void workers_procced() {
		worker_lock.lock();
		workers_procced.signalAll();
		worker_lock.unlock();
	}
	
	public State pull() {
		State our_state=null;
			//send a pull to other side
			try {
				send(ControlMessage.pull());
				ControlMessage cm = (ControlMessage)recieve();
				if (cm==null) {
					OpenBox.log(0, "Error during recieve trying to recover...");
					return null;
				}
				if (cm.type==ControlMessage.TRY_LATER) {
					return our_state;
				}
			} catch (IOException e) {
				OpenBox.log(0, "Failed to send/recieve pull control messages :  " + e);
				return null;
			} catch (ClassNotFoundException e) {
				OpenBox.log(0, "Failed to send/recieve pull control messages :  " + e);
				return null;
			} 
			
			OpenBox.log(0, "Preparing to pull");
			
			//send a request for state
			State other_state=null;
			try {
				send(ControlMessage.rstate());
				other_state = (State)recieve();
			} catch (IOException e) {
				OpenBox.log(0, "Failed to recieve other state" + e);
				return null;
			} catch (ClassNotFoundException e) {
				OpenBox.log(0, "Failed to recieve other state" + e);
				return null;
			}
			//get back the other state
			our_state = new State(state);
			//System.out.println("MY STATE:\n" + our_state);
			
			//System.out.println("OTHER STATE:\n"+ other_state);
			our_state.reconsolidate(other_state);
			//System.out.println("MY STATE AFTER:\n" + our_state);

			OpenBox.log(0,"waiting for workers...");

			wait_for_pull_workers();
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
			        		//OpenBox.log(0, "Asking for : " + other_filestate);
			        		OpenBox.log(0, "Computing file delta for " + repo_filename);
			        		FileDelta fd = new FileDelta(our_filestate,checksums);
			        		OpenBox.log(0, "Transfering blocks for " + repo_filename);
			        		//System.out.println(fd);
			        		//System.exit(1);
			        		//make sure the path here exists
			        		File f = new File(our_filestate.local_filename);
			        		f.getParentFile().mkdirs();
			        		f.setLastModified(0);
			        		//now we have the file delta lets request to fill the blocks we need
			        		LinkedList<Block> remote_blocks=new LinkedList<Block>();
			        		//OpenBox.log(0, "Getting the local blocks together for " + repo_filename);
			        		for (Block b : fd.ll) {
			        			if (b.local_block) {
				        			b.data=null;
				        			if (b.src_offset==b.dest_offset) {
				        				//skip this! its already there!!!
				        			} else {
				        				OpenBox.log(0, "requesting local block " + b);
				        				request_block(b);
				        				b.write_to_file(our_filestate.local_filename);
				        				f.setLastModified(our_filestate.last_modified);
					        			assert(b.data!=null);
				        			}
			        			} else {
			        				remote_blocks.add(b);
			        			}
			        		}
			        		

			        		//OpenBox.log(0, "Getting the remote blocks together for " + repo_filename);
			        		worker_pull((LinkedList<Block>) remote_blocks,our_filestate);

			        		try {
								state.walk_file(f);
				        		if (!abort_file) {
				        			FileState fs = state.m.get(repo_filename);
				        			if (fs.sha1.equals(other_filestate.sha1)) {
						        		f.setLastModified(other_filestate.last_modified);
				        			} else {
				        				OpenBox.log(0, "Failed to transfer " +repo_filename + " sha1 does not match .. " + fs.sha1 + " vs " + other_filestate.sha1);
				        			}
				        		}
							} catch (IOException e) {
								OpenBox.log(0, "Failed to properly check file after reciving it :( " + e);
								
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
		    OpenBox.log(0, "Done pull");
		    //System.out.println("Client finished pull successfully!");
		
		try {
			send(ControlMessage.yourturn());
		} catch (IOException e) {
			OpenBox.log(0, "Failed to send control message " + e);
		}
		return our_state;
	}
	
	
	public void request_block(Block b) {
		if (b.local_block) {
			fetch_data(b);
			return;
		}
		try {
			send(ControlMessage.rblock(b));
			send(b);
			Block r = (Block)recieve();
			//assert(r.data!=null);
			b.data=r.data; //link up the new data
		} catch (ClassNotFoundException e) {
			OpenBox.log(0, "Failed to retrieve remote block correctly " + e);
			b.data=null;
		} catch (IOException e) {
			OpenBox.log(0, "Failed to retrieve remote block correctly " + e);
			b.data=null;
		}
	}
	
	public FileChecksum[] request_fcheck(FileState fs) {
    	try {
    		send(ControlMessage.rfcheck(fs));
	    	FileChecksum checksums[] = (FileChecksum[]) recieve();
	    	return checksums;
		} catch (IOException e) {
			OpenBox.log(0, "Failed to retrieve remote file checksums correctly " + e);
		} catch (ClassNotFoundException e) {
			OpenBox.log(0, "Failed to retrieve remote file checksums correctly " + e);
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
			OpenBox.log(0, "Failed to send remote file checksums correctly " + e);
			try {
				send(null);
			} catch (IOException e1) {
				e1.printStackTrace();
				OpenBox.err(true, "file error and socket error, we are done!");
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
			OpenBox.log(0,"Failed to correctly recieve rblock : " + e );
		} catch (ClassNotFoundException e) {
			OpenBox.log(0,"Failed to correctly recieve rblock : " + e );
		}
	}
	
	public void handle_rstate(ControlMessage cm) {
		try {
			send(state);
		} catch (IOException e) {
			OpenBox.log(0, "Failed to send state correctly " + e);
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
			OpenBox.log(0,"Failed to correctly recieve next control message : " + e );
		} catch (ClassNotFoundException e) {
			OpenBox.log(0,"Failed to correctly recieve next control message : " + e );
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
			OpenBox.log(0,"Failed to properly close connection! : " + e );
		} catch (ClassNotFoundException e) {
			OpenBox.log(0,"Failed to properly close connection! : " + e );
		}
		return false;
		
	}
	
}

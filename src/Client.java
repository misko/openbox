import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.vfs2.FileChangeEvent;
import org.apache.commons.vfs2.FileListener;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.impl.DefaultFileMonitor;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class Client extends SyncAgent implements Runnable {
	FileObject fo_repo_root;
	final Lock state_lock = new ReentrantLock();
	String host_name;
	int host_port;
	
	
	public boolean resume_session() {
		assert(session_id!=null);
		try {
			send(ControlMessage.resume_session(session_id));
			ControlMessage cm = (ControlMessage)recieve();
			assert(cm.session_id.equals(session_id));
			assert(cm.type==ControlMessage.IN_SESSION);
			return true;
		} catch (IOException e) {
			OpenBox.err(true,"Failed to get a resume session id from server : " + e );
		} catch (ClassNotFoundException e) {
			OpenBox.err(true,"Failed to get a resume session id from server : " + e );
		}
		return false;
	}
	
	public boolean get_new_session_id() {
		try {
			send(ControlMessage.new_session());
			ControlMessage cm = (ControlMessage)recieve();
			assert(cm.type==ControlMessage.IN_SESSION);

			assert(cm.session_id!=null);
			session_id=cm.session_id;
			return true;
		} catch (IOException e) {
			OpenBox.err(true,"Failed to get a new session id from server : " + e );
		} catch (ClassNotFoundException e) {
			OpenBox.err(true,"Failed to get a new session id from server : " + e );
		}
		return false;
	}
	
	
	public Client(String host_name, int host_port, String repo_root, State state) throws UnknownHostException, IOException {
		super(repo_root, state,OpenBox.client_bytes_in_per_second,OpenBox.client_bytes_out_per_second);
		this.host_name=host_name;
		this.host_port=host_port;
		//lets try to listen on the repo folder
		FileSystemManager fsManager = VFS.getManager(); 
		fo_repo_root = fsManager.resolveFile(repo_root); 
		ClientFileListener sfl = new ClientFileListener();  
		DefaultFileMonitor fm = new DefaultFileMonitor(sfl);  
        fm.setDelay(OpenBox.poll_delay);
        fm.setRecursive(true);  
        fm.addFile(fo_repo_root);  
        fm.start();  
	}
	
	public Client(Client c) throws IOException {
		super(c.repo_root,null,OpenBox.client_bytes_in_per_second,OpenBox.client_bytes_out_per_second);
		this.parent_sa=c;
		this.session_id=c.session_id;
		this.host_name=c.host_name;
		this.host_port=c.host_port;
		this.repo_root=c.repo_root;
		
	}
	
	private void connect_socket() {
		Socket local_sckt = null;
		if (OpenBox.use_ssl) {
			SSLSocketFactory sslsocketfactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
			try {
				local_sckt = (SSLSocket) sslsocketfactory.createSocket(host_name, host_port);
			} catch (UnknownHostException e) {
				OpenBox.log(0, "Failed to connect to server! " + e.toString());
				return;
			} catch (IOException e) {
				OpenBox.log(0, "Failed to connect to server! " + e.toString());
				return;
			}
		} else {
			try {
				local_sckt = new Socket(host_name, host_port);
			} catch (UnknownHostException e) {
				OpenBox.log(0, "Failed to connect to server! " + e.toString());
			} catch (IOException e) {
				OpenBox.log(0, "Failed to connect to server! " + e.toString());
			}
		}
		//make the syncagent aware
		set_socket(local_sckt);
	}
	
	public void worker_connect() {
			assert(session_id!=null);
		
			connect_socket();

			resume_session();
			parent_sa.add_worker(this);
			
			//lets see if we should pull new data
			worker_ready_to_pull();
			worker_help_pull();
			
			//now need to turn to listen mode
			worker_ready_to_listen();
			listen(false);
			
			close();
			close_socket();
	}
	
	private void spawn_workers() {
		//spawn some workers
		for (int i=0; i<OpenBox.num_workers; i++ ) {
			try {
				Client ct = new Client(this);
				//lets put the session into the map
				Thread t = new Thread(ct);
				t.start();
			} catch (IOException e) {
				OpenBox.err(true, "Failed to spawn a worker thread! " + e.toString());
			}
		}
	}
	
	public synchronized void synchronize_with_server() {
		zero_worker_state();
			//make a new connection
			OpenBox.log(0, "Client is connecting to server " + host_name+":"+host_port);
			

			connect_socket();
			
			
			get_new_session_id();
			assert(session_id!=null);
			

			OpenBox.log(0, "Client has connected to server " + sckt.getLocalSocketAddress()+ " -> " + sckt.getRemoteSocketAddress() + " session_id:"+session_id);
			
			spawn_workers();
			
			state_lock.lock();

			turn_on_status(OpenBox.status_period);
			//do a quick look at repo entries
			state.quick_repo_walk();
			
			//lets do a pull from the server
			workers_to_pull();
			wait_for_pull_workers();
			workers_procced();
			pull(); 

			//lets do a push to the server if we can
			workers_to_listen();
			wait_for_listen_workers();
			workers_procced();
			ControlMessage cm = listen(true);
			
			//this should end in the server sending YOUR_TURN
			//assert(cm.type==ControlMessage.YOUR_TURN);
			
			//now lets initiate the disconnect sequence
			close();

			close_socket();
			
			state_lock.unlock();
			
			
			OpenBox.log(0, "Client has completed synchronization");
		
	}
	
	class ClientFileListener implements FileListener {		
		private void changeEvent(FileChangeEvent fce) {
			FileObject fo = fce.getFile();
			String repo_filename = fo.getName().getPath().replace(fo_repo_root.getName().getPath(), "");
			OpenBox.log(1, "Client has detected that "+ repo_filename+ " has been changed!");
			//ok, update the respective file in the state
			state_lock.lock();
			//System.out.println("State before: " + state);
			boolean change;
			try {
				change = state.walk_file(new File(repo_root+File.separatorChar+repo_filename));
				//change = state.check_for_zombies() || change;
				//System.out.println("State after: " + state);
				if (change) {
					synchronize_with_server();
				} else {
					OpenBox.log(1, "Fake event fired, there is no change!");
				}
			} catch (IOException e) {
				OpenBox.log(0, "Encountered an error in file event listener : " + e.toString());
			}
			state_lock.unlock();
		}
		
		@Override
		public void fileChanged(FileChangeEvent fce) throws Exception {	
			changeEvent(fce);
		}

		@Override
		public void fileCreated(FileChangeEvent fce) throws Exception {
			changeEvent(fce);
			
		}

		@Override
		public void fileDeleted(FileChangeEvent fce) throws Exception {
			changeEvent(fce);
		}
		
	}


	@Override
	public void run() {
		if (parent_sa==null) {
			//this is the head client

			synchronize_with_server();
			while (true) {
				try {
					Thread.sleep(OpenBox.server_sync_delay);
					synchronize_with_server();
				} catch (InterruptedException e) {
					OpenBox.log(0, "Failed to complete synchronization, got interrupt!" + e.toString());
					break;
				}
			}

			OpenBox.log(0, "Client is exiting!");
		} else {
			//this is a worker
			worker_connect();
		}
		
	}

}

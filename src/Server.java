import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.vfs2.FileChangeEvent;
import org.apache.commons.vfs2.FileListener;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.impl.DefaultFileMonitor;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;


public class Server {
	private SecureRandom random = new SecureRandom();
	HashMap<String, ServerThread> sessions;
	
	final Lock lock = new ReentrantLock();
	final Condition client_closed  = lock.newCondition(); //locks to make sure we know whats going down
	FileObject fo_repo_root;
	
	ServerSocket server_socket;
	int listen_port;
	String repo_root;
	
	int clients_pulling=0;
	int clients_pushing=0;
	boolean waiting_for_update=false; //true if something has changed server side and we want to update
	
	State state; //set to the last state that was syncd, either with remote or with local
	

	final Lock state_lock = new ReentrantLock();
	

	
	public boolean client_read() {
		lock.lock();
		if (!waiting_for_update && clients_pushing==0) {
			clients_pulling++;
			lock.unlock();
			return true;
		}
		lock.unlock();
		return false;
	}
	
	public void client_done_read() {
		lock.lock();
		clients_pulling--;
		lock.unlock();
	}
	
	public boolean client_push() {
		lock.lock();
		if (!waiting_for_update && clients_pulling==0 && clients_pushing==0) {
			clients_pushing++;
			lock.unlock();
			return true;
		}
		lock.unlock();
		return false;
	}
	
	public void client_done_push() {
		lock.lock();
		clients_pushing--;
		lock.unlock();
	}
	
	
	public void server_wait_for_update() {
		waiting_for_update=true;
		lock.lock();
		while (clients_pulling+clients_pushing>0) {
			try {
				client_closed.await();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				lock.unlock();
			}
		}
	}
	
	public void server_done_update() {
		waiting_for_update=false;
	}
	 
	public synchronized String make_session(ServerThread st) {
		String session_id = new BigInteger(130, random).toString(32);
		while (sessions.containsKey(session_id)) {
			session_id = new BigInteger(130, random).toString(32);
		}
		OpenBox.log(0, "New session added " + session_id);
		sessions.put(session_id, st);
		return session_id;
	}
	
	public synchronized ServerThread connect_worker(String session_id, SyncAgent sa) {
		assert(sessions.containsKey(session_id));
		ServerThread st = sessions.get(session_id);
		st.workers.add(sa);
		return st;
	}
	
	public Server(int listen_port, String repo_root, State state) {
		this.listen_port=listen_port;
		this.repo_root=repo_root;
		this.state = state; 
		
		sessions=new HashMap<String, ServerThread>();

		if (OpenBox.use_ssl) {
			SSLServerSocketFactory sslserversocketfactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();

			try {
				server_socket = (SSLServerSocket) sslserversocketfactory.createServerSocket(listen_port);
			} catch (IOException e) {
				OpenBox.err(true, "Failed to create SSL socket : " + e);
			}
		} else {
			try {
				server_socket = new ServerSocket(listen_port);
			} catch (IOException e) {
				OpenBox.err(true, "Failed to create server socket : " + e);
			}
		}
		
		//lets try to listen on the repo folder
		FileSystemManager fsManager;
		try {
			fsManager = VFS.getManager();
			fo_repo_root = fsManager.resolveFile(repo_root); 
		} catch (FileSystemException e) {
			OpenBox.err(true, "Failed to create file system watchers" + e);
		} 
		ServerFileListener sfl = new ServerFileListener();  
		DefaultFileMonitor fm = new DefaultFileMonitor(sfl);  
		fm.setDelay(OpenBox.poll_delay);
		fm.setRecursive(true);  
		fm.addFile(fo_repo_root);  
		fm.start(); 
	}
	

	public void quick_repo_walk(){ 
		state_lock.lock();
		state.quick_repo_walk();
		state_lock.unlock();
	}
	
	public void listen() {
		//make the server listen
		try {
			//OpenBox.log(0, "Server is listening on " + server_socket.getLocalSocketAddress());
			Socket sckt =  server_socket.accept();
			
			ServerThread st = new ServerThread(this,sckt,repo_root,state);
			//lets put the session into the map
			Thread t = new Thread(st);
			t.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	

	class ServerFileListener implements FileListener {

		private void changeEvent(FileChangeEvent fce) {
			FileObject fo = fce.getFile();
			String repo_filename = fo.getName().getPath().replace(fo_repo_root.getName().getPath(), "");
			//System.out.println("Server has detected that "+ repo_filename+ " has been changed!");
			//ok, update the respective file in the state
			state_lock.lock();
			//System.out.println("State before: " + state);
			System.out.println("Event for "+repo_filename);
			try {
				state.walk_file(new File(repo_root+File.separatorChar+repo_filename));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//state.check_for_zombies();
			//System.out.println("State after: " + state);
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


}

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.vfs2.FileChangeEvent;
import org.apache.commons.vfs2.FileListener;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.impl.DefaultFileMonitor;



public class Server {
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
	 
	
	public Server(int listen_port, String repo_root, State state) throws IOException {
		this.listen_port=listen_port;
		this.repo_root=repo_root;
		this.state = state; 
		
		//try to bind the socket
		server_socket = new ServerSocket(listen_port);
		
		//lets try to listen on the repo folder
		FileSystemManager fsManager = VFS.getManager(); 
		fo_repo_root = fsManager.resolveFile(repo_root); 
        ServerFileListener sfl = new ServerFileListener();  
        DefaultFileMonitor fm = new DefaultFileMonitor(sfl);  
        fm.setDelay(OpenBox.poll_delay);
        fm.setRecursive(true);  
        fm.addFile(fo_repo_root);  
        fm.start(); 
	}
	
	synchronized public void listen() {
		//make the server listen
		Socket sckt;
		try {
			OpenBox.log(0, "Server is listening on " + server_socket.getLocalSocketAddress());
			sckt = server_socket.accept();
			//need to pass in a copy of the state!
			state_lock.lock();
			state.check_for_zombies();
			state_lock.unlock();
			State thread_state = new State(state);
			ServerThread st = new ServerThread(this,sckt,repo_root,thread_state);
			Thread t = new Thread(st);
			t.run();
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
			state.walk_file(new File(repo_root+File.separatorChar+repo_filename));
			state.check_for_zombies();
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

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Date;
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

public class Client extends SyncAgent {
	FileObject fo_repo_root;
	final Lock state_lock = new ReentrantLock();
	String host_name;
	int host_port;
	
	public Client(String host_name, int host_port, String repo_root, State state) throws UnknownHostException, IOException {
		super(repo_root, state);
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
	
	
	public synchronized void synchronize_with_server() {
		try {
			//make a new connection
			OpenBox.log(0, "Client is connecting to server " + host_name+":"+host_port);
			
			//Change regular Socket, instead use SSLSocketFactory (Dec 9, 2012)
			//Socket sckt = new Socket(host_name, host_port);
			SSLSocketFactory sslsocketfactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
			Socket sckt = sslsocketfactory.createSocket(host_name, host_port);
			
			OpenBox.log(0, "Client has connected to server " + sckt.getLocalSocketAddress()+ " -> " + sckt.getRemoteSocketAddress());
			//make the syncagent aware
			set_socket(sckt);
			
			state_lock.lock();
			state.check_for_zombies();
			boolean r = pull(); //first pull from the other side
			
			ControlMessage cm = listen(true); //then listen
			assert(cm.type==ControlMessage.YOUR_TURN);
			close();
			state_lock.unlock();
			
			close_socket();
			
			OpenBox.log(0, "Client has completed synchronization");
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	class ClientFileListener implements FileListener {		
		private void changeEvent(FileChangeEvent fce) {
			FileObject fo = fce.getFile();
			String repo_filename = fo.getName().getPath().replace(fo_repo_root.getName().getPath(), "");
			OpenBox.log(1, "Client has detected that "+ repo_filename+ " has been changed!");
			//ok, update the respective file in the state
			state_lock.lock();
			//System.out.println("State before: " + state);
			boolean change = state.walk_file(new File(repo_root+File.separatorChar+repo_filename));
			change = state.check_for_zombies() || change;
			//System.out.println("State after: " + state);
			if (change) {
				synchronize_with_server();
			} else {
				OpenBox.log(1, "Fake event fired, there is no change!");
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

}

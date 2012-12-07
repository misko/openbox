import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Date;

import org.apache.commons.vfs2.FileChangeEvent;
import org.apache.commons.vfs2.FileListener;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.impl.DefaultFileMonitor;



public class Client extends SyncAgent {
	FileObject fo_repo_root;
	boolean pause_file_events=false;
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
			Socket sckt = new Socket(host_name, host_port);
			//make the syncagent aware
			set_socket(sckt);
			
			System.out.println("Client is trying to pull");
			pause_file_events=true;
			boolean r = pull(); //first pull from the other side
			System.out.println("Client is trying to listen");
			ControlMessage cm = listen(true); //then listen
			assert(cm.type==ControlMessage.YOUR_TURN);
			close();
			pause_file_events=false;
			
			close_socket();
			
			System.out.println("Client is exiting \n"+state);
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
			System.out.println("Client has detected that "+ repo_filename+ " has been changed!");
			//ok, update the respective file in the state
			if (!pause_file_events) {
				System.out.println("State before: " + state);
				boolean change = state.walk_file(new File(repo_root+File.separatorChar+repo_filename));
				System.out.println("State after: " + state);
				if (change) {
					synchronize_with_server();
				} else {
					System.out.println("Fake event fired, there is no change!");
				}
			} else {
				System.out.println("Skipping change event for file "+repo_filename);
			}
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

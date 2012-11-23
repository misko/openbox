import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
			System.out.println("Recieved State!");
			System.out.println(other_state);
			System.out.println("My state");
			System.out.println(state);
			//now lets figure out what to request
			//there are two cases, either we are missing a file, or we have a file that needs to be 
			//lets first check which files we are missing and request fchecks for those
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
			//can be in any order and any number RFCHECK and RBLOCK
		    
		    for (String repo_filename : filenames_to_fcheck) {
		    	System.out.println("Requesting FCHECK of " + repo_filename);
		    	send(new ControlMessage(ControlMessage.RFCHECK));
		    	send(new FileChecksums(repo_filename,null));
		    	//get response
		    	listen_and_handle();
		    	//FileChecksums fcheck = recieve();
		    }
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void handle_rfcheck() {
		try {
			FileChecksums fc = (FileChecksums)recieve();
			RollingChecksum rc = new RollingChecksum(repo_root+fc.repo_filename, blocksize);
			FileChecksums rfc = new FileChecksums(fc.repo_filename,rc.blocks());
			send(new ControlMessage(ControlMessage.FCHECK));
			send(rfc);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public void handle_fcheck() {
		try {
			FileChecksums fc = (FileChecksums)recieve();
			System.out.println(fc);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	//return ControlMessage if could not handle it
	public boolean listen_and_handle() {
		try {
			ControlMessage cm = (ControlMessage)recieve();
			if (cm.type==ControlMessage.STATE) {
				System.out.println("GOT STATE");
				rsync();
			} else if (cm.type==ControlMessage.RFCHECK) {
				System.out.println("GOT RFCHECK");
				handle_rfcheck();
			} else if (cm.type==ControlMessage.PULL) {
				System.out.println("GOT PULL");
				sync();
			} else if (cm.type==ControlMessage.CLOSE) {
				return false;
			} else if (cm.type==ControlMessage.FCHECK) {
				//need to compare to local file ad update
				System.out.println("Recieved FCHECK");
				handle_fcheck();
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

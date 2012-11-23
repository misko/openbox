import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;


public class Client extends SyncAgent {

	public Client(String host_name, int host_port, String repo_root) throws UnknownHostException, IOException {
		super(new Socket(host_name, host_port),repo_root);
	}
	

	
	public void initialze() {
		//need to send the state then listen for RFCHECK
		try {
			send(new ControlMessage(ControlMessage.PULL));
			ControlMessage cm = (ControlMessage)recieve();
			assert(cm.type==ControlMessage.STATE);
			rsync();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}

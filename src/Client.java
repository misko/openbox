import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;


public class Client extends SyncAgent {

	public Client(String host_name, int host_port, String repo_root) throws UnknownHostException, IOException {
		super(new Socket(host_name, host_port),repo_root);
	}
	

	
	public void run() {
		System.out.println("Client is trying to pull");
		boolean r = pull(); //first pull from the other side
		System.out.println("Client is trying to listen");
		ControlMessage cm = listen(true); //then listen
		assert(cm.type==ControlMessage.YOUR_TURN);
		close();
		System.out.println("Client is exiting");
	}

}

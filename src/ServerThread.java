import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;


public class ServerThread extends SyncAgent implements Runnable {

	
	public ServerThread(Socket sckt, String repo_root) throws IOException {
		super(sckt,repo_root);
	}
	
	@Override
	public void run() {
		System.out.println("New server thread is running!");
		//lets handle the current connection
		listen();
		//now lets try to pull
		pull();
		//send(ControlMessage.yourturn());
		listen();	
	}

}

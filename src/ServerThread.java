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
		boolean ok=true;
		while (ok) {
			System.out.println("Handling request");
			ok=listen_and_handle();
		}
	}

}

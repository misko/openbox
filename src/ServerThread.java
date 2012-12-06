import java.io.IOException;
import java.net.Socket;


public class ServerThread extends SyncAgent implements Runnable {
	Server server;
	
	public ServerThread(Server server, Socket sckt, String repo_root) throws IOException {
		super(sckt,repo_root);
		this.server=server;
	}
	
	@Override
	public void run() {
		System.out.println("New server thread is running!");
		//lets handle the current connection
		
		boolean client_read = server.client_read();
		ControlMessage cm = listen(client_read);
		if (client_read) {
			server.client_done_read();
		}

		//now lets try to pull
		boolean client_push = server.client_push();
		if (client_push) {
			boolean r = pull();
			server.client_done_push();
			
		} else {
			try {
				send(ControlMessage.yourturn());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		//send(ControlMessage.yourturn());
		listen(false);	
	}

}

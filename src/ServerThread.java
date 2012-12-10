import java.io.IOException;
import java.net.Socket;


public class ServerThread extends SyncAgent implements Runnable {
	Server server;
	
	public ServerThread(Server server, Socket sckt, String repo_root, State state) throws IOException {
		super(repo_root, state);
		set_socket(sckt);
		this.server=server;
	}
	
	@Override
	public void run() {
		OpenBox.log(0, "Spawned new server thread for connection from " + sckt.getRemoteSocketAddress());
		//lets handle the current connection
		
		boolean client_read = server.client_read();
		ControlMessage cm = listen(client_read);
		if (client_read) {
			server.client_done_read();
		}

		//now lets try to pull
		server.state_lock.lock();
		boolean client_push = server.client_push();
		if (client_push) {
			State new_state = pull();
			server.client_done_push();
		} else {
			try {
				send(ControlMessage.yourturn());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		server.state_lock.unlock();
		
		//send(ControlMessage.yourturn());
		listen(false);	
		
		close_socket();
	}

}

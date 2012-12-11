import java.io.IOException;
import java.net.Socket;


public class ServerThread extends SyncAgent implements Runnable {
	Server server;
	
	public String session_handshake() {
		try {
			ControlMessage cm = (ControlMessage) recieve();
			if (cm.type==ControlMessage.NEW_SESSION) {
				//lets make a new sesison and assign it
				session_id = server.make_session(this);
				assert(session_id!=null);
				send(ControlMessage.in_session(session_id));
				return session_id;
			} else {
				//lets connect this to the old session
				session_id = cm.session_id;
				parent_sa=server.connect_worker(cm.session_id, this);
				send(ControlMessage.in_session(session_id));
				return cm.session_id;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
		
	}
	
	public ServerThread(Server server, Socket sckt, String repo_root, State state) throws IOException {
		super(repo_root, state,OpenBox.server_bytes_in_per_second,OpenBox.server_bytes_out_per_second);
		set_socket(sckt);
		this.server=server;
	}
	
	@Override
	public void run() {
		//lets handle the current connection
		
		
		session_handshake();
		assert(session_id!=null);
		
		if (parent_sa==null) { 
			OpenBox.log(0, "Spawned new server thread for connection from " + sckt.getRemoteSocketAddress());
			turn_on_status(OpenBox.status_period);
			server.quick_repo_walk();
			
			boolean client_read = server.client_read();
			//get the workers ready to listen
			wait_for_listen_workers();
			//let them start listening
			workers_procced();
			listen(client_read);
			if (client_read) {
				server.client_done_read();
			}
	
			//move the workers to a pull state
			workers_to_pull();
			wait_for_pull_workers();
			workers_procced();
			
			//now lets try to pull
			server.state_lock.lock();
			boolean client_push = server.client_push();
			if (client_push) {
				pull();
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

			workers_to_listen();
			
			listen(false);	
			OpenBox.log(0, "Server is closing connection " + session_id);
		} else {
			worker_ready_to_listen();
			//OpenBox.log(0, "Worker server side is listening!");
			listen(false);
			worker_ready_to_pull();
			worker_help_pull();
			listen(false);	
			//OpenBox.log(0, "Worker server side is closing");
		}
		close_socket();
	}


}

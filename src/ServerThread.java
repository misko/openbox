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
				mos.set_bw_limit(bytes_in_per_second/OpenBox.num_workers);
				mis.set_bw_limit(bytes_in_per_second/OpenBox.num_workers);
				return cm.session_id;
			}
		} catch (IOException e) {
			OpenBox.log(0,"Failed negotiate session handshake with client : " + e );
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			OpenBox.log(0,"Failed negotiate session handshake with client : " + e );
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
		
		
		String r = session_handshake();
		if (r==null) {
			OpenBox.log(0, "Server thread exiting had an error!");
			return;
		}
		
		if (parent_sa==null) {

			OpenBox.log_skip();
			OpenBox.log(0, "Spawned new server thread for connection from " + sckt.getRemoteSocketAddress());
			start_status();
			turn_on_status(OpenBox.status_period);
			
			state.quick_repo_walk();
			
			boolean client_read = server.client_read();
			//get the workers ready to listen
			wait_for_listen_workers();
			//let them start listening
			workers_procced();
			ControlMessage cm = listen(client_read);
			if (cm==null) {
				//an error happened need to exit
				//TODO should really kill the workers here?
				return;
			}
			if (client_read) {
				server.client_done_read();
			}
	
			//move the workers to a pull state
			workers_to_pull();
			wait_for_pull_workers();
			workers_procced();
			
			//now lets try to pull
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

			workers_to_listen();
			
			listen(false);	
			end_status();
			OpenBox.log(0, "Server is closing connection " + session_id);
		} else {
			worker_ready_to_listen();
			//OpenBox.log(0, "Worker server side is listening!");
			ControlMessage cm = listen(false);
			if (cm==null) {
				//TODO need to clean up here
				return;
			}
			worker_ready_to_pull();
			worker_help_pull();
			listen(false);	
			//OpenBox.log(0, "Worker server side is closing");
		}
		close_socket();
	}


}

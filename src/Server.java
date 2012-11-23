import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;


public class Server {

	String repo_root;
	int listen_port;
	
	ServerSocket server_socket;
	
	State state;
	
	public Server(int listen_port, String repo_root) throws IOException {
		this.listen_port=listen_port;
		this.repo_root=repo_root;
		
		//try to bind the socket
		server_socket = new ServerSocket(listen_port);
		
		//walk to the base repo
		state = new State(repo_root);
	}
	
	synchronized public void listen() {
		//make the server listen
		Socket sckt;
		try {
			sckt = server_socket.accept();
			ServerThread st = new ServerThread(sckt);
			Thread t = new Thread(st);
			//t.run();
			st.read();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}

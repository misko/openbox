import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;


public class Server {

	ServerSocket server_socket;
	int listen_port;
	String repo_root;
	
	public Server(int listen_port, String repo_root) throws IOException {
		this.listen_port=listen_port;
		this.repo_root=repo_root;
		
		//try to bind the socket
		server_socket = new ServerSocket(listen_port);
		
	}
	
	synchronized public void listen() {
		//make the server listen
		Socket sckt;
		try {
			sckt = server_socket.accept();
			ServerThread st = new ServerThread(sckt,repo_root);
			Thread t = new Thread(st);
			t.run();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}

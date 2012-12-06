import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;


public class Server {

	ServerSocket server_socket;
	int listen_port;
	String repo_root;
	
	int clients_pulling=0;
	int clients_pushing=0;
	
	
	public synchronized boolean client_read() {
		if (clients_pushing==0) {
			clients_pulling++;
			return true;
		}
		return false;
	}
	
	public synchronized void client_done_read() {
		clients_pulling--;
	}
	
	public synchronized boolean client_push() {
		if (clients_pulling==0 && clients_pushing==0) {
			clients_pushing++;
			return true;
		}
		return false;
	}
	
	public synchronized void client_done_push() {
		clients_pushing--;
	}
	
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
			ServerThread st = new ServerThread(this,sckt,repo_root);
			Thread t = new Thread(st);
			t.run();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}

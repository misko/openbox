import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;


public class Client {

	Socket sckt;
	ObjectOutputStream oos;
	ObjectInputStream ois;
	
	public Client(String host_name, int host_port, String repo_root) throws UnknownHostException, IOException {
		// TODO Auto-generated constructor stub
		sckt = new Socket(host_name, host_port);
		ois = new ObjectInputStream(sckt.getInputStream());
		oos = new ObjectOutputStream(sckt.getOutputStream());
	}
	
	public void send(Object o) throws IOException {
		oos.writeObject(o);
	}
	
	public Object recieve

}

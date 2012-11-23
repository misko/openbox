import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;


public class ServerThread extends SyncAgent implements Runnable {

	
	public ServerThread(Socket sckt, String repo_root) throws IOException {
		super(sckt,repo_root);
	}
	
	/*public void read() throws IOException {
		ObjectInputStream ois = new ObjectInputStream(sckt.getInputStream());
		try {
			String s = (String) ois.readObject();
			System.out.println("server read : "+s);
			s = (String) ois.readObject();
			System.out.println("server read : "+s);
			Integer i = (Integer) ois.readObject();
			System.out.println("Server read :" + s + " " + i);
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}*/

	private void read_and_handle() {
		try {
			ControlMessage cm = (ControlMessage)recieve();
			if (cm.type==ControlMessage.OK) {
				System.out.println("Recieved " + cm.type + " from client");
				sync();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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

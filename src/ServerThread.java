import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;


public class ServerThread implements Runnable {

	Socket sckt;
	
	public ServerThread(Socket sckt) {
		this.sckt=sckt;
	}
	
	public void read() throws IOException {
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
		
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}

}

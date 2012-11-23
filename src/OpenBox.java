import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;


public class OpenBox {
	
	public static final int blocksize=10;
	
	static boolean server=false;
	static boolean client=false;
	
	//client variables
	static String host_name;
	static int host_port;
	
	
	//server variables
	static int listen_port;
	
	//both
	static String repo_root;
	
	
	public static void main(String[] args) {
		
		
		System.out.println("-p port, -s hostname, -r repo");
		
		//parse the command line arguments
		int port=-1;
		for (int i=0; i<args.length; i++) {
			
			//looking for the port number
			if (args[i].equals("-p")) {
				i++;
				if (i==args.length) {
					System.out.println("ERROR missing port");
					System.exit(1);
				}
				port=Integer.parseInt(args[i]);
			}
			
			//looking for server address
			if (args[i].equals("-s")) {
				i++;
				if (i==args.length) {
					System.out.println("ERROR missing hostname");
					System.exit(1);
				}
				host_name = args[i];
				client=true;
			}
			
			//looking for repo root
			if (args[i].equals("-r")) {
				i++;
				if (i==args.length) {
					System.out.println("ERROR missing repo root");
					System.exit(1);
				}
				repo_root=args[i];
			}
			
		}
		
		File root = new File(repo_root);
		try {
			repo_root=root.getCanonicalPath();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		/*if (server && client) {
			System.out.println("Error cannot be both server and client!");
			System.exit(1);
		}
		if (!server && !client) {
			System.out.println("Must be either server or client");
			System.exit(1);
		}*/
		if (!client) {
			server=true;
		}
		
		if (repo_root==null) {
			System.out.println("Must specify the repo root!");
			System.exit(1);
		}
		
		if (server) {
			listen_port=port;
			Server s;
			try {
				s = new Server(listen_port, repo_root);

				while (true) {
					s.listen();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if (client) {
			host_port=port;
			try {
				Client c = new Client(host_name, host_port, repo_root);
				c.initialze();
				//c.send("test");
				//c.send("what");
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		
		// TODO Auto-generated method stub
		System.out.println("test");
		String filename = "/Users/miskodzamba/test.c";
		Checksum.ChecksumFile(filename);
		try {
			RollingChecksum rl = new RollingChecksum(filename);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}

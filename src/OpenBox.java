import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;


public class OpenBox {
	
	public static final int blocksize=128;
	public static final long poll_delay=1000;
	public static final long server_sync_delay=15000; 
	public static int debug_level=1;
	public static SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss");
	static boolean server=false;
	static boolean client=false;
	
	static boolean set_host_name=false;
	static String host_name;
	static boolean set_host_port=false;
	static int host_port;
	static boolean set_listen_port=false;
	static int listen_port;
	static boolean set_repo_root=false;
	static String repo_root;
	
	public static void log(int level, String s) {
		if (debug_level>level) {
			System.out.println(dateFormat.format(new Date()) + "\t"+s);
		}
	}
	
	public static void usage() {
		String program_name="java OpenBox";
		System.out.println("OpenBox"+"\n"+"------------------");
		System.out.println("Using in server mode: " + program_name + "-p port_to_listen_on -r repository_root");
		System.out.println("Using in client mode: " + program_name + "-p port_to_connect_on -s servername_or_ip -r repository_root");
	}
	
	public static void main(String[] args) {
		
		//parse the command line arguments
		int port=-1;
		for (int i=0; i<args.length; i++) {
			
			//looking for the port number
			if (args[i].equals("-p")) {
				i++;
				if (i==args.length) {
					System.out.println("ERROR missing port");
					usage();
					System.exit(1);
				}
				port=Integer.parseInt(args[i]);
				
			}
			
			//looking for server address
			if (args[i].equals("-s")) {
				i++;
				if (i==args.length) {
					System.out.println("ERROR missing hostname");
					usage();
					System.exit(1);
				}
				host_name = args[i];
				client=true;
				set_host_name=true;
			}
			
			//looking for repo root
			if (args[i].equals("-r")) {
				i++;
				if (i==args.length) {
					System.out.println("ERROR missing repo root");
					usage();
					System.exit(1);
				}
				repo_root=args[i];
				set_repo_root=true;
			}
			
		}
		if (port>=0) {
			if (client) {
				set_host_port=true;
				host_port=port;
			} else {
				set_listen_port=true;
				listen_port=port;
			}
		}

		if (!client) {
			server=true;
		}
		
		
		if (client && set_host_port && set_host_name && set_repo_root) { 
			//everything is good
		} else if (!client && set_listen_port && set_repo_root) {
			//everything is good
		} else {
			//something is not right!
			usage();
			System.exit(1);
		}
		
		File root = new File(repo_root);
		try {
			repo_root=root.getCanonicalPath();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			System.exit(1);
		}
		
		
		if (repo_root==null) {
			System.out.println("Must specify the repo root!");
			System.exit(1);
		}
		
		//lets make the initial state
		State state = new State(repo_root);
		state.update_state();
		if (server) {
			
			//set System.setProperty (Dec 9, 2012)
			System.setProperty("javax.net.ssl.keyStore", repo_root+"/mySrvKeystore");
	        System.setProperty("javax.net.ssl.keyStorePassword", "123456");
			
			listen_port=port;
			Server s;
			try {
				s = new Server(listen_port, repo_root,state);

				while (true) {
					s.listen();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if (client) {
			
			//set System.setProperty (Dec 9, 2012)
			System.setProperty("javax.net.ssl.trustStore", repo_root+"/mySrvKeystore");
	        System.setProperty("javax.net.ssl.trustStorePassword", "123456");
			
			host_port=port;
			try {
				Client c = new Client(host_name, host_port, repo_root,state);
				c.synchronize_with_server();
				while (true) {
					try {
						Thread.sleep(OpenBox.server_sync_delay);
						c.synchronize_with_server();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						break;
					}
				}
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
		
	}

}

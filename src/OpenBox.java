import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;


public class OpenBox {
	
	public static final int blocksize=16*1024;
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
	public static int client_bytes_in_per_second=500000;
	public static int client_bytes_out_per_second=500000;
	public static int server_bytes_in_per_second=500000;
	public static int server_bytes_out_per_second=500000;
	public static int num_workers=3;
	public static long status_period=5000;
	
	private static String ssl_certificate_path=null;
	
	public static void log(int level, String s) {
		if (debug_level>level) {
			System.out.println( dateFormat.format(new Date()) + "\tThread: " + Thread.currentThread().getId() + "\t"+s);
		}
	}	
	
	public static void err(boolean exit, String s) {
		System.out.println( dateFormat.format(new Date()) + "\tThread: " + Thread.currentThread().getId() + "\t"+s);
		if (exit) {
			System.exit(1);
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
			if (!root.exists()) {
				OpenBox.err(true,"The repository root does not exist! repo_root="+repo_root);
			} else if (!root.isDirectory()) {
				OpenBox.err(true,"The repository root is not a directory! repo_root="+repo_root);
			}
		} catch (IOException e) {
			OpenBox.err(true, "Failed to open repository path : " +repo_root + " , "+ e);
		}
		
		
		if (repo_root==null) {
			System.out.println("Must specify the repo root!");
			System.exit(1);
		}
		
		//lets make the initial state
		State state = new State(repo_root);
		try {
			state.update_state();
		} catch (IOException e) {
			OpenBox.err(true, "Failed to open repository path : " +repo_root + " , " + e);
		}
		

		//find out what the ssl certificate path is 
		if (ssl_certificate_path==null) {
			OpenBox.log(0,"Missing ssl_certificate_path, using default " + repo_root + File.separatorChar +  "mySrvKeystore");
			ssl_certificate_path=repo_root+"/mySrvKeystore";
		}
		
		//lets check that the ssl certificate exists!
		File f = new File(ssl_certificate_path);
		if (!f.exists() || !f.isFile() || !f.canRead()) {
			OpenBox.err(true,"SSL certificate file does not exist/is not a file/is not readable! " + ssl_certificate_path);
		}
		
		if (server) {
			
			//set System.setProperty (Dec 9, 2012)
			System.setProperty("javax.net.ssl.keyStore", ssl_certificate_path);
			System.setProperty("javax.net.ssl.keyStorePassword", "123456");
			
			listen_port=port;
			Server s;
			s = new Server(listen_port, repo_root,state);

			while (true) {
				s.listen();
			}
		} else if (client) {
			
			//set System.setProperty (Dec 9, 2012)
			System.setProperty("javax.net.ssl.trustStore", ssl_certificate_path);
			System.setProperty("javax.net.ssl.trustStorePassword", "123456");
			
			host_port=port;
			try {
				Client c = new Client(host_name, host_port, repo_root,state);
				c.run();
			} catch (UnknownHostException e) {

				OpenBox.err(true, "Failed to connect to host: " + e);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}

}

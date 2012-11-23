import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;


public class State {
	Date last_sync;
	HashMap<String,String> m;
	String repo_path;
	
	boolean change=false; //has the state changed since last sync
	
	private boolean walk_dir(File dir) {
		//list the files
		File listFile[] = dir.listFiles();
		if (listFile != null) {
			for (int i = 0; i < listFile.length; i++) {
				if (listFile[i].isDirectory()) {
					change = change || walk_dir(listFile[i]);
				} else {
					String filename;
					try {
						filename = listFile[i].getCanonicalPath();
						String checksum = Checksum.ChecksumFile(filename);
						//System.out.println(filename + " " + Checksum.ChecksumFile(filename));
						if (m.containsKey(filename)) {
							if (!m.get(filename).equals(checksum)) {
								//has the key but checksum changed
								change=true;
							} else {
								//checksum is the same
							}
						} else {
							//does not have this key
							change=true;
						}
						m.put(filename, checksum);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		return change;
	}
	
	public State(String repo_path) {
		this.repo_path=repo_path;
	}
	
	public boolean update_state() { 
		//open the directory
		File dir = new File(repo_path);
		return walk_dir(dir);
	}
	
	public void synced() {
		change=false;
	}
	
	
}

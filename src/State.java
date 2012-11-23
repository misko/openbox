import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class State implements Serializable {
	Date last_sync;
	public HashMap<String,String> m;
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
					try {
						String filename = listFile[i].getCanonicalPath();
						String checksum = Checksum.ChecksumFile(filename);
						String repo_filename=filename.replaceFirst(repo_path, "");
						//System.out.println(filename + " " + Checksum.ChecksumFile(filename));
						if (m.containsKey(repo_filename)) {
							if (!m.get(repo_filename).equals(checksum)) {
								//has the key but checksum changed
								change=true;
							} else {
								//checksum is the same
							}
						} else {
							//does not have this key
							change=true;
						}
						m.put(repo_filename, checksum);
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
		m = new HashMap<String,String>();
		this.repo_path=repo_path;
	}
	
	public boolean update_state() { 
		System.out.println("Updating state");
		//open the directory
		File dir = new File(repo_path);
		return walk_dir(dir);
	}
	
	public void synced() {
		change=false;
		last_sync=new Date(); //TODO this could be out of sync and cause problems!
	}
	
	
	public String toString() {
		String r="";
	    Iterator it = m.entrySet().iterator();
	    while (it.hasNext()) {
	        Map.Entry pairs = (Map.Entry)it.next();
	        r+=pairs.getKey() + " = " + pairs.getValue()+"\n";
	        //it.remove(); // avoids a ConcurrentModificationException
	    }
	    return r;
		
	}
	
	
}

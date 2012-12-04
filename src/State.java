import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;


public class State implements Serializable {
	Date last_sync;
	public HashMap<String,FileState> m;
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
						String local_filename = listFile[i].getCanonicalPath();
						String repo_filename=local_filename.replaceFirst(repo_path, "");
						FileState current_fs = new FileState(repo_filename, local_filename);
						//System.out.println(filename + " " + Checksum.ChecksumFile(filename));
						if (m.containsKey(repo_filename)) {
							if (!m.get(repo_filename).equals(current_fs)) {
								//has the key but checksum changed
								change=true;
							} else {
								//checksum is the same
							}
						} else {
							//does not have this key
							change=true;
						}
						m.put(repo_filename, current_fs);
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
		m = new HashMap<String,FileState>();
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
	    Iterator<Entry<String, FileState>> it = m.entrySet().iterator();
	    while (it.hasNext()) {
	        Entry<String,FileState> pair = it.next();
	        //String repo_filename = pair.getKey();
	        FileState current_filestate=pair.getValue();
	        r+=current_filestate+"\n";
	        //it.remove(); // avoids a ConcurrentModificationException
	    }
	    return r;
	}
	
	
	
	public boolean diff(State other_state) {
		boolean diff=false;
		//find out what files are different or missing from our side
	    Iterator<Entry<String, FileState>> it = other_state.m.entrySet().iterator();
	    while (it.hasNext()) {
	        Entry<String,FileState> pairs = it.next();
	        String repo_filename = pairs.getKey();
        	FileState other_filestate = pairs.getValue();
	        if (!m.containsKey(repo_filename)) {
	        	//we dont have the file
	        	FileState new_filestate = new FileState(repo_filename,repo_path+'/'+repo_filename);
	        	other_filestate.send=true;
	        	m.put(repo_filename,new_filestate);
	        	diff=true;
	        } else {
	        	//we have the file but maybe it changed?
	        	FileState our_filestate=m.get(repo_filename);
	        	if (our_filestate.last_modified>other_filestate.last_modified) {
	        		//then our file is newer!
	        		our_filestate.send=true;
		        	diff=true;
	        	} else if (our_filestate.last_modified<other_filestate.last_modified) {
	        		other_filestate.send=true;
		        	diff=true;
	        	} else {
	        		//the modification times are the same!
	        		if (!our_filestate.sha1.equals(other_filestate.sha1)) {
	        			System.out.println("A serious error has occured. Modification times are same, checksum is different!");
	        		}
	        	}
	        }
	    }
	    //find out what files are missing on the other side
	    it = m.entrySet().iterator();
	    while (it.hasNext()) {
	        Entry<String,FileState> pair = it.next();
	        String repo_filename = pair.getKey();
	        FileState our_filestate = pair.getValue();
	        if (!other_state.m.containsKey(repo_filename)) {
	        	//we dont have the file
	        	our_filestate.send=true;
	        	FileState new_filestate = new FileState(repo_filename,null);
	        	other_state.m.put(repo_filename,new_filestate);
	        	diff=true;
	        }
	    }
	    
	    return diff;
	    
	    
	}
	
	
}

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 * The State object contains the current state of the repository.
 * 
 * This is composed of many individual FileState objects (one for each file) along with the path of the repository root
 * on the local file system and the last synchronization time
 *
 */
public class State implements Serializable {
	/**
	 * The time of last synchronization
	 * Currently not implemented. :(
	 */
	Date last_sync;
	/**
	 * A hashmap of filenames (with respect to repository root) to their corresponding FileState objects
	 */
	public HashMap<String,FileState> m;
	/** 
	 * The path for the root of the repository on the local filesystem
	 */
	String repo_path;
	
	
	public State(State s) {
		this.last_sync=s.last_sync;
		this.repo_path=s.repo_path;
		this.m =  new HashMap<String,FileState>();
	    Iterator<Entry<String, FileState>> it = s.m.entrySet().iterator();
	    while (it.hasNext()) {
	        Entry<String,FileState> pair = it.next();
	        String repo_filename = pair.getKey();
	        FileState fs=pair.getValue();
	        m.put(repo_filename, new FileState(fs));
	    }
	}

	/**
	 * Recursively traverse the given file and update the current state
	 * @param f The file to traverse
	 * @return True if and only if there has been a change in state detected
	 */
	public boolean walk_file(File f) {
		String r_filename;
		try {
			r_filename = f.getCanonicalPath().replace(repo_path, "");
			System.out.println("Walking: " + r_filename);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		boolean change=false;
		if (!f.exists()) {
			//the file does not exist need to mark it as deleted
			try {
				String repo_filename = f.getCanonicalPath().replace(repo_path, "");
				assert(m.containsKey(repo_filename));
				FileState fs = m.get(repo_filename);
				if (fs.deleted==false) {
					fs.deleted=true;
					fs.earliest_deleted_time=(new Date()).getTime()-OpenBox.poll_delay;
					change=true;
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		} else if (f.isDirectory()) {
			File listFile[] = f.listFiles();
			System.out.println("There are "+listFile.length);
			if (listFile != null) {
				for (int i = 0; i < listFile.length; i++) {
					change = walk_file(listFile[i]) || change;
				}
			}
		} else if (f.isFile()) {
			try {
				String local_filename = f.getCanonicalPath();
				String repo_filename=local_filename.replace(repo_path, "");
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
		} else {
			System.out.println("Something bad");
		}
		return change;
	}
	
	/**
	 * Create and populate a new State object from the given root folder
	 * @param repo_path The root of the repository
	 */
	public State(String repo_path) {
		m = new HashMap<String,FileState>();
		this.repo_path=repo_path;
	}
	
	/**
	 * Using the repository root, update the FileState's recursively
	 * @return True if and only if a change has been detected
	 */
	public boolean update_state() { 
		System.out.println("Updating state");
		//open the directory
		File dir = new File(repo_path);
		return walk_file(dir);
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
	
	
	/**
	 * This function potentially updates both the current and give State objects.
	 * 
	 * If a file needs to be sent to the remote server the 'send' flag for the FileState
	 * object will be set to true in the current State. If it is the opposite case the 
	 * 'send' flag will be set to true in the other_state.
	 * 
	 * If a FileState object is missing (with respect to its counterpart), it will be created
	 * with the corresponding flag (local_missing or repo_missing).
	 * 
	 * @param other_state The state to compare/update against
	 * @return True if and only if a difference was detected
	 */
	public boolean reconsolidate(State other_state) {
		boolean diff=false;
		//find out what files are different or missing from our side
	    Iterator<Entry<String, FileState>> it = other_state.m.entrySet().iterator();
	    while (it.hasNext()) {
	        Entry<String,FileState> pairs = it.next();
	        String repo_filename = pairs.getKey();
        	FileState other_filestate = pairs.getValue();
	        if (!m.containsKey(repo_filename)) {
	        	//we dont have the file
	        	FileState new_filestate = new FileState(repo_filename,repo_path+File.separatorChar+repo_filename);
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
	    
	    //check which have been deleted on both sides
	    assert(m.size()==other_state.m.size());
	    it = other_state.m.entrySet().iterator();
	    while (it.hasNext()) {
	        Entry<String,FileState> pairs = it.next();
	        String repo_filename = pairs.getKey();
        	FileState other_filestate = pairs.getValue();
        	FileState our_filestate = m.get(repo_filename);
        	if (other_filestate.deleted && our_filestate.deleted) {
        		//everything is fine
        	} else if (other_filestate.deleted) {
        		//means that not deleted on our side check to see last modified time
        		if (our_filestate.last_modified>other_filestate.earliest_deleted_time) {
        			//we let the file propagate the file
        			other_filestate.deleted=false;
        		} else {
            		our_filestate.deleted=true;
            		//make sure the file is now gone
            		File f = new File(our_filestate.local_filename);
            		boolean  r =f.delete();
            		if (!r) {
            			System.out.println("Permission denied to remove file!" + our_filestate.local_filename);
            		}
            		our_filestate.send=false;
            		other_filestate.send=false;
        		}
        	} else if (our_filestate.deleted) {
        		if (other_filestate.last_modified>our_filestate.earliest_deleted_time) {
        			//let the file propagate
        			our_filestate.deleted=false;
        		} else {
        			other_filestate.deleted=true;
        			our_filestate.send=false;
        			other_filestate.send=false;
        		}
        	}
	    }
	    
	    return diff;
	    
	    
	}
	
	
}

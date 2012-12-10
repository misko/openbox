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
	        FileState new_fs = new FileState(fs);
	        assert(new_fs!=null);
	        m.put(repo_filename, new_fs);
	    }
		check();
	}

	
	public boolean check_for_zombies() {
		check();
		boolean change=false;
	    Iterator<Entry<String, FileState>> it = m.entrySet().iterator();
	    while (it.hasNext()) {
	        Entry<String,FileState> pair = it.next();
	        FileState fs=pair.getValue();
	        String repo_filename=pair.getKey();
	        File f = new File(fs.local_filename);
	    	//OpenBox.log(0, "Zombie checking "+repo_filename + " " +fs.local_filename + " " +fs);
	        if (!fs.deleted && !f.exists()) {
	        	OpenBox.log(0, "ZOMBIE: "+ fs.local_filename);
	        	fs.earliest_deleted_time=(new Date()).getTime()-OpenBox.poll_delay;
	        	fs.deleted=true;
	        	change=true;
	        } if (fs.deleted && f.exists()) {
	        	if (fs.earliest_deleted_time<f.lastModified()) {
	        		OpenBox.log(0, "Ressurecting a zombie file that should be dead!" + repo_filename);
	        		//keep the file!
	        		fs.last_modified=f.lastModified();
	        		fs.deleted=false;
	        		//if we keep this file we should make sure its parent folders are undeleted
	        		File repo_root=new File(repo_path);
	        		File parent = f.getParentFile();
	        		try {
						while(!parent.getCanonicalPath().equals(repo_root.getCanonicalPath())) {
							String repo_parent_filename=parent.getCanonicalPath().replace(repo_path, "");
							if (m.containsKey(repo_parent_filename)) {
								m.get(repo_parent_filename).deleted=false;
							}
							parent=parent.getParentFile();
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	        	} else {
	        		//remove the file!
	        		OpenBox.log(0, "Removing a zombie file that should be dead!" + repo_filename);
	        		f.delete();
	        	}
	        }
	    }
		check();
	    return change;
	}
	
	public boolean check(){ 
		//return true;
		Iterator<Entry<String, FileState>> it = m.entrySet().iterator();
		try {
	    while (it.hasNext()) {
	        Entry<String,FileState> pair = it.next();
	        String repo_filename = pair.getKey();
	        FileState fs=pair.getValue();
	        if (fs==null) {
	        	OpenBox.log(0, "Error in state!" + repo_filename);
	        	throw new IOException();
	        }
	    }
		} catch (IOException e ) {
			e.printStackTrace();
			System.exit(1);
		}
	    return true;
	}
	
	
	
	private boolean check_deleted() {
		Iterator<Entry<String, FileState>> it = m.entrySet().iterator();
		boolean change=false;
	    while (it.hasNext()) {
	        Entry<String,FileState> pair = it.next();
	        String repo_filename = pair.getKey();
	        FileState fs=pair.getValue();
	        File f = new File(fs.local_filename);
	        if (!fs.deleted && !f.exists()) {
	        	fs.deleted=true;
	        	fs.earliest_deleted_time=(new Date()).getTime()-OpenBox.poll_delay;
	        	change=true;
	        }
	    }
	    return change;
	}
	
	private boolean check_new(File f) {
		boolean change=false;
		if (f.isDirectory()) {
			for (File c : f.listFiles()) {
				change=check_new(c) || change;
			}
		}
		//lets see if this file is in the index
		String local_filename;
		try {
			local_filename = f.getCanonicalPath();
			String repo_filename=local_filename.replace(repo_path, "");
			if (!m.containsKey(repo_filename)) {
				FileState fs =  FileState.from_file(repo_filename, local_filename,f.isDirectory());
				change=true;
				m.put(repo_filename, fs);
			} else {
				FileState fs = m.get(repo_filename);
				if (fs.last_modified!=f.lastModified()) {
					FileState current_fs =  FileState.from_file(repo_filename, local_filename,f.isDirectory());
					change=true;
					m.put(repo_filename, current_fs);
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return change;
	}
	
	public boolean quick_repo_walk()  {
		//for every file in the repo make sure its in the index
		File f = new File(repo_path);
		boolean change = check_new(f);
		//for every file in the index see if its alive
		change = check_deleted() || change;
		return change;
	}
	
	/**
	 * Recursively traverse the given file and update the current state
	 * @param f The file to traverse
	 * @return True if and only if there has been a change in state detected
	 * @throws IOException 
	 */
	public boolean walk_file(File f) throws IOException {
		check();
		boolean change=false;

			String local_filename = f.getCanonicalPath();
			String repo_filename=local_filename.replace(repo_path, "");
			assert(m!=null);
			assert(repo_filename!=null);
			if (!f.exists()) {
				//the file does not exist need to mark it as deleted
					assert(m.containsKey(repo_filename));
					FileState fs = m.get(repo_filename);
					assert(fs!=null);
					if (fs.deleted==false) {
						fs.deleted=true;
						fs.earliest_deleted_time=(new Date()).getTime()-OpenBox.poll_delay;
						change=true;
					}
			} else if (f.isFile() || f.isDirectory()) {
					FileState current_fs =  FileState.from_file(repo_filename, local_filename,f.isDirectory());
					if (!current_fs.directory && current_fs.sha1==null) {
						OpenBox.log(0, "Filestate SHA1 returned null, expected non-null in walk-file. "+ repo_filename);
					} else {
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
						if (!f.isDirectory() || !f.getCanonicalPath().equals(repo_path)) {
							assert(current_fs!=null);
							m.put(repo_filename, current_fs);
						}
						//if its a directory recurse
						if (f.isDirectory()) {
							File listFile[] = f.listFiles();
							if (listFile != null) {
								for (int i = 0; i < listFile.length; i++) {
									change = walk_file(listFile[i]) || change;
								}
							}
						}
					}
			} else {
				OpenBox.log(0,"ERROR: File is not a file or directory, "+ local_filename);
			}


			check();
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
	 * @throws IOException 
	 */
	public boolean update_state() throws IOException { 
		OpenBox.log(0,"Updating state");
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
		//check();
		boolean diff=false;
		//find out what files are different or missing from our side
	    Iterator<Entry<String, FileState>> it = other_state.m.entrySet().iterator();
	    while (it.hasNext()) {
	        Entry<String,FileState> pairs = it.next();
	        String repo_filename = pairs.getKey();
        	FileState other_filestate = pairs.getValue();
        	assert(other_filestate.send==false);
	        if (!m.containsKey(repo_filename)) {
	        	//we dont have the file
	        	FileState new_filestate = FileState.from_file(repo_filename,repo_path+File.separatorChar+repo_filename,other_filestate.directory);
	        	if (new_filestate.sha1==null) {
	        		if (other_filestate.deleted) {
	        			new_filestate.deleted=true;
	        		} else {
	        			other_filestate.send=true;
	        		}
		        	m.put(repo_filename,new_filestate);
		        	diff=true;
	        	} else {
	        		//we have a file like this, therefore it should have been indexed
	        		OpenBox.log(0, "reconsolidate found missed file from index " + repo_filename);
	        	}
	        } else {
	        	//we have the file but maybe it changed?
	        	FileState our_filestate=m.get(repo_filename);
	        	assert(our_filestate.send==false);
	        	if (our_filestate.deleted && other_filestate.deleted) {
	        		//lets sync up most recent deletion times
	        		our_filestate.earliest_deleted_time=java.lang.Math.max(our_filestate.earliest_deleted_time, other_filestate.earliest_deleted_time);
	        	} else if (other_filestate.deleted && !our_filestate.deleted) {
	        		//other side is set to deleted but not our side
	        		//lets figure out if we should keep our file or toss it
	        		if (our_filestate.last_modified>other_filestate.earliest_deleted_time) {
	        			//then we keep our file
	        			other_filestate.deleted=false;
	        			our_filestate.send=true;
	        		} else {
	        			//lose our files
	        			our_filestate.deleted=true;
	        			our_filestate.earliest_deleted_time=other_filestate.earliest_deleted_time;
	        		}
	        	} else if (!other_filestate.deleted && our_filestate.deleted) {
	        		//figure out if we should keep or purge our file
	        		if (other_filestate.last_modified>our_filestate.earliest_deleted_time) {
	        			//get the other guys file
	        			our_filestate.deleted=false;
	        			other_filestate.send=true;
	        		} else {
	        			//purge other guys copy
	        			other_filestate.deleted=true;
	        			other_filestate.earliest_deleted_time=our_filestate.earliest_deleted_time;
	        		}
	        	} else if (our_filestate.last_modified>other_filestate.last_modified) {
	        		//then our file is newer!
	        		our_filestate.send=true;
		        	diff=true;
	        	} else if (our_filestate.last_modified<other_filestate.last_modified) {
	        		other_filestate.send=true;
		        	diff=true;
	        	} else {
	        		//the modification times are the same!
	        		//System.out.println(our_filestate);
	        		if (!our_filestate.directory && !our_filestate.sha1.equals(other_filestate.sha1)) {
	        			OpenBox.log(0,"ERROR: Modification times are same, checksum is different! " + our_filestate.local_filename);
	        		}
	        	}
	        }
	    }
	    /*//find out what files are missing on the other side
	    it = m.entrySet().iterator();
	    while (it.hasNext()) {
	        Entry<String,FileState> pair = it.next();
	        String repo_filename = pair.getKey();
	        FileState our_filestate = pair.getValue();
	        if (!other_state.m.containsKey(repo_filename)) {
	        	//we dont have the file
	        	our_filestate.send=true;
	        	FileState new_filestate = FileState.from_file(repo_filename,null,our_filestate.directory);
	        	assert(new_filestate!=null);
	        	other_state.m.put(repo_filename,new_filestate);
	        	diff=true;
	        }
	    }
	    
	    //check which have been deleted on both sides
	    assert(m.size()==other_state.m.size());*/
	    
	    /*it = other_state.m.entrySet().iterator();
	    while (it.hasNext()) {
	        Entry<String,FileState> pairs = it.next();
	        String repo_filename = pairs.getKey();
        	FileState other_filestate = pairs.getValue();
        	FileState our_filestate = m.get(repo_filename);
        	if (other_filestate.deleted && our_filestate.deleted) {
        		//everything is fine
        		our_filestate.earliest_deleted_time=java.lang.Math.max(other_filestate.earliest_deleted_time,our_filestate.earliest_deleted_time);
        		our_filestate.send=false;
        		other_filestate.send=false;
        	} else if (other_filestate.deleted || our_filestate.deleted) {
        		//conflict resolution here, one side wants the file gone!
        		//lets try to figure out what is going on here
        		File f = new File(repo_filename,repo_path+File.separatorChar+repo_filename);
        		if (!f.exists()) {
        			if (other_filestate.deleted) {
        				//we are missing the file, just have to mark it as deleted
        				our_filestate.deleted=true;
        				our_filestate.earliest_deleted_time=other_filestate.earliest_deleted_time;
        			} else if (our_filestate.deleted) {
        				if (other_filestate.last_modified>our_filestate.earliest_deleted_time) {
        					//get the file from the other side
        					other_filestate.send=true;
        					our_filestate.send=false;
        					our_filestate.deleted=false;
        				}
        			}
        		} else {
        			if (other_filestate.deleted) {
        				if (our_filestate.last_modified>other_filestate.earliest_deleted_time) {
        					//keep our copy of the file
        					our_filestate.send=true;
        					other_filestate.send=false;
        					other_filestate.deleted=false;
        				} else {
        					our_filestate.deleted=true;
        					//need to delete the file!
        				}
        			} else if (our_filestate.deleted) {
        				if (our_filestate.last_modified>our_filestate.earliest_deleted_time) {
        					//keep the file
        					our_filestate.deleted=false;
        					our_filestate.send=true;
        					other_filestate.send=false;
        				}
        			}
        		}
        	}
	    }*/

		//check();
	    return diff;
	    
	    
	}
	
	
}

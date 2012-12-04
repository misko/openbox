import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;


public class FileState implements Serializable {
	String repo_filename;
	String local_filename;
	long last_modified;
	long size;
	String sha1;
	
	
	//some useful flags
	boolean send;
	boolean local_missing;
	boolean repo_missing;
	
	public FileState(String repo_filename, String local_filename) {
		this.repo_filename=repo_filename;
		this.local_filename=local_filename;
		if (local_filename==null) {
			local_missing=true;
		} else {
			try {
				sha1=SHA1.ChecksumFile(local_filename);
				File file = new File(local_filename);
				size = file.length();
				last_modified=file.lastModified();
				
				local_missing=false;
			} catch (IOException e) {
				local_missing=true;
			}
		}
	}
	
	public boolean equals(FileState other) {
		if (!repo_filename.equals(other.repo_filename)) {
			return false;
		} else if (!sha1.equals(other.sha1)) {
			return false;
		} else if (last_modified!=other.last_modified) {
			return false;
		} else if (size!=other.size) {
			return false;
		}
		return true;
	}
	
	public String toString() {
		return "Repo: "+repo_filename + " , Local: "+ local_filename + " , LastModified: " + (new Date(last_modified)) + " , send: "  + (send ? "Y" : "N");
	}
	
}

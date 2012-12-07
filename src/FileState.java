import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;

/**
 * This class describes the state of a file with respect the repository. 
 *
 */
public class FileState implements Serializable {
	/**
	 * The filename with respect to repository root
	 */
	String repo_filename;
	/**
	 * The filename with respect to local file system
	 */
	String local_filename;
	/**
	 * The time in ?? of when the file was last modified
	 */
	long last_modified;
	/**
	 * The size of the file
	 */
	long size;
	/** 
	 * The SHA1 checksum of the file
	 */
	String sha1;
	
	
	//some useful flags
	/**
	 * This flag is set if this file should be sent to the "other" side
	 */
	boolean send;
	/**
	 * This flag is set if the local copy of this file is missing/deleted
	 */
	boolean local_missing;
	/**
	 * This flag is set if the remote copy of this file is missing/deleted
	 */
	boolean remote_missing;
	/** 
	 * Indicates if this file has been deleted
	 */
	boolean deleted;
	/**
	 * Indicates the time of earliest deletion, since we are polling this is not exact
	 */
	long earliest_deleted_time;
	
	public FileState(FileState fs) {
		this.repo_filename=fs.repo_filename;
		this.local_filename=fs.local_filename;
		this.last_modified=fs.last_modified;
		this.size=fs.size;
		this.sha1=fs.sha1;
		this.send=fs.send;
		this.local_missing=fs.local_missing;
		this.remote_missing=fs.remote_missing;
		this.deleted=fs.deleted;
		this.earliest_deleted_time=fs.earliest_deleted_time;
	}
	
	/**
	 * Createa filestate object for the given filename.
	 * SHA1 and adler64 checksums are computed using the local file
	 * @param repo_filename The filename with respect to repository root
	 * @param local_filename The filename with respect to local file system
	 */
	public FileState(String repo_filename, String local_filename) {
		this.repo_filename=repo_filename;
		this.local_filename=local_filename;
		this.deleted=false;
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
	
	/**
	 * Returns true if two FileState objects are equal (in terms of repository_filename, sha1, last_modified and size)
	 * @param other The other FileState object
	 * @return True if the two are equal (with respect to compared fields, described above)
	 */
	@Override
	public boolean equals(Object othero) {
		if (othero.getClass()!=getClass()) {
			return false;
		}
		FileState other = (FileState)othero;
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
		return "Repo: "+repo_filename + " , Local: "+ local_filename + " , LastModified: " + (new Date(last_modified)) + " , send: "  + (send ? "Y" : "N") + " , deleted: " + (deleted ? "Y" : "N");
	}
	
}

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
	/**
	 * True iff this is a directory
	 */
	boolean directory;
	
	public FileState(FileState fs) {
		this.repo_filename=fs.repo_filename;
		this.local_filename=fs.local_filename;
		this.last_modified=fs.last_modified;
		this.size=fs.size;
		this.sha1=fs.sha1;
		this.send=fs.send;
		this.deleted=fs.deleted;
		this.earliest_deleted_time=fs.earliest_deleted_time;
		this.directory=fs.directory;
	}
	
	public static FileState from_file(String repo_filename, String local_filename, boolean directory) {
		FileState fs = new FileState(repo_filename,local_filename,directory);
		File file = new File(local_filename);
		if (!file.exists()) {
			return fs;
		}
		try {
			if (!directory) {
				fs.sha1=SHA1.ChecksumFile(local_filename);
				fs.size = file.length();
			}
			fs.last_modified=file.lastModified();

		} catch (IOException e) {
			e.printStackTrace();
		}
		return fs;
	}
	
	/**
	 * Createa filestate object for the given filename.
	 * SHA1 and adler64 checksums are computed using the local file
	 * @param repo_filename The filename with respect to repository root
	 * @param local_filename The filename with respect to local file system
	 */
	private FileState(String repo_filename, String local_filename, boolean directory) {
		this.repo_filename=repo_filename;
		this.local_filename=local_filename;
		this.deleted=false;
		this.directory=directory;
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
		} else if (directory!=other.directory) {
			return false;
		} else if (!directory && !sha1.equals(other.sha1)) {
			return false;
		} else if (last_modified!=other.last_modified) {
			return false;
		} else if (!directory && size!=other.size) {
			return false;
		}
		return true;
	}
	
	public String toString() {
		return "Repo: "+repo_filename + " , Local: "+ local_filename + " , LastModified: " + (new Date(last_modified)) + " , send: "  + (send ? "Y" : "N") + " , deleted: " + (deleted ? "Y" : "N") + " , directory: " + (directory ? "Y" : "N") + " , sha1: "+ sha1;
	}
	
}

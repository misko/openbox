import java.io.Serializable;


public class Block implements Serializable {
	String repo_filename;
	long offset=0;
	long adler64=0;
	byte data[];
	long size=0;
	boolean use_adler;
	
	//creates a request block for the given file, the given offset and the given size
	public Block(String filename, long offset, boolean user_adler, long adler64, long size) {
		this.size=size;
		this.use_adler=use_adler;
		this.adler64=adler64;
		this.offset=offset;
		this.repo_filename=filename;
		data=null;
	}
	
	public String toString() {
		return repo_filename + " offset: " + offset + " adler: " + (use_adler ? adler64 : "N/A") + " size: " + size;
	}
	
	
}

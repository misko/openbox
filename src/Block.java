import java.io.Serializable;


public class Block implements Serializable {
	String repo_filename;
	long src_offset=0;
	long dest_offset=0;
	long adler64=0;
	byte data[];
	long size=0;
	boolean use_adler;
	
	//creates a request block for the given file, the given offset and the given size
	public Block(String filename, long offset, boolean use_adler, long adler64, long size) {
		this.size=size;
		this.use_adler=use_adler;
		this.adler64=adler64;
		this.src_offset=offset;
		this.repo_filename=filename;
		data=null;
	}
	
	public String toString() {
		return repo_filename + " src_offset: " + src_offset + " dst_offset: "+ dest_offset+ " adler: " + (use_adler ? adler64 : "N/A") + " size: " + size;
	}
	
	public Block copy() {
		Block b = new Block(repo_filename,src_offset,use_adler,adler64,size);
		b.dest_offset=dest_offset;
		b.data=data;
		return b;
		
	}
	
	
}

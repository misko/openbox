import java.io.Serializable;

/**
 * The Block object is used to describe a block of bytes.
 * 
 * The Block object can serve as both a request for bytes and as a carrier of bytes.
 * A Block is first created with enough information to determine how to fill the data bytes
 * this can be by local data (using adler64 matching) or send to remote side to be filled there
 * and returned.
 * 
 * This object is the carrier of actual file data between sides.
 */
public class Block implements Serializable {
	/**
	 * The repository name of the file
	 */
	String repo_filename;
	/**
	 * The offset into the file on the side that will be filling this request
	 */
	long src_offset=0;
	/**
	 * The offset into the file where this data will be written to
	 */
	long dest_offset=0;
	/**
	 * If this is set then do not fill the Block from remote side, instead use the local
	 * data that has this checksum
	 */
	boolean use_adler;
	/**
	 * The adler64 checksum for this Block. This is only useful when use_adler is set true.
	 */
	long adler64=0;
	/**
	 * The data corresponding to this Block. If this is null then this Block is a request
	 * to be filled. Once filled the Block carries the data as described. 
	 */
	byte data[];
	/**
	 * The size of the data in this block
	 */
	long size=0;
	
	//creates a request block for the given file, the given offset and the given size
	/**
	 * Creates a block request
	 * @param repo_filename the filename with respec to repo root
	 * @param src_offset the offset of where in the file the data should be read from (either remote or local
	 * depending on is use_adler is set)
	 * @param use_adler if set then read the data from the remote side, otherwise read from local copy
	 * @param adler64 the adler64 checksum for this Block
	 * @param size the number of bytes in this block
	 */
	public Block(String repo_filename, long src_offset, boolean use_adler, long adler64, long size) {
		this.size=size;
		this.use_adler=use_adler;
		this.adler64=adler64;
		this.src_offset=src_offset;
		this.repo_filename=repo_filename;
		data=null;
	}
	
	public String toString() {
		return repo_filename + " src_offset: " + src_offset + " dst_offset: "+ dest_offset+ " adler: " + (use_adler ? adler64 : "N/A") + " size: " + size;
	}
	
	/**
	 * Returns a copy of this Block. Does not duplicate data object, but links it.
	 * 
	 * @return A copy of the Block
	 */
	public Block copy() {
		Block b = new Block(repo_filename,src_offset,use_adler,adler64,size);
		b.dest_offset=dest_offset;
		b.data=data;
		return b;
		
	}
	
	
}

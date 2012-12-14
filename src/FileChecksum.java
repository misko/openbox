import java.io.Serializable;

/**
 * This is a wrapper class for size , adler64 hash and md5 hash of a block of data.
 *
 * Can be used in hash table, implements hashcode and equals
 *
 *
 */
public class FileChecksum implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6906625089862271966L;
	long size;
	long adler64;
	String md5;
	
	public FileChecksum(long size, long adler64, String md5) {
		this.size=size;
		this.adler64=adler64;
		this.md5=md5;
	}
	
	@Override
	public boolean equals(Object othero) {
		if (othero.getClass()!=getClass()) {
			return false;
		}
		FileChecksum other = (FileChecksum)othero;
		if (adler64==other.adler64 && size==other.size && md5.equals(other.md5)) {
			return true;
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return (int) adler64;
	}
	
	@Override
	public String toString() {
		return "FC\tsize:"+size+",adler64:"+adler64+",md5:"+md5;
	}
	
}

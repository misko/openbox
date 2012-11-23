
public class Block {
	String filename;
	boolean raw_data=false;
	long alder64=0;
	byte data[];
	long size=0;
	
	
	public Block(String filename, long alder64, long size) {
		this.alder64=alder64;
		this.size=size;
		this.raw_data=false;
		this.filename=filename;
		data=null;
	}
	
	public Block(long size) {
		raw_data=true;
		data=null;
		this.size=size;
	}
	
}

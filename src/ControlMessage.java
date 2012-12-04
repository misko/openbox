import java.io.Serializable;


public class ControlMessage implements Serializable {
	//DEFINE TYPES OF MESSAGES
	public static final int OK = 0;
	public static final int STATE = 1<<0;
	public static final int RFCHECK = 1<<1;
	public static final int FCHECK = 1<<2;
	public static final int RBLOCK = 1<<3;
	public static final int BLOCK = 1<<4;
	public static final int ABORT = 1<<5;
	public static final int NOTHING = 1<<6;
	public static final int PULL = 1<<7;
	public static final int CLOSE = 1<<8;
	
	public String repo_filename;

	int type;
	
	public ControlMessage(int type) {
		this.type=type;
	}
	
	public static ControlMessage RFCheck(FileState fs) {
		ControlMessage cm = new ControlMessage(ControlMessage.RFCHECK);
		cm.repo_filename=fs.repo_filename;
		return cm;
	}
	
	public static ControlMessage RBlock(Block b) {
		ControlMessage cm = new ControlMessage(ControlMessage.RBLOCK);
		cm.repo_filename=b.repo_filename;
		return cm;
	}
	
	
}

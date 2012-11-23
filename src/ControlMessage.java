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

	int type;
	
	public ControlMessage(int type) {
		this.type=type;
	}
	
}

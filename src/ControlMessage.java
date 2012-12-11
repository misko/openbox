import java.io.Serializable;

/**
 * A class used for communicating control (as opposed to data).
 * 
 */
public class ControlMessage implements Serializable {
	//Not all message types are currently used
	
	//public static final int OK = 0; 
	//public static final int STATE = 1<<0; //not used
	
	/**
	 * Used to request State object from other side
	 */
	public static final int RSTATE = 1<<1; 
	/** 
	 * Used to request file checksums from other side
	 */
	public static final int RFCHECK = 1<<2;
	//public static final int FCHECK = 1<<3; //not used
	/**
	 * Used to request a Block from other side
	 */
	public static final int RBLOCK = 1<<4;
	//public static final int BLOCK = 1<<5; //not used
	//public static final int ABORT = 1<<6; //not used
	//public static final int NOTHING = 1<<7; //not used
	/**
	 * Used to print this message on receiver side
	 */
	public static final int PULL = 1<<8;
	/**
	 * Used to notify receiver that no further communication to follow
	 */
	public static final int CLOSE = 1<<9;
	/**
	 * Used to notify receiver that they should move out of listen state, and that this side is now listening
	 */
	public static final int YOUR_TURN = 1<<10;
	public static final int TRY_LATER = 1<<11;
	
	public static final int NEW_SESSION = 1<<12;
	public static final int JOIN_SESSION = 1<<13;
	public static final int IN_SESSION = 1<<14;
	
	
	public String repo_filename;
	public String session_id;

	int type;
	
	public ControlMessage(int type) {
		this.type=type;
	}

	
	
	public static ControlMessage new_session() {
		ControlMessage cm = new ControlMessage(ControlMessage.NEW_SESSION);
		return cm;
	}
	
	public static ControlMessage resume_session(String session_id) {
		ControlMessage cm = new ControlMessage(ControlMessage.JOIN_SESSION);
		cm.session_id=session_id;
		return cm;
	}
	
	public static ControlMessage in_session(String session_id) {
		ControlMessage cm = new ControlMessage(ControlMessage.IN_SESSION);
		cm.session_id=session_id;
		return cm;
	}
	
	/**
	 * Returns a ControlMessage that when sent requests file checksums for the given file to be sent be the receiver.
	 * @return A ControlMessage with RFCHECK set
	 */
	public static ControlMessage rfcheck(FileState fs) {
		ControlMessage cm = new ControlMessage(ControlMessage.RFCHECK);
		cm.repo_filename=fs.repo_filename;
		return cm;
	}

	/**
	 * Returns a ControlMessage that when sent requests a block object to be filled with data remotely and returned by the receiver.
	 * @return A ControlMessage with RBLOCK set
	 */
	public static ControlMessage rblock(Block b) {
		ControlMessage cm = new ControlMessage(ControlMessage.RBLOCK);
		cm.repo_filename=b.repo_filename;
		return cm;
	}

	/**
	 * Returns a ControlMessage that when sent does nothing other then prints on the receiver side.
	 * @return A ControlMessage with PULL set
	 */
	public static ControlMessage pull() {
		ControlMessage cm = new ControlMessage(ControlMessage.PULL);
		return cm;
	}
	
	/**
	 * Returns a ControlMessage that when sent requests a State object to be sent be receiver.
	 * @return A ControlMessage with RSTATE set
	 */
	public static ControlMessage rstate() {
		ControlMessage cm = new ControlMessage(ControlMessage.RSTATE);
		return cm;
	}

	/**
	 * Returns a ControlMessage that when sent requests tells the receiver that they are now in the active mode and this side is listening.
	 * @return A ControlMessage with YOUR_TURN set
	 */
	public static ControlMessage yourturn() {
		ControlMessage cm = new ControlMessage(ControlMessage.YOUR_TURN);
		return cm;
	}

	/**
	 * Returns a ControlMessage that when sent tells the other side not to expect any more messages from this side.
	 * @return A ControlMessage with CLOSE set
	 */
	public static ControlMessage close() {
		ControlMessage cm = new ControlMessage(ControlMessage.CLOSE);
		return cm;
	}
	
	public static ControlMessage try_later() {
		ControlMessage cm = new ControlMessage(ControlMessage.TRY_LATER);
		return cm;
	}
}

import java.util.TimerTask;


public class StatusTask extends TimerTask {

	SyncAgent sa; 
	
	public StatusTask(SyncAgent sa) {
		this.sa=sa;
	}
	
	@Override
	public void run() {
		sa.status();
	}
	
}

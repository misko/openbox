import java.io.Serializable;


public class FileChecksums implements Serializable {
	String repo_filename;
	long checksums[][];
	
	public FileChecksums(String repo_filename, long checksums[][]) {
		this.repo_filename=repo_filename;
		this.checksums=checksums;
	}
	
	public String toString() {
		String r=repo_filename+"\n";
		for (int i=0; i<checksums.length; i++) {
			for (int j=0; j<checksums[i].length; j++) {
				r+=checksums[i][j]+"\t";
			}
			r+="\n";
		}
		return r;
	}
}

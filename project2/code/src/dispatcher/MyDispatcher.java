package dispatcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class MyDispatcher {
	
	
	private static void copyFileUsingFileChannels(File source, File dest) throws IOException {   
    	FileInputStream fis = null;
    	FileOutputStream fos = null;
        FileChannel inputChannel = null;    
        FileChannel outputChannel = null;    
	    try {
	    	fis = new FileInputStream(source);
	        inputChannel = fis.getChannel();
	        fos = new FileOutputStream(dest);
	        outputChannel = fos.getChannel();
	        outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
	    } finally {
	        inputChannel.close();
	        outputChannel.close();
	        fis.close();
	        fos.close();
	    }
    }

	public static void main(String[] args) {
		String[] servers = new String[] {
			"S1", "S2", "S3"
		};
		String[] clients = new String[] {
			"C1", "C2", "C3", "C4", "C5"
		};
		
		try {
			String parentDir = new File("dispatcher.jar").getAbsoluteFile().getParent().toString();
			// servers
			File src = new File(parentDir + "/server.jar");
			for (String s : servers) {
				File dest = new File(parentDir + "/" + s + "/server.jar");
				if (dest.exists()) {
					dest.delete();
				}
				copyFileUsingFileChannels(src, dest);
			}
			// clients
			src = new File(parentDir + "/client.jar");
			for (String s : clients) {
				File dest = new File(parentDir + "/" + s + "/client.jar");
				if (dest.exists()) {
					dest.delete();
				}
				copyFileUsingFileChannels(src, dest);
			}
			System.out.println("Dispatch successfully.");
    	} catch (IOException e) {
            e.printStackTrace();
        }

	}

}

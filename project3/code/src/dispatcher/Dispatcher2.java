package dispatcher;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import global.Global;

public class Dispatcher2 {
	
	private static void setServers(String parentDir) {
		String folderDir = parentDir + "/" + Global.SERVERNAME + "s";
    	String srcDir = parentDir + "/" + Global.SERVERSRC;
		File src = new File(srcDir);
		for (int i = 0; i < Global.SERVERNUM; i++) {
			String nodeDir = folderDir + "/" + Global.SERVERPREFIX + i;
			String destDir = nodeDir + "/" + Global.SERVERSRC;
			File dest = new File(destDir);
			if (dest.exists()) {
				dest.delete();
			}
			try {
				Dispatcher.copyFileUsingFileChannels(src, dest);
    		} catch (IOException e) {
                e.printStackTrace();
            }
		}
	}
	
	private static void setClients(String parentDir) {
		String folderDir = parentDir + "/" + Global.CLIENTNAME + "s";
    	String srcDir = parentDir + "/" + Global.CLIENTSRC;
		File src = new File(srcDir);
		for (int i = 0; i < Global.CLIENTNUM; i++) {
			String nodeDir = folderDir + "/" + Global.CLIENTPREFIX + i;
			String destDir = nodeDir + "/" + Global.CLIENTSRC;
			File dest = new File(destDir);
			if (dest.exists()) {
				dest.delete();
			}
			try {
				Dispatcher.copyFileUsingFileChannels(src, dest);
    		} catch (IOException e) {
                e.printStackTrace();
            }
		}
	}

	public static void main(String[] args) {
		
		// set nodes
		System.out.println("Start dispatching at " + new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()) + ".");
		String parentDir = new File("dispatcher.jar").getAbsoluteFile().getParent().toString();
		setServers(parentDir);
		setClients(parentDir);
		
		System.out.println("Dispatch successfully.");

	}

}

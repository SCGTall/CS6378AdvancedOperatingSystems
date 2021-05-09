package dispatcher;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

import global.Global;

public class Dispatcher {

	public static void copyFileUsingFileChannels(File source, File destination) throws IOException {
		
		if (!source.exists()) {
			return;
		}
    	FileInputStream fis = null;
    	FileOutputStream fos = null;
        FileChannel inputChannel = null;    
        FileChannel outputChannel = null;    
	    try {
	    	fis = new FileInputStream(source);
	        inputChannel = fis.getChannel();
	        fos = new FileOutputStream(destination);
	        outputChannel = fos.getChannel();
	        outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
	    }  finally {
	    	try {
	    		inputChannel.close();
		        outputChannel.close();
		        fis.close();
		        fos.close();
    		} catch (RuntimeException e) {
                e.printStackTrace();
            }
	    }
    }
	
	private static void deleteAll(File file) {  // delete all in a folder recursively

        if (file.isFile() || file.list().length == 0) {
            file.delete();
        } else {
            for (File f : file.listFiles()) {
                deleteAll(f);
            }
            file.delete();
        }
    }
	
	private static void setServers(String parentDir) {
		
		String folderDir = parentDir + "/" + Global.SERVERNAME + "s";
		File folder = new File(folderDir);
    	if (folder.exists()) {
    		deleteAll(folder);
    	}
    	folder.mkdir();
    	String srcDir = parentDir + "/" + Global.SERVERSRC;
		File src = new File(srcDir);
		for (int i = 0; i < serverNum; i++) {
			String nodeDir = folderDir + "/" + Global.SERVERPREFIX + i;
			File node = new File(nodeDir);
			node.mkdir();
			String destDir = nodeDir + "/" + Global.SERVERSRC;
			File dest = new File(destDir);
			try {
				copyFileUsingFileChannels(src, dest);
    		} catch (IOException e) {
                e.printStackTrace();
            }
			// set files
			for (int j = 0; j < fileNum; j++) {
				String fileDir = nodeDir + "/" + Global.FILEPREFIX + j + Global.FILESUFFIX;
				File file = null;
        		BufferedWriter writer = null;
        		try {
        			file = new File(fileDir);
    				file.createNewFile();
        			writer = new BufferedWriter(new FileWriter(fileDir));
	        		writer.write(Global.EMPTYSTRING);
	        		writer.newLine();
	        		writer.flush();
	        		writer.close();
        		} catch (IOException e) {
                    e.printStackTrace();
                } finally {
                	try {
                		writer.close();
                	} catch (IOException e) {
	                    e.printStackTrace();
	                }
                }
        		
			}
		}
	}
	
	private static void setClients(String parentDir) {
		
		String folderDir = parentDir + "/" + Global.CLIENTNAME + "s";
		File folder = new File(folderDir);
    	if (folder.exists()) {
    		deleteAll(folder);
    	}
    	folder.mkdir();
    	String srcDir = parentDir + "/" + Global.CLIENTSRC;
		File src = new File(srcDir);
		for (int i = 0; i < clientNum; i++) {
			String nodeDir = folderDir + "/" + Global.CLIENTPREFIX + i;
			File node = new File(nodeDir);
			node.mkdir();
			String destDir = nodeDir + "/" + Global.CLIENTSRC;
			File dest = new File(destDir);
			try {
				copyFileUsingFileChannels(src, dest);
    		} catch (IOException e) {
                e.printStackTrace();
            }
		}
	}
	
	private static boolean isPositiveInteger(String s) {
		try {
			int v = Integer.parseInt(s);
			return v > 0;
		} catch (NumberFormatException e) {
			return false;
		}
	}
	
	private static int serverNum = Global.SERVERNUM;
	private static int clientNum = Global.CLIENTNUM;
	private static int fileNum = Global.FILENUM;

	public static void main(String[] args) {
		
		// init
		if (args.length == 0) {
			;
		} else if (args.length == 3 && isPositiveInteger(args[0]) && isPositiveInteger(args[1]) && isPositiveInteger(args[2])) {
			serverNum = Integer.parseInt(args[0]);
			clientNum = Integer.parseInt(args[1]);
			fileNum = Integer.parseInt(args[2]);
		} else {
			System.out.println("Wrong input! Try like: jar dispatcher.jar <server num> <client num> <file num>");
			System.exit(1);
		}
		System.out.println("Server num: " + serverNum);
		System.out.println("Client num: " + clientNum);
		System.out.println("File num: " + fileNum);
		// set nodes
		System.out.println("Start dispatching at " + new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()) + ".");
		String parentDir = new File("dispatcher.jar").getAbsoluteFile().getParent().toString();
		setServers(parentDir);
		setClients(parentDir);
		System.out.println("Dispatch successfully.");

	}

}

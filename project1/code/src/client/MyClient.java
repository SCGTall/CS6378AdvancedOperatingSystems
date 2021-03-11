package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
 
public class MyClient extends Socket {
 
    private static final String SERVER_IP = "10.176.69.52";
    private static final int SERVER_PORT = 8899;
    private static final String TARGET_DIR = "D1";
    private static final int BUFFER_SIZE = 256;
    private enum CMD {
		CommonMSG(0),
		MakeNewDir(1),
		MakeNewFile(2),
		StartReadingFile(3),
		ExitAfterAllDone(4);
		
		private int value;

	    private CMD(int value) {
	        this.value = value;
	    }

	    public int getValue() {
	        return value;
	    }
	}
    
    /*
	 *  C2
	 *  format:
	 *  CMD + len + msg
	 *  int + int + String
	 *  send negative number which indicate certain meanful command
	 */
    public static void main(String[] args) {
        try {
        	// connect to server
        	Socket s = new Socket(SERVER_IP, SERVER_PORT);
        	InputStream in  = s.getInputStream();
    		DataInputStream din = new DataInputStream(in);
        	OutputStream out = s.getOutputStream();
    		DataOutputStream dout = new DataOutputStream(out);
    		byte buf[];
    		int cmd = -1;
    		int len;
    		// say hello to server
    		String hello = "Hello server.";
    		dout.writeInt(CMD.CommonMSG.getValue());
    		dout.flush();
    		dout.writeInt(hello.length());
    		dout.flush();
    		dout.write(hello.getBytes());
    		dout.flush();
    		System.out.println("Say hello to server.");
    		// create D1copy
    		File folder = new File(TARGET_DIR);
    		File[] files = folder.listFiles();
    		String copied = TARGET_DIR + "copy";
    		dout.writeInt(CMD.MakeNewDir.getValue());
    		dout.flush();
    		dout.writeInt(copied.length());
    		dout.flush();
    		dout.write(copied.getBytes());
    		dout.flush();
    		System.out.println("Send new directory name: " + copied + ".");
    		System.out.println("To server: Create " + copied + ".");
    		// wait for server msg for create new directory
    		cmd = din.readInt();
    		while(cmd == -1) {
    			Thread.sleep(100);
    		}
			if (cmd != CMD.CommonMSG.getValue()) {
    			System.out.println("Unvalid cmd received: " + cmd);
    		} else {
    			len = din.readInt();
        		buf = new byte[len];
        		din.read(buf);
        		System.out.println("From server: " + new String(buf));
    		}
			cmd = -1;
    		for (File file : files) {
    			// send file info
    			dout.writeInt(CMD.MakeNewFile.getValue());
        		dout.flush();
    			dout.writeInt(file.getName().length());
    			dout.flush();
    			dout.write((file.getName().getBytes()));
    			dout.flush();
    			System.out.println("Send new file name: " + file.getName() + ".");
    			System.out.println("To server: Create " + file.getName() + ".");
    			// wait for server msg for create new file
    			cmd = din.readInt();
    			while(cmd == -1) {
        			Thread.sleep(100);
        		}
    			if (cmd != CMD.CommonMSG.getValue()) {
        			System.out.println("Unvalid cmd received: " + cmd);
        		} else {
        			len = din.readInt();
            		buf = new byte[len];
            		din.read(buf);
            		System.out.println("From server: " + new String(buf));
        		}
    			cmd = -1;
    			// send file content
    			System.out.println("Start sending content of " + file.getName() + ".");
    			dout.writeInt(CMD.StartReadingFile.getValue());
        		dout.flush();
    			dout.writeLong((long) file.length());  // assume a file length is long
    			dout.flush();
    			DataInputStream fin = new DataInputStream(new FileInputStream(file));
    			while (fin != null) {
    				buf = new byte[BUFFER_SIZE];
    				len = fin.read(buf);
    				if (len == -1) {
    					break;
    				}
    				
    				dout.write(buf, 0, len);
    				dout.flush();
    			}
    			System.out.println("End sending content of " + file.getName() + ".");
    			fin.close();
    			System.out.println("To server: Finish sending " + file.getName() + ".");
    			// wait for server msg for finish saving file
    			cmd = din.readInt();
    			while(cmd == -1) {
        			Thread.sleep(100);
        		}
    			if (cmd != CMD.CommonMSG.getValue()) {
        			System.out.println("Unvalid cmd received: " + cmd);
        		} else {
        			len = din.readInt();
            		buf = new byte[len];
            		din.read(buf);
            		System.out.println("From server: " + new String(buf));
        		}
    			cmd = -1;
    		}
    		dout.writeInt(CMD.ExitAfterAllDone.getValue());
    		dout.flush();
    		System.out.println("To server: Finish sending all files.");
    		System.out.println("Finish sending all files in directory and exit now.");
    		s.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
			e.printStackTrace();
		}
    		
    	
    }
}
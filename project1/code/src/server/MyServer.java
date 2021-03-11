package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public class MyServer {
	
	private static final int SERVER_PORT = 8899;
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
	 *  C1
	 *  format:
	 *  CMD + len + msg
	 *  int + int + String
	 *  send negative number which indicate certain meanful command
	 */
    public static void main(String[] args) {
        try {
        	ServerSocket server = new ServerSocket(SERVER_PORT);
        	// establish connection
    		System.out.println("listening...");
    		Socket s = server.accept(); // block and wait
    		System.out.println("Client connected.");
    		InputStream in  = s.getInputStream();
    		DataInputStream din = new DataInputStream(in);
        	OutputStream out = s.getOutputStream();
    		DataOutputStream dout = new DataOutputStream(out);
    		byte buf[];
    		int cmd = -1;
    		int len;
    		boolean flag = true;
    		String fileName = "";
    		String folderName = "";
    		File file;
    		String msg;
    		while (flag) {
    			cmd = din.readInt();
    			//System.out.println("CMD:" + cmd);
    			// use cmd to decide coming action
    			switch (cmd) {
	    			case 0:  // CommonMSG
	    				len = din.readInt();
	    				buf = new byte[len];
		        		din.read(buf);
		        		System.out.println("From client: " + new String(buf));
	    				break;
	    			case 1:  // MakeNewDir
	    				// create new directory
	    				len = din.readInt();
	    				buf = new byte[len];
		        		din.read(buf);
		        		folderName = new String(buf);
		        		System.out.println("From client: Create " + folderName + ".");
		        		File folder = new File(folderName);
		        		if (!folder.exists() && !folder.isDirectory()) {
		        			folder.mkdir();
		        			System.out.println("Create new directory: " + folderName);
		        		} else {
		        			System.out.println("Directory existed.");
		        		}
		        		// report success
		        		msg = folderName + " has been created successfully,";
	    				dout.writeInt(CMD.CommonMSG.getValue());
	    				dout.flush();
	    				dout.writeInt(msg.length());
	    				dout.flush();
	    				dout.write(msg.getBytes());
	    				dout.flush();
		        		System.out.println("To client: " + msg);
	    				break;
	    			case 2:  // MakeNewFile
	    				// create new file
	    				len = din.readInt();
	    				buf = new byte[len];
		        		din.read(buf);
		        		fileName = new String(buf);
		        		System.out.println("From client: Create " + fileName + ".");
		        		file = new File(folderName + "/" + fileName);
		        		if (file.exists()) {
		        			file.delete();
		        		}
		        		file.createNewFile();
		        		System.out.println("Create file: " + fileName);
		        		// report success
		        		msg = fileName + " has been created successfully,";
	    				dout.writeInt(CMD.CommonMSG.getValue());
	    				dout.flush();
	    				dout.writeInt(msg.length());
	    				dout.flush();
	    				dout.write(msg.getBytes());
	    				dout.flush();
	    				System.out.println("To client: " + msg);
	    				break;
	    			case 3:  // StartReadingFile
	    				// append content to last file
	    				System.out.println("From client: Sending content of " + folderName + ".");
	    				long left = din.readLong();
	    				file = new File(folderName + "/" + fileName);
	    				FileOutputStream fout = new FileOutputStream(file);
	    				System.out.println("Writing " + fileName + ".");
	    				while (left > 0) {
	    					if (left >= BUFFER_SIZE) {
	    						buf = new byte[BUFFER_SIZE];
	    						din.readFully(buf);
	    						fout.write(buf, 0, buf.length);
	    					} else {
	    						buf = new byte[(int) left];
	    						din.readFully(buf);
	    						fout.write(buf, 0, buf.length);
	    						break;
	    					}
	    					left -= BUFFER_SIZE;
	    				}
	    				// report after receiving client's msg
	    				msg = fileName + " has been saved successfully,";
	    				dout.writeInt(CMD.CommonMSG.getValue());
	    				dout.flush();
	    				dout.writeInt(msg.length());
	    				dout.flush();
	    				dout.write(msg.getBytes());
	    				dout.flush();
	    				System.out.println("To client: " + msg);
	    				break;
	    			case 4:  // ExitAfterAllDone
	    				flag = false;  // use to break while true
	    				System.out.println("Finish receiving all files and exit now.");
	    				break;
	    			default:
	    				System.out.print("Unvalid cmd received: " + cmd);
	    				break;
    			}
    			cmd = -1;
    		}
        	
        	s.close();
        	din.close();
        	server.close();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
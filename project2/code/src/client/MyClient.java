package client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
 
public class MyClient extends Socket {
    
	// finals
    private static final int ROUND_TIMES = 20;
    private static final boolean SHOW_LOG = false;
    private enum ACTION {
		Read(0),
		Write(1);
		
		private int value;

	    private ACTION(int value) {
	        this.value = value;
	    }
	    
	    public int getValue() {
	        return value;
	    }
	    
	    public boolean equals(ACTION act) {
	    	return (value == act.getValue());
	    }
	}
    
    // globals
	private static String[] serverList = new String[] {
    		"10.176.69.52",
    		"10.176.69.53",
    		"10.176.69.54",
    };  // ip for servers
	private static int[] portList = new int[] {
			8890, 8891, 8892
	};  // ports for servers
	private enum CMD {
		Message(0),
		Enquiry(1),
		Read(2),
		Write(3),
		Next(4),
		Exit(5);
		
		private int value;

	    private CMD(int value) {
	        this.value = value;
	    }
	    
	    public int getValue() {
	        return value;
	    }
	}
	
    private static ArrayList<String> fileList;  // file list in server
    private static int clientID;
    private static Random random;  // set seed to client ID
    private static Socket[] socketList = new Socket[serverList.length];
    
    private static class Input {
    	private int cmd;
    	private String[] messages;
    	
    	public Input(int cmd, String[] msgs) {
    		this.cmd = cmd;
    		this.messages = msgs;
    	}

    	public int getCMD() {
    		return cmd;
    	}
    	
    	public String getMSG(int index) {
    		return messages[index];
    	}
    }
    
    private static final int BUFFER_SIZE = 64;
    
    private static void toSocket(Socket s, CMD cmd, String to, int fi, String msg) throws IOException {
    	String combined = "|" + cmd.getValue() + "|C" + clientID + "|" + to + "|" + fi + "|" + msg + "|";
    	String format = "%-" + BUFFER_SIZE + "s";
    	String padded = String.format(format, combined); // 64 byte
		OutputStream dout = s.getOutputStream();
		dout.write(padded.getBytes());
		dout.flush();
		if (SHOW_LOG) {
			System.out.println("Output:(" + padded + ")");
		}
		return;
	}
    
    
    private static Input fromSocket(Socket s) {
    	byte buf[] = new byte[BUFFER_SIZE];
		try {
			InputStream din = s.getInputStream();
			din.read(buf);
		} catch (IOException e) {
			return new Input(5, null);
		}
		String padded = new String(buf);
		if (SHOW_LOG) {
			System.out.println("Input :(" + padded + ")");
		}
		// decode messages
		String[] splited = padded.split("\\|", 0);
		int cmd = Integer.parseInt(splited[1]);
		String[] msgs = splited[5].split("&");
		return new Input(cmd, msgs);
    }
    

	private static ArrayList<String> enquiry(int serverID) {
		ArrayList<String> files = new ArrayList<String>();
		try {
			Socket s = socketList[serverID];
			System.out.println("[Enquiry]");
    		int cmd;
    		// enquire file list from server
    		String enquiryWord = "Enquiry list of hosted files to client " + clientID;
    		toSocket(s, CMD.Enquiry, "S" + serverID,  -1, enquiryWord);
    		boolean flag = true;
    		while (flag) {
    			Input inp = fromSocket(s);
    			cmd = inp.getCMD();
    			if (cmd == CMD.Next.getValue()) {
    				flag = false;
    			} else if (cmd == CMD.Enquiry.getValue()) {
    				files.add(inp.getMSG(0));
    			} else {
    				flag = false;
    				System.out.println("Wrong CMD code happen in Enquiry: " + cmd);
    			}
    		}
    		System.out.println(enquiryWord);
    		System.out.println("Hosted files:");
    		for (String fname : files) {
    			System.out.println(fname);
    		}
        } catch (IOException e) {
            e.printStackTrace();
        }
		return files;
	}
    

	private static void read(int serverID, String fileName, String timestamp) {
		try {
			Socket s = socketList[serverID];
    		// send message to server
    		toSocket(s, CMD.Read, "S" + serverID, fileName2Index(fileName), timestamp);
    		// receive last line of target file
    		Input inp = fromSocket(s);
    		if (inp.getCMD() == CMD.Next.getValue()) {
    			System.out.println("Read last line from " + fileName + ": " + inp.getMSG(0));
    		} else {
    			System.out.println("Wrong CMD code happen in Read! CMD: " + inp.getCMD());
    		}
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
    
    
    private static void write(int serverID, String fileName, String timestamp) {
    	try {
    		Socket s = socketList[serverID];
    		// send message to server
			String newLine = "" + clientID + ", " + timestamp;
			String msg = timestamp + "&" + newLine;
    		toSocket(s, CMD.Write, "S" + serverID, fileName2Index(fileName), msg);
    		System.out.println("Write new line to " + fileName + ": " + newLine);
    		// wait for server
    		Input inp = fromSocket(s);
    		if (inp.getCMD() != CMD.Next.getValue()) {
    			System.out.println("Wrong CMD code happen in Write! CMD: " + inp.getCMD());
    		}
        } catch (IOException e) {
            e.printStackTrace();
        }
		
	}
    
    
    private static void beforeCloseSocket(int serverID) {
    	try {
    		Socket s = socketList[serverID];
    		toSocket(s, CMD.Exit, "S" + serverID, -1, "");
    		s.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    
    private static int fileName2Index(String fileName) {
		for (int i = 0; i < fileList.size(); i++) {
			if (fileList.get(i).equals(fileName)) {
				return i;
			}
		}
		return -1;
	}
	

	private static int getRandomSocketIndex() {
		int serverID = random.nextInt(serverList.length);
		System.out.print("Client " + clientID + " to server " + serverID + ":");
		return serverID;
	}
	
	
	private static String getRandomFile() {
		if (fileList.size() == 0) {
			return "";
		} else {
			return fileList.get(random.nextInt(fileList.size()));
		}
	}
	
	
	private static ACTION getRandomAction() {
		return ACTION.values()[random.nextInt(ACTION.values().length)];
	}
	
	
	private static String getLocalTimestamp() {
		return new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
	}
	
	
	public static void main(String[] args) {
    	// init
    	if(args.length != 1) {
			System.out.println("Wrong input! Try like: jar client.jar <client id>");
			System.exit(1);
		}
    	clientID = Integer.parseInt(args[0]);
    	random = new Random();
		random.setSeed(clientID);
		// connect to server
		try {
			for (int i = 0; i < serverList.length; i++) {
				Socket s = new Socket(serverList[i], portList[i]);
				socketList[i] = s;
				toSocket(s, CMD.Message, "S" + i, -1, Integer.toString(clientID));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		// Enquire hosted file list
    	fileList = enquiry(getRandomSocketIndex());
    	System.out.println("Begin random actions.");
    	// read or write
    	for (int r = 0; r < ROUND_TIMES; r++) {  // terminate after loop enough times
    		try {
				Thread.sleep(random.nextInt(300) + 300);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    		ACTION act = getRandomAction();
    		if (act.equals(ACTION.Read)) {
    			read(getRandomSocketIndex(), getRandomFile(), getLocalTimestamp());
    		} else if (act.equals(ACTION.Write)) {
    			write(getRandomSocketIndex(), getRandomFile(), getLocalTimestamp());
    		}
    	}
    	for (int i = 0; i < serverList.length; i++) {
    		beforeCloseSocket(i);
    	}
    	System.out.println("Finish " + ROUND_TIMES + " rounds and exit.");
    	System.exit(0);
    }
	
}
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
    	private ArrayList<String> messages;
    	
    	public Input(int cmd, ArrayList<String> msgs) {
    		this.cmd = cmd;
    		this.messages = msgs;
    	}

    	public int getCMD() {
    		return cmd;
    	}
    	
    	public String getMSG(int index) {
    		return messages.get(index);
    	}
    }
    
    private static final int BUFFER_SIZE = 64;
    
    private static void toSocket(Socket s, CMD cmd, String msg) throws IOException {
    	String combined = "|" + Integer.toString(cmd.getValue()) + "|" + msg + "|";
    	String format = "%-" + Integer.toString(BUFFER_SIZE) + "s";
    	String padded = String.format(format, combined); // 64 byte
		OutputStream dout = s.getOutputStream();
		dout.write(padded.getBytes());
		dout.flush();
		//System.out.println("Output:(" + padded + ")");
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
		int cmd = padded.charAt(1) - '0';
		//System.out.println("Input: (" + padded + ")");
		ArrayList<String> msgs = new ArrayList<String>();
		int start = 3;
		for (int i = start; i < BUFFER_SIZE; i++) {
			if (padded.charAt(i) == '|') {  // end of all messages
				String tmp = padded.substring(start, i);
				msgs.add(tmp);
				break;
			}
			if (padded.charAt(i) == '&')  {  // seperator of messages
				String tmp = padded.substring(start, i);
				msgs.add(tmp);
				start = i + 1;
			}
		}
		return new Input(cmd, msgs);
    }
    

	private static ArrayList<String> enquiry(Socket s) {
		ArrayList<String> files = new ArrayList<String>();
		try {
			System.out.println("[Enquiry]");
    		int cmd;
    		// enquire file list from server
    		String enquiryWord = "Enquiry list of hosted files to client " + Integer.toString(clientID);
    		toSocket(s, CMD.Enquiry, enquiryWord);
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
    				System.out.println("Wrong CMD code happen in Enquiry: " + Integer.toString(cmd));
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
    

	private static void read(Socket s, String fileName, String timestamp) {
		try {
    		// send message to server
			String msg = fileName + "&" + timestamp;
    		toSocket(s, CMD.Read, msg);
    		// receive last line of target file
    		Input inp = fromSocket(s);
    		if (inp.getCMD() == CMD.Read.getValue()) {
    			System.out.println("Read last line from " + fileName + ": " + inp.getMSG(0));
    		} else {
    			System.out.println("Wrong CMD code happen in Read! CMD: " + Integer.toString(inp.getCMD()));
    		}
    		// wait for server
    		Input inp2 = fromSocket(s);
    		if (inp2.getCMD() != CMD.Next.getValue()) {
    			System.out.println("Wrong CMD code happen in Read! CMD: " + Integer.toString(inp2.getCMD()));
    		}
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
    
    
    private static void write(Socket s, String fileName, String timestamp) {
    	try {
    		// send message to server
			String newLine = Integer.toString(clientID) + ", " + timestamp;
			String msg = fileName + "&" + timestamp + "&" + newLine;
    		toSocket(s, CMD.Write, msg);
    		System.out.println("Write new line to " + fileName + ": " + newLine);
    		// wait for server
    		Input inp = fromSocket(s);
    		if (inp.getCMD() != CMD.Next.getValue()) {
    			System.out.println("Wrong CMD code happen in Write! CMD: " + Integer.toString(inp.getCMD()));
    		}
        } catch (IOException e) {
            e.printStackTrace();
        }
		
	}
    
    
    private static void beforeCloseSocket(Socket s) {
    	try {
    		Socket socket = s;
    		toSocket(s, CMD.Exit, "");
    		socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
	

	private static Socket getRandomSocket() {
		int serverID = random.nextInt(serverList.length);
		System.out.print("To server " + Integer.toString(serverID) + ":");
		return socketList[serverID];
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
		//random.setSeed(clientID);
		// connect to server
		try {
			for (int i = 0; i < serverList.length; i++) {
				Socket s = new Socket(serverList[i], portList[i]);
				socketList[i] = s;
				toSocket(s, CMD.Message, Integer.toString(clientID));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		// Enquire hosted file list
    	fileList = enquiry(getRandomSocket());
    	System.out.println("Begin random actions.");
    	// read or write
    	for (int r = 0; r < ROUND_TIMES; r++) {  // terminate after loop enough times
    		ACTION act = getRandomAction();
    		if (act.equals(ACTION.Read)) {
    			read(getRandomSocket(), getRandomFile(), getLocalTimestamp());
    		} else if (act.equals(ACTION.Write)) {
    			write(getRandomSocket(), getRandomFile(), getLocalTimestamp());
    		}
    	}
    	for (Socket s : socketList) {
    		beforeCloseSocket(s);
    	}
    	System.out.println("Finish " + Integer.toString(ROUND_TIMES) + " rounds and exit.");
    	System.exit(0);
    }
	
}
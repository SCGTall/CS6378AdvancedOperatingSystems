package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
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
	private static final int SERVER_PORT = 8899;
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
	private enum CMD {
		Message(0),
		Enquiry(1),
		Read(2),
		Write(3),
		Next(4),
		Exit(5),
		Request(6),
		Release(7),
		LastElem(8);
		
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
    private static MySocketObject[] socketList = new MySocketObject[serverList.length];
    
    private static class Input {
    	private int cmd;
    	private String message;
    	
    	public Input(int cmd, int len, String msg) {
    		this.cmd = cmd;
    		this.message = msg;
    	}

    	public int getCMD() {
    		return cmd;
    	}
    	
    	public String getMSG() {
    		return message;
    	}
    }
    
    private static class MySocketObject {
    	private Socket socket;
    	private DataInputStream din;
    	private DataOutputStream dout;
    	
    	public MySocketObject(Socket s) throws IOException {
    		this.socket = s;
    		InputStream in  = s.getInputStream();
    		this.din = new DataInputStream(in);
        	OutputStream out = s.getOutputStream();
    		this.dout = new DataOutputStream(out);
    	}
    	
    	public Socket getSocket() {
    		return this.socket;
    	}
    	
    	public DataInputStream getDataInputStream() {
    		return this.din;
    	}
    	
    	public DataOutputStream getDataOutputStream() {
    		return this.dout;
    	}
    }
	
    
    private static void toSocket(DataOutputStream dout, CMD cmd, String s) throws IOException {
    	dout.writeInt(cmd.getValue());
    	dout.flush();
		dout.writeInt(s.length());
		dout.flush();
		dout.write(s.getBytes());
		dout.flush();
		return;
	}
    
    
    private static Input fromSocket(DataInputStream din) throws IOException {
    	int cmd = din.readInt();
    	if (cmd < 0 && cmd > CMD.LastElem.getValue()) {
    		cmd = din.readInt();
    	}
		int len = din.readInt();
		byte buf[] = new byte[len];
		din.read(buf);
		Input inp  = new Input(cmd, len, new String(buf));
		return inp;
    }
    

	private static ArrayList<String> enquiry(MySocketObject s) {
		ArrayList<String> files = new ArrayList<String>();
		try {
			DataInputStream din = s.getDataInputStream();
			DataOutputStream dout = s.getDataOutputStream();
    		int cmd;
    		// enquire file list from server
    		String enquiryWord = "Enquiry list of hosted files to client " + Integer.toString(clientID);
    		toSocket(dout, CMD.Enquiry, enquiryWord);
    		boolean flag = true;
    		while (flag) {
    			Input inp = fromSocket(din);
    			cmd = inp.getCMD();
    			if (cmd == CMD.Exit.getValue()) {
    				flag = false;
    			} else if (cmd == CMD.Enquiry.getValue()) {  // cmd + len + string
    				files.add(inp.getMSG());
    			} else {
    				flag = false;
    				System.out.println("Wrong CMD code happen in Enquiry!");
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
    

	private static void read(MySocketObject s, String fileName) {
		try {
			DataInputStream din = s.getDataInputStream();
			DataOutputStream dout = s.getDataOutputStream();
    		// send file name to server
    		toSocket(dout, CMD.Read, fileName);
    		// receive last line of target file
    		Input inp = fromSocket(din);
    		if (inp.getCMD() == CMD.Read.getValue()) {
    			System.out.println("Last line from " + fileName + ": " + inp.getMSG());
    		} else {
    			System.out.println("Wrong CMD code happen in Read! CMD: " + Integer.toString(inp.getCMD()));
    		}
    		// wait for server
    		Input inp2 = fromSocket(din);
    		if (inp2.getCMD() != CMD.Exit.getValue()) {
    			System.out.println("Wrong CMD code happen in Read! CMD: " + Integer.toString(inp2.getCMD()));
    		}
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
    
    
    private static void write(MySocketObject s, String fileName, String timestamp) {
    	try {
    		DataInputStream din = s.getDataInputStream();
			DataOutputStream dout = s.getDataOutputStream();
    		// send file name to server
    		toSocket(dout, CMD.Write, fileName);
    		// wait for server
    		Input inp = fromSocket(din);
    		if (inp.getCMD() != CMD.Next.getValue()) {
    			System.out.println("Wrong CMD code happen in Write! CMD: " + Integer.toString(inp.getCMD()));
    		}
    		// send new line to server
    		String newLine = Integer.toString(clientID) + ", " + timestamp;
    		toSocket(dout, CMD.Write, newLine);
    		System.out.println("Write to " + fileName + ": " + newLine);
    		// wait for server
    		Input inp2 = fromSocket(din);
    		if (inp2.getCMD() != CMD.Exit.getValue()) {
    			System.out.println("Wrong CMD code happen in Write! CMD: " + Integer.toString(inp2.getCMD()));
    		}
        } catch (IOException e) {
            e.printStackTrace();
        }
		
	}
    
    
    private static void beforeCloseSocket(MySocketObject s) {
    	try {
    		DataInputStream din = s.getDataInputStream();
			DataOutputStream dout = s.getDataOutputStream();
    		Socket socket = s.getSocket();
    		toSocket(dout, CMD.Exit, "_");
    		din.close();
    		dout.close();
    		socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
	

	private static MySocketObject getRandomSocket() {
		int serverID = random.nextInt(serverList.length);
		System.out.println("To server " + Integer.toString(serverID) + ":");
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
		return new SimpleDateFormat("MM/dd/yyyy-HH:mm:ss.SSS").format(new Date());
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
				Socket s = new Socket(serverList[i], SERVER_PORT);
				socketList[i] = new MySocketObject(s);
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
    			read(getRandomSocket(), getRandomFile());
    		} else if (act.equals(ACTION.Write)) {
    			write(getRandomSocket(), getRandomFile(), getLocalTimestamp());
    		}
    	}
    	for (MySocketObject s : socketList) {
    		beforeCloseSocket(s);
    	}
    	System.out.println("Finish " + Integer.toString(ROUND_TIMES) + " rounds and exit.");
    }
	
}
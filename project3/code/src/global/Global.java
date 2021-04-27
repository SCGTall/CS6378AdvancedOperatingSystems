package global;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class Global {
	
	public static final int CLIENTNUM = 5;
	public static final String CLIENTNAME = "client";
	public static final String CLIENTSRC = "client.jar";
	public static final String CLIENTPREFIX = "C";
	public static final int SERVERNUM =3;
	public static final String SERVERNAME = "server";
	public static final String SERVERSRC = "server.jar";
	public static final String SERVERPREFIX = "S";
	public static final int FILENUM = 4;
	public static final String FILEPREFIX = "F";
	public static final String FILESUFFIX = ".txt";
	public static final String EMPTYSTRING = "Empty first line.";
	public static final int BUFFER_SIZE = 64;
	public static final boolean SHOW_LOG = true;
	
	public static String[] serverIPs = new String[] {
		"10.176.69.52",
		"10.176.69.53",
		"10.176.69.54",
    };
	public static int[] serverPorts = new int[] {
		8800,
		8801,
		8802,
    };
	public static String[] clientIPs = new String[] {
		"10.176.69.62",
		"10.176.69.63",
		"10.176.69.64",
		"10.176.69.65",
		"10.176.69.66",
    };
	public static int[] clientPorts = new int[] {
		8900,
		8901,
		8902,
		8903,
		8904,
    };
	
	public static enum CMD {
		Message(0),
		Enquiry(1),
		Read(2),
		Write(3),
		FinishEnquiry(4),
		Yield(5),
		Failed(6),;
		
		private int value;

	    private CMD(int value) {
	        this.value = value;
	    }
	    
	    public int getValue() {
	        return value;
	    }
	    
	    public boolean equals(CMD other) {
	    	return other.getValue() == this.value;
	    }
	}
	
	public static class Message {
		public Socket socket;
    	public int cmd;
    	public String from;
    	public String to;
    	public String file;
    	public String timestamp;
    	public String sentence;
    	
    	public Message(Socket s, CMD cmd, String from, String to, String file, String timestamp, String sentence) {
    		this.socket = s;
    		this.cmd = cmd.getValue();
    		this.from = from;
    		this.to = to;
    		this.file = file;
    		this.timestamp = timestamp;
    		this.sentence = sentence;
    	}
    	
    	public Message(Socket s, String str) {
    		this.socket = s;
    		String[] splited = str.split("\\|", 0);
    		this.cmd = Integer.parseInt(splited[1]);
    		this.from = splited[2];
    		this.to = splited[3];
    		this.file = splited[4];
    		this.timestamp = splited[5];
    		this.sentence = splited[6];
    	}
    	
    	@Override
    	public String toString() {
    		StringBuilder sb = new StringBuilder();
    		sb.append("|" + cmd);
    		sb.append("|" + from);
    		sb.append("|" + to);
    		sb.append("|" + file);
    		sb.append("|" + timestamp);
    		sb.append("|" + sentence);
    		sb.append("|");
    		String format = "%-" + BUFFER_SIZE + "s";
        	String padded = String.format(format, sb.toString());
    		return padded;
    	}
    }
	
	public static void toSocket(Socket s, Message m) throws IOException {
    	OutputStream dout = m.socket.getOutputStream();
    	String padded = m.toString();
		dout.write(padded.getBytes());
		dout.flush();
		if (SHOW_LOG) {
			System.out.println("Output:(" + padded + ")");
		}
		return;
	}
    
    
    public static Message fromSocket(Socket s) {
    	byte buf[] = new byte[BUFFER_SIZE];
		try {
			InputStream din = s.getInputStream();
			din.read(buf);
		} catch (IOException e) {
			return null;
		}
		
		String padded = new String(buf);
		Message m = new Message(s, padded);
		if (SHOW_LOG) {
			System.out.println("Input :(" + padded + ")");
		}
		return m;
    }
    
    public static int getID(String name) {
		return name.charAt(1) - '0';
	}

}

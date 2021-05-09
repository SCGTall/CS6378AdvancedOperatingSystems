package global;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;

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
	public static final int BUFFERSIZE = 64;
	public static final boolean LOGLEVEL1 = false;  // input and output
	public static final boolean LOGLEVEL2 = true;  // state and general
	public static final int SLEEPTIME = 50;
	public static final String TIMEFORMAT = "HH:mm:ss";
	//public static final String TIMEFORMAT = "HH:mm:ss.SSS";
	
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
		Message(0, "Message"),
		Enquiry(1, "Enquiry"),  // ask file list in server
		Read(2, "Read"),
		Write(3, "Write"),
		FinishEnquiry(4, "FinishEnquiry"),
		Exit(5, "Exit"),
		Request(6, "Request"),
		Reply(7, "Reply"),
		Failed(8, "Failed"),
		Inquire(9, "Inquire"),  // ask for ownership between clients
		Relinquish(10, "Relinquish"),
		Release(11, "Release");
		
		private int value;
		private String name;
		private static final HashMap<Integer, String> map;
		
		static
	    {
			map = new HashMap<Integer, String>();
	        for (CMD cmd : EnumSet.allOf(CMD.class))
	        {
	            map.put(cmd.getValue(), cmd.getName());
	        }
	    }

	    private CMD(int value, String name) {
	        this.value = value;
	        this.name = name;
	    }
	    
	    public int getValue() {
	        return this.value;
	    }
	    
	    public String getName() {
	    	return this.name;
	    }
	    
	    public boolean equals(CMD other) {
	    	return other.getValue() == this.value;
	    }
	    
	    public static String getNameByValue(int value) {
	    	return map.get(value);
	    }
	}
	
	public static class Counter {
		public HashMap<Integer, Integer> counts = new HashMap<Integer, Integer>();
		
		public Counter() {
			for (CMD cmd : EnumSet.allOf(CMD.class))
	        {
				counts.put(cmd.value, 0);
	        }
		}
		
		public void print(String label) {
			System.out.println("\nMessage analysis: (" + label + ")");
			ArrayList<String> lines1 = new ArrayList<String>();
			ArrayList<String> lines2 = new ArrayList<String>();
			String line = "";
			int count = 0;
			for (CMD cmd : EnumSet.allOf(CMD.class))
	        {
				if (count < 6) {
					line += cmd.getName() + "  ";
					count += 1;
				} else {
					lines1.add(line);
					line = "";
					count = 0;
				}
	        }
			lines1.add(line);
			line = "";
			count = 0;
			for (CMD cmd : EnumSet.allOf(CMD.class))
	        {
				if (count < 6) {
					line += String.format("%-" + cmd.getName().length() + "s", counts.get(cmd.getValue())) + "  ";
					count += 1;
				} else {
					lines2.add(line);
					line = "";
					count = 0;
				}
	        }
			lines2.add(line);
			for (int i = 0; i < lines1.size(); i++) {
				System.out.println(lines1.get(i));
				System.out.println(lines2.get(i));
			}
		}
	}
	
	public static class Message {
		public String cmdName;
    	public int cmd;
    	public String from;
    	public String to;
    	public String file;
    	public String timestamp;
    	public String sentence;
    	
    	public Message(CMD cmd, String from, String to, String file, String timestamp, String sentence) {
    		this.cmdName = cmd.getName();
    		this.cmd = cmd.getValue();
    		this.from = from;
    		this.to = to;
    		this.file = file;
    		this.timestamp = timestamp;
    		this.sentence = sentence;
    	}
    	
    	public Message(String str) {
    		String[] splited = str.split("\\|", 0);
    		this.cmd = Integer.parseInt(splited[1]);
    		this.cmdName = CMD.getNameByValue(this.cmd);
    		this.from = splited[2];
    		this.to = splited[3];
    		this.file = splited[4];
    		this.timestamp = splited[5];
    		this.sentence = splited[6];
    	}
    	
    	@Override
    	public String toString() {
    		StringBuilder sb = new StringBuilder();
    		sb.append("|" + this.cmd);
    		sb.append("|" + this.from);
    		sb.append("|" + this.to);
    		sb.append("|" + this.file);
    		sb.append("|" + this.timestamp);
    		sb.append("|" + this.sentence);
    		sb.append("|");
    		String format = "%-" + BUFFERSIZE + "s";
        	String padded = String.format(format, sb.toString());
    		return padded;
    	}
    }
	
	public static void toSocket(Socket s, Message m, Counter c) throws IOException {
    	OutputStream dout = s.getOutputStream();
    	String padded = m.toString();
		dout.write(padded.getBytes());
		dout.flush();
		if (LOGLEVEL1) {
			System.out.println("Output:(" + m.cmdName + padded + ")");
		}
		c.counts.put(m.cmd, c.counts.get(m.cmd) + 1);
		return;
	}
    
    
    public static Message fromSocket(Socket s, Counter c) {
    	byte buf[] = new byte[BUFFERSIZE];
		try {
			InputStream din = s.getInputStream();
			din.read(buf);
		} catch (IOException e) {
			return null;
		}
		
		String padded = new String(buf);
		Message m = new Message(padded);
		if (LOGLEVEL1) {
			System.out.println("Input:(" + m.cmdName + padded + ")");
		}
		c.counts.put(m.cmd, c.counts.get(m.cmd) + 1);
		return m;
    }
    
    public static int getID(String name) {
		return name.charAt(1) - '0';
	}
    
    public static String getLocalTimestamp() {
		return new SimpleDateFormat(TIMEFORMAT).format(new Date());
	}
    
    public static boolean compareTimestamp(String ts1, String ts2) {  // return true if ts1 < ts2
		Date date1 = null;
		Date date2 = null;
		try {
			date1 = new SimpleDateFormat(TIMEFORMAT).parse(ts1);
			date2 = new SimpleDateFormat(TIMEFORMAT).parse(ts2);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return date1.before(date2);
	}

}

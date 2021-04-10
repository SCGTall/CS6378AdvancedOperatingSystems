package server;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MyServer {
	
	// finals
	private static final int CLIENT_NUM = 5;
	private static final String FOLDER_DIR = "hosted";
	private static final String ORIGINAL_HOSTED = "original";
	private static final String JAR_NAME = "server.jar";
	
	
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
		Exit(5),
		Request(6),
		Reply(7),
		EarlyExit(-48);
		
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
	
    private static int serverID;
    private static ArrayList<Object> locks = new ArrayList<Object>();
    private static ArrayList<SharedState> states = new ArrayList<SharedState>();
    private static Object readyLock = new Object();
    private static boolean allClientsReady = false;
    private static String prefix;  // prefix of folder
    private static ArrayList<MySocketObject> serverLan = new ArrayList<MySocketObject>(serverList.length);
    private static ArrayList<MySocketObject> clientLan = new ArrayList<MySocketObject>(CLIENT_NUM);
    private static ArrayList<String> fileList = new ArrayList<String>();
    
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
    	
    	public String getOriginalMSG() {
    		return String.join("&", messages);
    	}
    }
    
    private static final int BUFFER_SIZE = 64;
    
    private static void toSocket(DataOutputStream dout, CMD cmd, String s) throws IOException {
    	String combined = "|" + Integer.toString(cmd.getValue()) + "|" + s + "|";
    	String format = "%-" + Integer.toString(BUFFER_SIZE) + "s";
    	String padded = String.format(format, combined); // 64 byte
		dout.write(padded.getBytes());
		dout.flush();
		//System.out.println("Output:(" + padded + ")");
		return;
	}
    
    
    private static Input fromSocket(DataInputStream din) {
    	byte buf[] = new byte[BUFFER_SIZE];
		try {
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
				String s = padded.substring(start, i);
				msgs.add(s);
				break;
			}
			if (padded.charAt(i) == '&')  {  // seperator of messages
				String s = padded.substring(start, i);
				msgs.add(s);
				start = i + 1;
			}
		}
		return new Input(cmd, msgs);
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
    	
    	public DataInputStream getDin() {
    		return this.din;
    	}
    	
    	public DataOutputStream getDout() {
    		return this.dout;
    	}
    }
    
    private static class Operation {
    	private boolean finished = false;
    	public String timestamp;
    	public Date date;
    	public CMD cmd;
    	public String fileName;
    	public String newLine = "";
    	public String lastLine = "";
    	private String originalMSG;
    	
    	public Operation(CMD cmd, Input inp) throws ParseException {
    		this.timestamp = inp.getMSG(1);
    		this.date = new SimpleDateFormat("HH:mm:ss.SSS").parse(timestamp);
    		this.cmd = cmd;
    		this.fileName = inp.getMSG(0);
    		if (cmd.getValue() == CMD.Write.getValue()) {
    			this.newLine = inp.getMSG(2);
    		}
    		this.originalMSG = inp.getOriginalMSG();
		}
    	
    	public void setFinish() {
    		finished = true;
    	}
    	
    	public boolean isFinished() {
    		return finished;
    	}

		public boolean isBefore(Operation o2) {
    		return date.before(o2.date);
    	}
    }
    
    private static class SharedState {
    	public boolean using = false;
    	public boolean waiting = false;
    	public boolean blockForWrite = false;
    	public boolean[] alist;
    	public boolean[] deferred;
    	public boolean[] finishWrite;
    	private ArrayList<Operation> priorityQueue = new ArrayList<Operation>();
    	private boolean haveNewHead = false;
    	
    	public SharedState() {
    		this.alist = new boolean[serverList.length];
    		this.deferred = new boolean[serverList.length];
    		this.finishWrite = new boolean[serverList.length];
    		for (int i = 0; i < serverList.length; i++) {
    			this.alist[i] = false;
    			this.deferred[i] = false;
    		}
    		initFinishWrite();
    	}
    	
    	public void initFinishWrite() {
    		for (int i = 0; i < finishWrite.length; i++) {
    			finishWrite[i] = (i == serverID);
    		}
    		//showState();
    	}
    	/*
    	public void showState() {
    		String fStr = "";
    		String aStr = "";
    		for (int i = 0; i < finishWrite.length; i++) {
    			if (i == 0) {
    				fStr = "[";
    				aStr = "[";
    			} else {
    				fStr += ", ";
    				aStr += ", ";
    			}
    			fStr += bool2Str(finishWrite[i]);
    			aStr += bool2Str(alist[i]);
    		}
    		fStr += "]";
    		aStr += "]";
    		System.out.println(fStr);
    		System.out.println(aStr);
    	}
    	
    	public String bool2Str(boolean b) {
    		if (b) {
    			return "1";
    		} else {
    			return "0";
    		}
    	}
    	*/
    	
    	public boolean allFinishWrite() {
    		boolean allFinished = true;
    		for (int i = 0; i < serverList.length; i++) {
    			if (finishWrite[i] == false) {
    				allFinished = false;
    				break;
    			}
    		}
    		return allFinished;
    	}

		public int addToPriorityQueue(Operation o) {
			int index = 0;
			if (using) {
				index += 1;
			}
			while (true) {
				if (index >= priorityQueue.size()) {
					priorityQueue.add(o);
					break;
				} else if (o.isBefore(priorityQueue.get(index))) {
					priorityQueue.add(index, o);
					break;
				} else {
					index += 1;
				}
			}
			if (index == 0) {
				haveNewHead = true;
			}
			return index;
		}
		
		public Operation getHeadOperation() {
			if (priorityQueue.size() == 0) {
				return null;
			} else {
				return priorityQueue.get(0);
			}
		}
    	
    }
    
    // create lan and listen to clients
    public static class ListenerThreadHandler implements Runnable {
    	
        private ExecutorService executor;
        private ServerSocket server;
        private int totalNum;
        private int currentNum = 0;
        
        public ListenerThreadHandler(ExecutorService executor, ServerSocket server, int threadNum) throws IOException {
        	this.server = server;
        	this.executor = executor;
        	this.totalNum = threadNum;
        }
        
        @Override
        public void run() {
        	// firstly, build up lan
            try {
            	// start listening
            	System.out.println("Start listening...");
        		// connect with existing server. Make sure that all servers are executed by turn.
        		for (int i = 0; i < serverID; i++) {
        			Socket s = new Socket(serverList[i], portList[i]);
        			executor.submit(new S2SThreadHandler(s));
        			currentNum += 1;
        		}
        		// build worker thread for each file
                for (int i = 0; i < fileList.size(); i++) {
                	executor.submit(new WorkerHandler(fileList.get(i), locks.get(i), states.get(i)));
                	currentNum += 1;
                }
                // wait for other clients
                while (currentNum < totalNum) {  // end loop when all sockets are established
                	Socket s = server.accept();
                	//System.out.println("ip: " + s.getInetAddress().toString());
                	String ip = s.getInetAddress().toString().substring(1);
                	int id = getServerIndex(ip);
                	if (id < 0) {  // id not found
                		executor.submit(new C2SThreadHandler(s));
                	} else {
                		executor.submit(new S2SThreadHandler(s));
                	}
                	currentNum += 1;
                }
                synchronized (readyLock) {
                	allClientsReady = true;
                	System.out.println("Start now.");
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            
        }
        
    }
    
    
	// worker thread for each file
    public static class WorkerHandler implements Runnable {
    	
    	private String fileName;
        private Object lock;
        private SharedState ss;
        
        public WorkerHandler(String fileName, Object lock, SharedState ss) {
        	this.fileName = fileName;
            this.lock = lock;
            this.ss = ss;
        }
        
        @Override
        public void run() {
        	while (true) {  // have to release lock to let other thread change state
        		synchronized(lock) {
        			Operation head = ss.getHeadOperation();
        			if (ss.blockForWrite) {  // if block for write
        				if (ss.allFinishWrite()) {
        					release(head);
        					ss.blockForWrite = false;
        				}
        			} else if (getAllReply(ss.alist) && head != null) {  // ready to go: waitfor (A[j] = true for all j ME);
        				ss.waiting = false;
        				ss.using = true;
        				if (head.cmd.equals(CMD.Read)) {
        					synRead(head);
        					release(head);
        				} else if (head.cmd.equals(CMD.Write)) {
        					synWrite(head);
        				} else {
        					System.out.println("Wrong cmd in Operation.");
        				}
        			} else if (ss.haveNewHead && head != null) {  // request resource for new operation
    					request(head);
            		}
            	}
        	}
        }
        
        private void synRead(Operation o) {
        	try {
	        	String readDir = prefix + fileName;
        		FileInputStream fis = new FileInputStream(readDir);
        		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
        		String lastLine = "";
        		String line = br.readLine();
        		while (line != null) {
        			lastLine = line;
        			line = br.readLine();
        		}
        		fis.close();
        		o.lastLine = lastLine;
        	} catch (IOException e) {
                e.printStackTrace();
            }
		}

		private void synWrite(Operation o) {
			try {
        		String fileName = o.fileName;
        		String writeDir = prefix + "/" + fileName;
				String newLine = o.newLine + "\n";
        		// write to local
				Files.write(Paths.get(writeDir), newLine.getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
				// write to remote
				String msg = o.originalMSG;
				for (MySocketObject s : serverLan) {
					DataOutputStream dout = s.getDout();
					toSocket(dout, CMD.Write, msg);
				}
        		ss.blockForWrite = true;
        		ss.initFinishWrite();
        	} catch (IOException e) {
                e.printStackTrace();
            }
		}

		private void release(Operation o) {
			// remove finished opeartion from priority queue
			o.setFinish();
			ss.priorityQueue.remove(0);
			if (ss.priorityQueue.size() > 0) {
				ss.haveNewHead = true;
			}
			// release resource
        	ss.using = false;
        	try {
	        	for (int i = 0; i < ss.alist.length; i++) {
	        		if (ss.deferred[i]) {  // if ReplyDeferred [j]
	        			ss.alist[i] = false;
	        			ss.deferred[i] = false;  // A[j] := ReplyDeferred [j] := false;
	        			DataOutputStream dout = serverLan.get(i).getDout();
	        			toSocket(dout, CMD.Reply, fileName);  // Send (REPLY (ME), j)
	        		}
	        	}
        	} catch (IOException e) {
				e.printStackTrace();
			}
        }
        
        private boolean getAllReply(boolean[] alist) {
			for (int i = 0; i < alist.length; i++) {
				if (i == serverID) {
					continue;
				} else {
					if (alist[i] == false) {
						return false;
					}
				}
			}
			return true;
		}
		
        public void request(Operation o) {
        	ss.waiting = true;  // Waiting := true;
        	try {
        		for (MySocketObject s : serverLan) {
        			String ip = s.getSocket().getInetAddress().toString().substring(1);
                	int id = getServerIndex(ip);
                	if (!ss.alist[id]) {
                		DataOutputStream dout = s.getDout();
        				toSocket(dout, CMD.Request, o.fileName + "&" + o.timestamp);  // send (REQUEST (OurSequenceNumber, ME), j)
                	}
    			}
        	} catch (IOException e) {
				e.printStackTrace();
			}
			ss.haveNewHead = false;
			
        }
        
    }
    
    
    // server to server
    public static class S2SThreadHandler implements Runnable {
    	
        private MySocketObject s;
        
        public S2SThreadHandler(Socket s) throws IOException {
            this.s = new MySocketObject(s);
            serverLan.add(this.s);  // record in serverLan in order to be found when need to close.
        }
        
        @Override
        public void run() {
            try {
        		DataInputStream din = s.getDin();
        		DataOutputStream dout = s.getDout();
        		int cmd;
        		boolean flag = true;
        		while (flag) {
        			Input inp = fromSocket(din);
        			cmd = inp.getCMD();
        			// use cmd to decide coming action
        			switch (cmd) {
	        			case 3: {  // Write
	        				String fileName = inp.getMSG(0);
		    				String writeDir = prefix + fileName;
		    				String newLine = inp.getMSG(2) + "\n";
			        		// write to file
			        		Files.write(Paths.get(writeDir), newLine.getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
			        		// tell end of write
			        		toSocket(dout, CMD.Next, inp.getOriginalMSG());
			        		String log = "Server " + serverID + ": ";
			        		log += "Replicate to " + fileName + " (" + inp.getMSG(2) + ").";
			        		System.out.println(log);
		    				break;
		    			}
	        			case 4: { // Next
	        				String ip = s.getSocket().getInetAddress().toString().substring(1);
		                	int id = getServerIndex(ip);
		                	String fileName = inp.getMSG(0);
		                	int fi = getFileIndex(fileName);
		                	synchronized (locks.get(fi)) {
		                		SharedState ss = states.get(fi);
		                		if (ss.blockForWrite && ss.getHeadOperation().originalMSG.equals(inp.getOriginalMSG())) {
		                			states.get(fi).finishWrite[id] = true;
		                		}
		                	}
	        				break;
	        			}
	        			case 5: {  // Exit
	        				if (!s.getSocket().isClosed()) {
	        					s.getSocket().shutdownInput();
	        				}
    	    				flag = false;
    	    				break;
    	    			}
	        			case 6: {  // Request
	        				String fileName = inp.getMSG(0);
		    				String timestamp = inp.getMSG(1);
		    				Date date = timestampToDate(timestamp);
		    				int fi = getFileIndex(fileName);
		    				String ip = s.getSocket().getInetAddress().toString().substring(1);
		                	int id = getServerIndex(ip);
	        				synchronized (locks.get(fi)) {
	        					SharedState ss = states.get(fi);
	        					Operation head = ss.getHeadOperation();
	        					Date our = null;
	        					if (head != null) {
	        						our = head.date;
	        					}
	        					if (ss.using || (ss.waiting && ourPriority(our, date, id))) {  // Using or (Waiting and Our Priority)
	        						ss.deferred[id] = true;// ReplyDeferred [j] :-- true
	        					} else if (!(ss.using || ss.waiting) || (
	        							ss.waiting && !ss.alist[id] && !ourPriority(our, date, id))) {  // not (Using or Waiting) or (Waiting and (not A [j]) and (not Our Priority))
	        						toSocket(dout, CMD.Reply, fileName);  // send (REPLY(ME), j)
	        					} else if (ss.waiting && ss.alist[id] && !ourPriority(our, date, id)) {  // Waiting and A[j] and (not Our Priority)
	        						ss.alist[id] = false;  // A[j] := false;
	        						toSocket(dout, CMD.Reply, fileName);  // send (REPLY(ME), j);
	        						toSocket(dout, CMD.Request, fileName + "&" + timestamp);  // send (REQUEST (OurSequenceNumber, ME), j)
	        					} else {
	        						System.out.println("Unhandled condition in Request handler.");
	        					}
	        				}
	        				break;
	        			}
	        			case 7: {  // Reply
	        				String fileName = inp.getMSG(0);
		    				int fi = getFileIndex(fileName);
		    				String ip = s.getSocket().getInetAddress().toString().substring(1);
		                	int id = getServerIndex(ip);
		                	synchronized (locks.get(fi)) {
		                		states.get(fi).alist[id] = true;  // A[j] := true
	        				}
	        				break;
	        			}
	        			case -48: {  // EarlyExit
	        				System.out.println("One server disconnected.");
	        				if (!s.getSocket().isClosed()) {
	        					s.getSocket().shutdownInput();
	        				}
	        				flag = false;
    	    				break;
	        			}
		    			default: {
		    				System.out.println("Unvalid cmd received in s2s: " + cmd);
		    				flag = false;
		    				break;
		    			}
        			}
        		}
        		din.close();
            	dout.close();
            	s.getSocket().close();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ParseException e) {
				e.printStackTrace();
			}
        }
        
        public boolean ourPriority(Date our, Date their, int theirID) {
        	if (our == null) {
        		return false;
        	}
        	// Our Priority := Their_Sequence_Number > Our_ Sequence_Number or ((Their Sequence_Number = Our Sequence_ Number) and j > ME);
        	return (their.after(our) || (their.equals(our) && theirID > serverID));
        }
        
    }
    
    
    // client to server
    public static class C2SThreadHandler implements Runnable {

    	private MySocketObject s;
        
        public C2SThreadHandler(Socket s) throws IOException {
        	this.s = new MySocketObject(s);
        	clientLan.add(this.s);  // record in clientLan in order to be found when need to close.
        }
        
        @Override
        public void run() {
        	try {
            	DataInputStream din = s.getDin();
            	DataOutputStream dout = s.getDout();
        		int cmd;
        		boolean flag = true;
        		int clientID = din.readInt();
        		System.out.println("Client " + clientID + ": connected.");
        		while (flag) {
        			if (s.getSocket().isClosed()) {
        				break;
        			}
        			Input inp = fromSocket(din);
        			cmd = inp.getCMD();
        			String log = "Client " + clientID + ": ";
        			// use cmd to decide coming action
        			switch (cmd) {
	        			case 1: {  // Enquiry
	        				boolean waitOthers = true;
	        				while (waitOthers) {
	        					synchronized (readyLock) {
	        						waitOthers = !allClientsReady;
	        					}
	        				}
    	    				// traverse hosted files
    	    				for (String name : fileList) {
    	    					toSocket(dout, CMD.Enquiry, name);
    	    				}
    	    				// tell client to end
    	    				toSocket(dout, CMD.Next, inp.getOriginalMSG());
    	    				log += "Enquiry.";
    	    				break;
    	    			}
    	    			case 2: {  // Read
    	    				// push operation into queue
    	    				Operation o = new Operation(CMD.Read, inp);
    	    				String fileName = o.fileName;
    	    				int fi = getFileIndex(fileName);
    	    				synchronized(locks.get(fi)) {
    	    					states.get(fi).addToPriorityQueue(o);
    	    				}
    	    				// wait for operation finish
    	    				boolean finished = false;
    	    				while (!finished) {
    	    					synchronized(locks.get(fi)) {
    	    						if (o.isFinished()) {
    	    							finished = true;
    	    						}
    	    					}
    	    				}
    	        			// tell client last line
    	        			toSocket(dout, CMD.Read, o.lastLine);
    	        			// tell client to end
    	        			toSocket(dout, CMD.Next, inp.getOriginalMSG());
    	        			log += "Read from " + fileName + " (" + o.lastLine + ").";
    	    				break;
    	    			}
    	    			case 3: {  // Write
    	    				Operation o = new Operation(CMD.Write, inp);
    	    				String fileName = o.fileName;
    	    				int fi = getFileIndex(fileName);
    	    				synchronized(locks.get(fi)) {
    	    					states.get(fi).addToPriorityQueue(o);
    	    				}
    	    				// wait for operation finish
    	    				boolean finished = false;
    	    				while (!finished) {
    	    					synchronized(locks.get(fi)) {
    	    						if (o.isFinished()) {
    	    							finished = true;
    	    						}
    	    					}
    	    				}
    	    				// tell client to end
    	            		toSocket(dout, CMD.Next, inp.getOriginalMSG());
    	            		log += "Write to " + fileName + " (" + o.newLine + ").";
    	    				break;
    	    			}
    	    			case 5: {  // Exit
    	    				flag = false;
    	    				s.getSocket().shutdownInput();
    	    				log += "Exit after all tasks finished.";
    	    				break;
    	    			}
    	    			default: {
    	    				System.out.println("Unvalid cmd received in c2s: " + cmd);
    	    				flag = false;
    	    				break;
    	    			}
        			}
        			System.out.println(log);
        		}
        		din.close();
            	dout.close();
            	s.getSocket().close();  // close client to server socket after client sent Exit command.
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ParseException e) {
				e.printStackTrace();
			}
        }
        
        
    }
    
    
    private static int getFileIndex(String fileName) {
		for (int i = 0; i < fileList.size(); i++) {
			if (fileList.get(i).equals(fileName)) {
				return i;
			}
		}
		return -1;
	}
    
    
    private static Date timestampToDate(String timestamp) throws ParseException {
    	return new SimpleDateFormat("HH:mm:ss.SSS").parse(timestamp);
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
    
    
    private static int getServerIndex(String ip) {
    	String target = ip;
    	if (ip.charAt(0) == '/') {
    		target = ip.substring(1);  // remove possible /
    	}
    	for (int i = 0; i < serverList.length; i++) {
    		if (serverList[i].equals(target)) {
    			return i;
    		}
    	}
    	return -1;
    }
    
	
    public static void main(String[] args) {
    	// init
    	if(args.length != 1) {
			System.out.println("Wrong input! Try like: jar server.jar <server id>");
			System.exit(1);
		}
    	serverID = Integer.parseInt(args[0]);
    	// copy beginning files from ORIGINAL_HOSTED
    	try {
    		String parentDir = new File(JAR_NAME).getAbsoluteFile().getParentFile().getParent().toString();
        	File original = new File(parentDir + "/" + ORIGINAL_HOSTED);
        	if (!original.exists()) {
        		System.out.println("Original hosted files are missing!");
    			System.exit(1);
        	}
        	File folder = new File(FOLDER_DIR);
        	if (folder.exists()) {
        		deleteAll(folder);
        	}
        	folder.mkdir();
        	prefix = folder.getName() + "/";
        	for (File f : original.listFiles()) {
        		File dest = new File(prefix + f.getName());
        		fileList.add(f.getName());
        		Date[] requests = new Date[serverList.length];
        		boolean[] replies = new boolean[serverList.length];
        		for (int i = 0; i < serverList.length; i++) {
        			requests[i] = null;
        			replies[i] = false;
            	}
        		locks.add(new Object());
        		states.add(new SharedState());
        		copyFileUsingFileChannels(f, dest);
        	}
    	} catch (IOException e) {
            e.printStackTrace();
        }
    	try {
			// create a listener to connect with clients and servers
	    	// use a thread pool to manage threads which contain 5(to clients) + 2(to other servers) + 2(file num) = 10 threads
	    	int threadNum = serverList.length - 1 + CLIENT_NUM + fileList.size();
	    	ExecutorService executor = Executors.newFixedThreadPool(threadNum);
	    	ServerSocket server = new ServerSocket(portList[serverID]);
			Thread listener = new Thread(new ListenerThreadHandler(executor, server, threadNum));
	    	listener.start();
	    	// wait until exit
	    	System.out.println("Enter exit to terminate the server.");
	    	Scanner io = new Scanner(System.in);
	    	String word = io.nextLine();
	    	while (!word.equals("exit")) {
	    		word = io.nextLine();
	    	}
	    	io.close();
	    	System.out.println("Clear up remaining threads and sockets.");
	    	// do before exit
	    	executor.shutdown();
	    	executor.awaitTermination(500, TimeUnit.MILLISECONDS);
	        server.close();
	        // close all sockets
	        for (MySocketObject s : serverLan) {
	        	s.getDin().close();
	        	s.getDout().close();
	        	if (!s.getSocket().isClosed()) {
	        		s.getSocket().shutdownInput();
	        	}
	        	s.getSocket().close();
	        }
	        	
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
        System.out.println("Exit after all works are done.");
        System.exit(0);
    }
}
package server;

import java.io.BufferedReader;
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
	private static int[] reservedPortList = new int[] {
			8900, 8901
	};  // port for in stream and port for out stream
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
    private static Socket[] serverInLan = new Socket[serverList.length];
    private static Socket[] serverOutLan = new Socket[serverList.length];
    private static Socket[] clientLan = new Socket[CLIENT_NUM];
    private static ArrayList<String> fileList = new ArrayList<String>();
    
    private static class Input {
    	private int cmd;
    	private int fromID;
    	private int fileIndex;
    	private String[] messages;
    	
    	public Input(int cmd, int id, int fi, String[] msgs) {
    		this.cmd = cmd;
    		this.fromID = id;
    		this.fileIndex = fi;
    		this.messages = msgs;
    	}

    	public int getCMD() {
    		return cmd;
    	}
    	
    	public int getFromID() {
    		return fromID;
    	}
    	
    	public int getFileIndex() {
    		return fileIndex;
    	}
    	
    	public String getMSG(int index) {
    		return messages[index];
    	}
    	
    	public String getOriginalMSG() {
    		return String.join("&", messages);
    	}
    }
    
    private static final int BUFFER_SIZE = 64;
    
    private static void toSocket(Socket s, CMD cmd, String to, int fi, String msg) throws IOException {
    	String combined = "|" + cmd.getValue() + "|S" + serverID + "|" + to + "|" + fi + "|" + msg + "|";
    	String format = "%-" + BUFFER_SIZE + "s";
    	String padded = String.format(format, combined); // 64 byte
		OutputStream dout = s.getOutputStream();
		dout.write(padded.getBytes());
		dout.flush();
		System.out.println("Output:(" + padded + ")");
		return;
	}
    
    
    private static Input fromSocket(Socket s) {
    	byte buf[] = new byte[BUFFER_SIZE];
		try {
			InputStream din = s.getInputStream();
			din.read(buf);
		} catch (IOException e) {
			return new Input(5, -1, -1, null);
		}
		String padded = new String(buf);
		if (padded.length() == 0) {
			return new Input(5, -1, -1, null);
		}
		System.out.println("Input :(" + padded + ")");
		// decode messages
		String[] splited = padded.split("\\|", 0);
		int cmd = Integer.parseInt(splited[1]);
		int id = splited[2].charAt(1) - '0';
		int fi = Integer.parseInt(splited[4]);
		String[] msgs = splited[5].split("&");
		return new Input(cmd, id, fi, msgs);
    }
    
    private static class Operation {
    	public String timestamp;
    	public Date date;
    	public CMD cmd;
    	public int fileIndex;
    	public String newLine = "";
    	private String message;
    	public Socket s;
    	public int clientID;
    	
    	public Operation(CMD cmd, Input inp, Socket s, int clientID) throws ParseException {
    		this.timestamp = inp.getMSG(0);
    		this.date = new SimpleDateFormat("HH:mm:ss.SSS").parse(timestamp);
    		this.cmd = cmd;
    		this.fileIndex = inp.getFileIndex();
    		if (cmd.getValue() == CMD.Write.getValue()) {
    			this.newLine = inp.getMSG(1);
    		}
    		this.message = inp.getOriginalMSG();
    		this.s = s;
    		this.clientID = clientID;
		}

		public boolean isBefore(Operation o2) {
    		return date.before(o2.date);
    	}
    }
    
    private static class SharedState {
    	public boolean using = false;
    	public boolean waiting = false;
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
    	}
    	
    	public boolean allFinishWrite() {
    		for (int i = 0; i < serverList.length; i++) {
    			if (finishWrite[i] == false) {
    				return false;
    			}
    		}
    		return true;
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
				waiting = false;
				using = false;
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
    
    // create lan
    public static class ListenerThreadHandler implements Runnable {
    	
        private ExecutorService executor;
        private ServerSocket[] servers;
        
        public ListenerThreadHandler(ExecutorService executor, ServerSocket[] servers) throws IOException {
        	this.executor = executor;
        	this.servers = servers;
        }
        
        @Override
        public void run() {
        	// firstly, build up lan
            try {
            	// start listening
            	System.out.println("Server " + serverID + " start listening...");
        		// connect with existing server. Make sure that all servers are executed by turn.
            	int connectedServer = 1;
        		for (int i = 0; i < serverID; i++) {
        			Socket s1 = new Socket(serverList[i], reservedPortList[0]);
        			Socket s2 = new Socket(serverList[i], reservedPortList[1]);
        			executor.submit(new S2SThreadHandler(s1, s2));
        			connectedServer += 1;
        		}
                // wait for other servers
                while (connectedServer < serverList.length) {  // end loop when all sockets are established
                	Socket s1 = servers[0].accept();
                	Socket s2 = servers[1].accept();
                	executor.submit(new S2SThreadHandler(s1, s2));
                	connectedServer += 1;
                }
                // wait for clients
                int connectedClient = 0;
                while (connectedClient < CLIENT_NUM) {
                	Socket s = servers[2].accept();
                	executor.submit(new C2SThreadHandler(s));
                	connectedClient += 1;
                }
                // build worker thread for each file
                for (int i = 0; i < fileList.size(); i++) {
                	executor.submit(new WorkerHandler(i, locks.get(i), states.get(i)));
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
    	
    	private int fileIndex;
        private Object lock;
        private SharedState ss;
        
        public WorkerHandler(int fi, Object lock, SharedState ss) {
        	this.fileIndex = fi;
            this.lock = lock;
            this.ss = ss;
        }
        
        @Override
        public void run() {
        	while (true) {  // have to release lock to let other thread change state
        		synchronized(lock) {
        			Operation head = ss.getHeadOperation();
        			if (getAllReply(ss.alist) && head != null) {  // ready to go: waitfor (A[j] = true for all j ME);
        				ss.waiting = false;
        				ss.using = true;
        				if (head.cmd.equals(CMD.Read)) {
        					System.out.println("###1###");
        					synRead(head);
        				} else if (head.cmd.equals(CMD.Write)) {
        					System.out.println("###2###");
        					synWrite(head);
        				} else {
        					System.out.println("Wrong cmd in Operation.");
        				}
        				System.out.println("###3###");
        				release(head);
        			} else if (ss.haveNewHead && head != null) {  // request resource for new operation
        				request(head);
        				System.out.println("###0###");
            		}
            	}
        	}
        }
        
        private void synRead(Operation o) {
        	try {
	        	String readDir = prefix + fileList.get(fileIndex);
        		FileInputStream fis = new FileInputStream(readDir);
        		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
        		String lastLine = "";
        		String line = br.readLine();
        		while (line != null) {
        			lastLine = line;
        			line = br.readLine();
        		}
        		fis.close();
        		// tell client last line
    			toSocket(o.s, CMD.Next, "C" + o.clientID, o.fileIndex, lastLine + "&Next for read");
    			System.out.println("To client " + o.clientID + ": Read from " + fileList.get(o.fileIndex) + " (" + o.newLine + ").");
        	} catch (IOException e) {
                e.printStackTrace();
            }
		}

		private void synWrite(Operation o) {
			try {
        		int fi = o.fileIndex;
        		String writeDir = prefix + "/" + fileList.get(fileIndex);
				String newLine = o.newLine + "\n";
        		// write to local
				Files.write(Paths.get(writeDir), newLine.getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
				// write to remote
				for (int i = 0; i < serverList.length; i++) {
					if (i != serverID) {
						toSocket(serverOutLan[i], CMD.Write, "S" + i, fi, o.message);
					}
				}
        		ss.initFinishWrite();
        		System.out.println("WStep1");
        		while (true) {
        			if (ss.allFinishWrite()) {
        				break;
        			}
        			System.out.println("WStep2");
        		}
        		System.out.println("WStep3");
        		// tell client to end
        		toSocket(o.s, CMD.Next, "C" + o.clientID, fi, "Next for write");
        		System.out.println("To client " + o.clientID + ": Write to " + fileList.get(o.fileIndex) + " (" + o.newLine + ").");
        	} catch (IOException e) {
                e.printStackTrace();
            }
		}

		private void release(Operation o) {
			// remove finished opeartion from priority queue
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
	        			toSocket(serverOutLan[i], CMD.Reply, "S" + i, o.fileIndex, "");  // Send (REPLY (ME), j)
	        		}
	        	}
        	} catch (IOException e) {
				e.printStackTrace();
			}
        }
        
        private boolean getAllReply(boolean[] alist) {
			for (int i = 0; i < alist.length; i++) {
				if (i != serverID && alist[i] == false) {
					return false;
				}
			}
			return true;
		}
		
        public void request(Operation o) {
        	ss.waiting = true;  // Waiting := true;
        	try {
        		for (int i = 0; i < serverList.length; i++) {
        			if (i != serverID) {
        				if (!ss.alist[i]) {
        					toSocket(serverOutLan[i], CMD.Request, "S" + i, o.fileIndex, o.timestamp);  // send (REQUEST (OurSequenceNumber, ME), j)
        				}
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
    	
        private Socket sin;
        private Socket sout;
        private int otherID;
        
        public S2SThreadHandler(Socket s1, Socket s2) throws IOException {
            this.otherID = getServerIndex(s1.getInetAddress().toString());
            if (serverID < otherID) {
            	this.sin = s1;  // always assume higher id server use s1 as input socket
            	this.sout = s2;
            } else {
            	this.sout = s1;
            	this.sin = s2;
            }
            serverInLan[otherID] = this.sin;
            serverOutLan[otherID] = this.sout;  // record in serverLan in order to be found when need to close.
        }
        
        @Override
        public void run() {
            try {
        		int cmd;
        		boolean flag = true;
        		while (flag) {
        			Input inp = fromSocket(sin);
        			cmd = inp.getCMD();
        			// use cmd to decide coming action
        			switch (cmd) {
	        			case 3: {  // Write
	        				int fi = inp.getFileIndex();
	        				String fileName = fileList.get(fi);
		    				String writeDir = prefix + fileName;
		    				String newLine = inp.getMSG(1) + "\n";
			        		// write to file
			        		Files.write(Paths.get(writeDir), newLine.getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
			        		// tell end of write
			        		toSocket(sout, CMD.Next, "S" + otherID, fi, "Next for write.");
			        		String log = "Server " + serverID + ": ";
			        		log += "Replicate to " + fileName + " (" + inp.getMSG(1) + ").";
			        		System.out.println(log);
		    				break;
		    			}
	        			case 4: { // Next
		                	int fi = inp.getFileIndex();
		                	int id = inp.getFromID();
		                	states.get(fi).finishWrite[id] = true;
		                	System.out.print("Finish Write: ");
		                	for (int i = 0; i < serverList.length; i++) {
		                		System.out.print(states.get(fi).finishWrite[i]);
		                		System.out.print(" ");
		                	}
		                	System.out.print(states.get(fi).allFinishWrite());
		                	System.out.print("\n");
		                	break;
	        			}
	        			case 5: {  // Exit
	        				System.out.println("One server disconnected.");
	        				if (!sin.isClosed()) {
	        					sin.shutdownInput();
	        				}
    	    				flag = false;
    	    				break;
    	    			}
	        			case 6: {  // Request
		    				String timestamp = inp.getMSG(0);
		    				Date date = timestampToDate(timestamp);
		    				int fi = inp.getFileIndex();
		                	int id = inp.getFromID();
		                	System.out.println("Req lock " + fi + ".");
	        				synchronized (locks.get(fi)) {
	        					System.out.println("Req inside " + fi + ".");
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
	        						toSocket(sout, CMD.Reply, "S" + otherID, fi, "");  // send (REPLY(ME), j)
	        					} else if (ss.waiting && ss.alist[id] && !ourPriority(our, date, id)) {  // Waiting and A[j] and (not Our Priority)
	        						ss.alist[id] = false;  // A[j] := false;
	        						toSocket(sout, CMD.Reply, "S" + otherID, fi, "");  // send (REPLY(ME), j);
	        						toSocket(sout, CMD.Request, "S" + otherID, fi, head.timestamp);  // send (REQUEST (OurSequenceNumber, ME), j)
	        					} else {
	        						System.out.println("Unhandled condition in Request handler.");
	        					}
	        				}
	        				System.out.println("Req unlock " + fi + ".");
	        				break;
	        			}
	        			case 7: {  // Reply
		    				int fi = inp.getFileIndex();
		                	int id = inp.getFromID();
		                	System.out.println("Rep lock " + fi + ".");
		                	synchronized (locks.get(fi)) {
		                		System.out.println("Rep inside " + fi + ".");
		                		states.get(fi).alist[id] = true;  // A[j] := true
	        				}
		                	System.out.println("Rep unlock " + fi + ".");
	        				break;
	        			}
		    			default: {
		    				System.out.println("Unvalid cmd received in s2s: " + cmd);
		    				flag = false;
		    				break;
		    			}
        			}
        		}
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

    	private Socket s;
    	private int clientID;
        
        public C2SThreadHandler(Socket s) throws IOException {
        	this.s = s;
        }
        
        @Override
        public void run() {
        	try {
        		int cmd;
        		boolean flag = true;
        		Input input = fromSocket(s);
        		clientID = Integer.parseInt(input.getMSG(0));
        		clientLan[clientID] = s;  // record in clientLan in order to be found when need to close.
        		System.out.println("Client " + clientID + ": connected.");
        		// begin at the same time
        		boolean waitOthers = true;
				while (waitOthers) {
					synchronized (readyLock) {
						waitOthers = !allClientsReady;
					}
				}
        		while (flag) {
        			
        			if (s.isClosed()) {
        				break;
        			}
        			Input inp = fromSocket(s);
        			cmd = inp.getCMD();
        			String logHead = "To client " + clientID + ": ";
        			// use cmd to decide coming action
        			switch (cmd) {
	        			case 1: {  // Enquiry
    	    				// traverse hosted files
    	    				for (String name : fileList) {
    	    					toSocket(s, CMD.Enquiry, "C" + clientID, -1, name);
    	    				}
    	    				// tell client to end
    	    				toSocket(s, CMD.Next, "C" + clientID, -1, "Next for enquiry.");
    	    				System.out.println(logHead + "Enquiry.");
    	    				break;
    	    			}
    	    			case 2: {  // Read
    	    				// push operation into queue
    	    				Operation o = new Operation(CMD.Read, inp, s, clientID);
    	    				int fi = o.fileIndex;
    	    				System.out.println("Read lock " + fi + ".");
    	    				synchronized(locks.get(fi)) {
    	    					System.out.println("Read inside " + fi + ".");
    	    					states.get(fi).addToPriorityQueue(o);
    	    				}
    	    				System.out.println("Read unlock " + fi + ".");
    	    				break;
    	    			}
    	    			case 3: {  // Write
    	    				Operation o = new Operation(CMD.Write, inp, s, clientID);
    	    				int fi = o.fileIndex;
    	    				System.out.println("Write lock " + fi + ".");
    	    				synchronized(locks.get(fi)) {
    	    					System.out.println("Write inside " + fi + ".");
    	    					states.get(fi).addToPriorityQueue(o);
    	    				}
    	    				System.out.println("Write unlock " + fi + ".");
    	    				break;
    	    			}
    	    			case 5: {  // Exit
    	    				flag = false;
    	    				s.shutdownInput();
    	    				System.out.println(logHead + "Exit after all tasks finished.");
    	    				break;
    	    			}
    	    			default: {
    	    				System.out.println("Unvalid cmd received in c2s: " + cmd);
    	    				flag = false;
    	    				break;
    	    			}
        			}
        		}
            	s.close();  // close client to server socket after client sent Exit command.
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ParseException e) {
				e.printStackTrace();
			}
        }
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
	    	int threadNum = (serverList.length - 1) * 2 + CLIENT_NUM + fileList.size();
	    	ExecutorService executor = Executors.newFixedThreadPool(threadNum);
	    	ServerSocket[] servers = new ServerSocket[3];
	    	servers[0] = new ServerSocket(reservedPortList[0]);
    		servers[1] = new ServerSocket(reservedPortList[1]);
    		servers[2] = new ServerSocket(portList[serverID]);
			Thread listener = new Thread(new ListenerThreadHandler(executor, servers));
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
	        // close all sockets
	    	for (ServerSocket s : servers) {
	        	if (s != null && !s.isClosed()) {
	        		s.close();
	        	}
	        }
	        for (Socket s : serverInLan) {
	        	if (s != null && !s.isClosed()) {
	        		s.close();
	        	}
	        }
	        for (Socket s : serverOutLan) {
	        	if (s != null && !s.isClosed()) {
	        		s.close();
	        	}
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
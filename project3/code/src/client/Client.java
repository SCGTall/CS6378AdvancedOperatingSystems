package client;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import global.Global;
import global.Global.*;
import client.ClientClass.*;

public class Client {
	
	private static final int ROUND_TIME = 50;
	private static String myName;
	private static Socket[] serverSockets = new Socket[Global.SERVERNUM];
	private static ArrayList<String> fileList = new ArrayList<String>();
	private static HashMap<String, Control> controlMap;
	private static Random random = new Random();
	private static boolean finishEnquiry = false;
	private static Socket[] clientSockets = new Socket[Global.CLIENTNUM];
	private static int[] quorumList;
	private static boolean[] agreeToExit = new boolean[Global.CLIENTNUM];
	private static Counter inputCounter = new Counter();
	private static Counter outputCounter = new Counter();
	
	
	private static boolean allAgreeToExit() {
		for (boolean b : agreeToExit) {
			if (b == false) {
				return false;
			}
		}
		return true;
	}
	
	private static void printAgreeToExit() {
		if (Global.LOGLEVEL2) {
			System.out.print("State (Exit): ");
			for (boolean b : agreeToExit) {
				System.out.print("\t");
				System.out.print(b);
			}
			System.out.print("\n");
		}
	}
	
	private static Socket getRandomSocket() {
		int serverID = random.nextInt(serverSockets.length);
		return serverSockets[serverID];
	}
	
	private static Control getRandomFileControl() {
		if (controlMap.size() == 0) {
			return null;
		} else {
			return controlMap.get(fileList.get(random.nextInt(controlMap.size())));
		}
	}
	
	private static HashMap<String, Control> enquiry(Socket socket) {
		String to = Global.SERVERPREFIX + getOppositeServerID(socket);
		try {
			Message m = new Message(CMD.Enquiry, myName, to, "", "", "");
			Global.toSocket(socket, m, outputCounter);
        } catch (IOException e) {
            e.printStackTrace();
        }
		while (!finishEnquiry) {
    		try {
				Thread.sleep(Global.SLEEPTIME);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    	}
		String[] files = fileList.toArray(new String[fileList.size()]);
		HashMap<String, Control> controls = new HashMap<String, Control>();
		for (String name : files) {
			Control c = new Control(name, myName);
			controls.put(name, c);
			System.out.println(name);
		}
		System.out.println("Enquiry file list from " + to + ".");
		return controls;
	}
	
	private static void requestToQuorum(Control control, String timestamp) {
		try {
			for (int id : quorumList) {
				String member = Global.CLIENTPREFIX + id;
				Socket s = clientSockets[id];
				Message newM = new Message(CMD.Request, myName, member, control.name, timestamp, "");
				Global.toSocket(s, newM, outputCounter);
			}
		} catch (IOException e) {
            e.printStackTrace();
        }
		control.receiveFailed = false;
	}
	
	private static void releaseToQuorum(Control control, String timestamp) {
		try {
			for (int id : quorumList) {
				String member = Global.CLIENTPREFIX + id;
				Socket s = clientSockets[id];
				Message newM = new Message(CMD.Release, myName, member, control.name, timestamp, "");
				Global.toSocket(s, newM, outputCounter);
			}
		} catch (IOException e) {
            e.printStackTrace();
        }
	}

	private static void read(Socket socket, Control control, String timestamp) {
		// request to quorum
		if (Global.LOGLEVEL2) {
			System.out.println("New read: " + myName + " " + control.name + " " + timestamp);
		}
		requestToQuorum(control, timestamp);
		control.initReceiveReply(quorumList);
		int count = 0;
		boolean printFlag = false;
		while (!control.receiveAllReply()) {
			try {
				Thread.sleep(Global.SLEEPTIME);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			count += 1;
			if (count > 50 && !printFlag) {
				System.out.println("Wait for request in read: " + myName + " " + control.name + " " + timestamp);
				control.printReceiveReply();
				printFlag = true;
			}
		}
		control.using = true;
		// send message to server
		String to = Global.SERVERPREFIX + getOppositeServerID(socket);
		try {
			Message m = new Message(CMD.Read, myName, to, control.name, timestamp, "");
			Global.toSocket(socket, m, outputCounter);
        } catch (IOException e) {
            e.printStackTrace();
        }
		// receive last line of target file
		count = 0;
		printFlag = false;
		while (!control.finish) {
    		try {
				Thread.sleep(Global.SLEEPTIME);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    		count += 1;
			if (count > 50 && !printFlag) {
				System.out.println("Wait for finish in read: " + myName + " " + control.name + " " + timestamp);
				control.printReceiveReply();
				printFlag = true;
			}
    	}
		String lastLine = control.sentence;
		if (Global.LOGLEVEL2) {
			System.out.println("Read last line from " + control.name + ": " + lastLine);
		}
		control.finish = false;
		// release to quorum
		releaseToQuorum(control, timestamp);
    	control.using = false;
	}
    
    private static void write(Socket[] sockets, Control control, String timestamp) {
    	if (Global.LOGLEVEL2) {
			System.out.println("New write: " + myName + " " + control.name + " " + timestamp);
		}
    	// request to quorum
    	requestToQuorum(control, timestamp);
		control.initReceiveReply(quorumList);
		int count = 0;
		boolean printFlag = false;
		while (!control.receiveAllReply()) {
			try {
				Thread.sleep(Global.SLEEPTIME);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			count += 1;
			if (count > 50 && !printFlag) {
				System.out.println("Wait for request in write: " + myName + " " + control.name + " " + timestamp);
				control.printReceiveReply();
				printFlag = true;
			}
		}
		control.using = true;
    	// send message to server
    	String newLine = myName.substring(1) + ", " + timestamp;
    	control.sentence = newLine;
    	for (int i = 0; i < Global.SERVERNUM; i++) {
    		Socket socket = sockets[i];
    		String to = Global.SERVERPREFIX + getOppositeServerID(socket);
        	try {
    			Message m = new Message(CMD.Write, myName, to, control.name, timestamp, newLine);
    			Global.toSocket(socket, m, outputCounter);
            } catch (IOException e) {
                e.printStackTrace();
            }
    	}
    	count = 0;
    	printFlag = false;
    	while (!control.allFinishWrite()) {
    		try {
				Thread.sleep(Global.SLEEPTIME);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    		count += 1;
			if (count > 50 && !printFlag) {
				System.out.println("Wait for finish in write: " + myName + " " + control.name + " " + timestamp);
				control.printReceiveReply();
				printFlag = true;
			}
    	}
    	if (Global.LOGLEVEL2) {
    		System.out.println("Write new line to " + control.name + ": " + newLine);
    	}
    	control.initFinishWrite();
    	// release to quorum
		releaseToQuorum(control, timestamp);
    	control.using = false;
	}
    
    private static int getOppositeServerID(Socket s) {
		String ip = s.getInetAddress().toString();
    	if (ip.charAt(0) == '/') {
    		ip = ip.substring(1);  // remove possible /
    	}
    	for (int i = 0; i < Global.SERVERNUM; i++) {
    		if (Global.serverIPs[i].equals(ip)) {
    			return i;
    		}
    	}
    	return -1;
	}
    
    public static class S2CThreadHandler implements Runnable {

    	private Socket socket;
    	private String from;
        
        public S2CThreadHandler(Socket s) throws IOException {
        	this.socket = s;
        	this.from = Global.SERVERPREFIX + getOppositeServerID(this.socket);
        }
        
        @Override
        public void run() {
    		while (true) {
    			Message m = Global.fromSocket(this.socket, inputCounter);
    			String file = m.file;
				String sentence = m.sentence;
				int id = Global.getID(m.from);
    			switch (m.cmd) {
	    			case 0: {  // Message
	    				System.out.println(m.sentence);
	    				break;
	    			}
	    			case 1: {  // Enquiry
	    				fileList.add(file);
	    				break;
	    			}
	    			case 2: {  // Read
	    				Control c = controlMap.get(file);
	    				if (c.name.equals(file)) {
    						c.sentence = sentence;
    						c.finish = true;
    					}
	    				if (Global.LOGLEVEL2) {
	    					System.out.println("Server " + m.from + " finish reading from " + from + ": " + sentence);
	    				}
	    				break;
	    			}
        			case 3: {  // Write
        				Control c = controlMap.get(file);
        				if (c.name.equals(file)) {
    						c.finishWrite[id] = true;
    					}
        				if (Global.LOGLEVEL2) {
        					System.out.println("Server " + m.from + " finish writing from " + from + ": " + sentence);
        				}
	    				break;
	    			}
        			case 4: {  // Finish Enquiry
        				finishEnquiry = true;
	    				System.out.println("Server " + m.from + " finish enquirying from " + from + ".");
	    				break;
        			}
        			case 5 : {  // Exit
        				try {
							this.socket.shutdownInput();
							this.socket.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
        				break;
        			}
	    			default: {
	    				System.out.println("Unvalid message received: cmd(" + m.cmd + ")");
	    				System.out.println(m.toString());
	    				break;
	    			}
    			}
    		}
        }
    }
    
    private static int getOppositeClientID(Socket s) {
		String ip = s.getInetAddress().toString();
    	if (ip.charAt(0) == '/') {
    		ip = ip.substring(1);  // remove possible /
    	}
    	for (int i = 0; i < Global.CLIENTNUM; i++) {
    		if (Global.clientIPs[i].equals(ip)) {
    			return i;
    		}
    	}
    	return -1;
	}
	
	public static class C2CThreadHandler implements Runnable {

    	private Socket socket;
    	private String oppo;
        
        public C2CThreadHandler(Socket s) throws IOException {
        	this.socket = s;
        	this.oppo = Global.CLIENTPREFIX + getOppositeClientID(this.socket);
        }
        
        @Override
        public void run() {
        	try {
	    		while (true) {
	    			Message m = Global.fromSocket(this.socket, inputCounter);
	    			String file = m.file;
	    			String timestamp = m.timestamp;
	    			switch (m.cmd) {
		    			case 5 : {  // Exit
	        				agreeToExit[Global.getID(this.oppo)] = true;
	        				printAgreeToExit();
	        				break;
	        			}
		    			case 6: {  // Request
		    				Control c = controlMap.get(file);
		    				Task t = new Task(this.oppo, timestamp);
		    				if (c.lockFor == null) {
		    					c.lockFor = t;
		    					Message newM = new Message(CMD.Reply, myName, this.oppo, file, timestamp, "");
		    					Global.toSocket(this.socket, newM, outputCounter);
		    				} else {
		    					boolean failedFlag = false;
		    					if (c.lockFor.higherPriority(t)) {
		    						failedFlag = true;
		    					}
		    					if (c.queue.size() > 0 && c.queue.get(0).higherPriority(t)) {
		    						failedFlag = true;
		    					}
		    					c.addToQueue(t);
		    					if (failedFlag) {
		    						Message newM = new Message(CMD.Failed, myName, this.oppo, file, timestamp, "");
			    					Global.toSocket(this.socket, newM, outputCounter);
		    					} else {
		    						if (c.waitInquire != true) {
		    							Message newM = new Message(CMD.Inquire, myName, t.client, file, timestamp, "");
		    							Socket s = clientSockets[Global.getID(t.client)];
				    					Global.toSocket(s, newM, outputCounter);
				    					c.waitInquire = true;
		    						}
		    					}
		    				}
		    				break;
		    			}
		    			case 7: {  // Reply
		    				Control c = controlMap.get(file);
		    				int id = Global.getID(this.oppo);
		    				c.receiveReply[id] = true;
		    				c.printReceiveReply();
		    				break;
		    			}
		    			case 8: { // Failed
		    				Control c = controlMap.get(file);
		    				c.receiveFailed = true;
		    				c.waitInquire = false;
		    				break;
		    			}
		    			case 9: {  // Inquire
		    				Control c = controlMap.get(file);
		    				if (c.receiveFailed) {
		    					for (int id : quorumList) {
		    						String member = Global.CLIENTPREFIX + id;
		    						Socket s = clientSockets[id];
		    						Message newM = new Message(CMD.Relinquish, myName, member, file, timestamp, "");
		    						Global.toSocket(s, newM, outputCounter);
		    					}
		    				}
		    				break;
		    			}
		    			case 10: {  // Relinquish
		    				Control c = controlMap.get(file);
		    				if (c.lockFor == null) {
		    					if (c.queue.size() > 0) {
		    						c.lockFor = c.queue.get(0);
			    					c.queue.remove(0);
			    				}
		    				} else {
		    					c.addToQueue(c.lockFor);
		    					c.lockFor = c.queue.get(0);
		    					c.queue.remove(0);
		    				}
		    				if (c.lockFor != null) {
		    					Task t = c.lockFor;
		    					Socket tSocket = clientSockets[Global.getID(t.client)];
		    					Message newM = new Message(CMD.Reply, myName, t.client, file, timestamp, "");
		    					Global.toSocket(tSocket, newM, outputCounter);
		    				}
		    				break;
		    			}
		    			case 11: {  // Release
		    				Control c = controlMap.get(file);
		    				c.lockFor = null;
		    				if (c.queue.size() > 0) {
		    					c.lockFor = c.queue.get(0);
		    					c.queue.remove(0);
		    				}
		    				if (c.lockFor != null) {
		    					Task t = c.lockFor;
		    					Socket tSocket = clientSockets[Global.getID(t.client)];
		    					Message newM = new Message(CMD.Reply, myName, t.client, file, timestamp, "");
		    					Global.toSocket(tSocket, newM, outputCounter);
		    				}
		    				c.waitInquire = false;
		    				break;
		    			}
		    			default: {
		    				System.out.println("Unvalid message received: cmd(" + m.cmd + ")");
		    				System.out.println(m.toString());
		    				break;
		    			}
	    			}
	    		}
        	} catch (IOException e) {
				e.printStackTrace();
			}
        }
    }
    
    private static boolean allClientClose() {
		for (Socket socket : serverSockets) {
			if (socket == null || !socket.isClosed()) {
				return false;
			}
		}
		return true;
	}

	public static void main(String[] args) {
		
		// init
    	if(args.length != 1) {
			System.out.println("Wrong input! Try like: jar client.jar <client id>");
			System.exit(1);
		}
    	myName = Global.CLIENTPREFIX + args[0];
    	int clientID = Integer.parseInt(args[0]);
    	quorumList = new int[] {(clientID+1) % Global.CLIENTNUM, (clientID+2) % Global.CLIENTNUM};
		random.setSeed(clientID);
		for (int i = 0; i < agreeToExit.length; i++) {
			agreeToExit[i] = false;
		}
		// connect to server
		ExecutorService executor = Executors.newFixedThreadPool(Global.SERVERNUM + Global.CLIENTNUM);
		try {
			for (int i = 0; i < Global.SERVERNUM; i++) {
				Socket socket = new Socket(Global.serverIPs[i], Global.serverPorts[i]);
				serverSockets[i] = socket;
				executor.submit(new S2CThreadHandler(socket));
				System.out.println("Connect to server " + i + ".");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Connect to all servers.");
		// connect to client
		ServerSocket server = null;
		try {
			server = new ServerSocket(Global.clientPorts[clientID]);
			for (int i = 0; i < clientID; i++) {
				Socket socket = new Socket(Global.clientIPs[i], Global.clientPorts[i]);
				clientSockets[i] = socket;
				executor.submit(new C2CThreadHandler(socket));
				System.out.println("Connect to client " + i + ".");
			}
			for (int i = clientID + 1; i < Global.CLIENTNUM; i++) {
				Socket socket = server.accept();
				int id = getOppositeClientID(socket);
				clientSockets[id] = socket;
				executor.submit(new C2CThreadHandler(socket));
				System.out.println("Connect to client " + id + ".");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				server.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Connect to all clients.");
		// enquire hosted file list
    	controlMap = enquiry(getRandomSocket());
    	System.out.println("Begin random actions.");
    	// read or write
    	for (int r = 0; r < ROUND_TIME; r++) {  // terminate after loop enough times
    		int actionNum = random.nextInt(2);  // 0 or 1
    		if (Global.LOGLEVEL2) {
    			System.out.println("----------------------------------------");
    		}
    		if (actionNum == 0) {  // read
    			read(getRandomSocket(), getRandomFileControl(), Global.getLocalTimestamp());
    		} else {  // write
    			write(serverSockets, getRandomFileControl(), Global.getLocalTimestamp());
    		}
    	}
    	System.out.println("Finish all tasks.");
    	// wait for other clients
    	agreeToExit[Global.getID(myName)] = true;
    	printAgreeToExit();
    	for (int i = 0; i < Global.CLIENTNUM; i++) {
    		if (i == Global.getID(myName)) {
    			agreeToExit[i] = true;
    	    	printAgreeToExit();
    		} else {
    			Socket socket = clientSockets[i];
    			try {
        			String to = Global.CLIENTPREFIX + getOppositeServerID(socket);
        			Message m = new Message(CMD.Exit, myName, to, "", "", "");
        			Global.toSocket(socket, m, outputCounter);
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    		}
    	}
    	while (!allAgreeToExit()) {
    		try {
				Thread.sleep(Global.SLEEPTIME);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    	}
    	System.out.println("Ready to exit now.");
    	// clean before close
    	for (Socket socket : serverSockets) {
    		try {
    			String to = Global.SERVERPREFIX + getOppositeServerID(socket);
    			Message m = new Message(CMD.Exit, myName, to, "", "", "");
    			Global.toSocket(socket, m, outputCounter);
				socket.shutdownOutput();
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
    	while (!allClientClose()) {
			try {
				Thread.sleep(Global.SLEEPTIME);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
    	for (Socket socket : clientSockets) {
    		if (socket == null) {
    			continue;
    		}
    		try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
    	try {
    		executor.shutdown();
			executor.awaitTermination(500, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    	inputCounter.print("input");
    	outputCounter.print("output");
    	System.out.println("Client " + myName + " close.");

	}

}

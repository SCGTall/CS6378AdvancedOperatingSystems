package client;

import java.io.IOException;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import global.Global;
import global.Global.Message;
import global.Global.CMD;

public class Client {
	
	private static final int ROUND_TIME = 20;
	private static String myName;
	private static Socket[] serverSockets = new Socket[Global.SERVERNUM];
	private static ArrayList<String> fileList = new ArrayList<String>();
	private static Control[] controlList;
	private static Random random = new Random();
	private static boolean finishEnquiry = false;
	
	private static class Control {
		public String name;
		public boolean[] finishWrite = new boolean[Global.SERVERNUM];
		public boolean finish;
		public String sentence;
		@SuppressWarnings("unused")
		public boolean using;
		
		public Control(String fileName) {
			this.name = fileName;
			this.initFinishWrite();
			this.finish = false;
			this.using = false;
		}
		
		public void initFinishWrite() {
			for (int i = 0; i < Global.SERVERNUM; i++) {
				this.finishWrite[i] = false;
			}
		}
		
		public boolean allFinishWrite() {
			for (boolean b : this.finishWrite) {
				if (b == false) {
					return false;
				}
			}
			return true;
		}
	}
	
	private static Socket getRandomSocket() {
		int serverID = random.nextInt(serverSockets.length);
		return serverSockets[serverID];
	}
	
	private static Control getRandomFile() {
		if (controlList.length == 0) {
			return null;
		} else {
			return controlList[random.nextInt(controlList.length)];
		}
	}
	
	private static String getLocalTimestamp() {
		return new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
	}
	
	private static int getOppositeServerID(Socket s) {
		String ip = s.getInetAddress().toString();
    	if (ip.charAt(0) == '/') {
    		ip = ip.substring(1);  // remove possible /
    	}
    	for (int i = 0; i < serverSockets.length; i++) {
    		if (Global.serverIPs[i].equals(ip)) {
    			return i;
    		}
    	}
    	return -1;
	}
	
	private static Control[] enquiry(Socket socket) {
		String to = Global.SERVERPREFIX + getOppositeServerID(socket);
		try {
			Message m = new Message(socket, CMD.Enquiry, myName, to, "", "", "");
			Global.toSocket(socket, m);
        } catch (IOException e) {
            e.printStackTrace();
        }
		while (!finishEnquiry) {
    		try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    	}
		String[] files = fileList.toArray(new String[fileList.size()]);
		Control[] controls = new Control[files.length];
		for (int i = 0; i < files.length; i++) {
			controls[i] = new Control(files[i]);
			System.out.println(files[i]);
		}
		System.out.println("Enquiry file list from " + to + ".");
		return controls;
	}

	private static void read(Socket socket, Control control, String timestamp) {
		control.using = true;
		String to = Global.SERVERPREFIX + getOppositeServerID(socket);
		try {
    		// send message to server
			Message m = new Message(socket, CMD.Read, myName, to, control.name, timestamp, "");
			Global.toSocket(socket, m);
        } catch (IOException e) {
            e.printStackTrace();
        }
		// receive last line of target file
		while (!control.finish) {
    		try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    	}
		String lastLine = control.sentence;
		System.out.println("Read last line from " + control.name + ": " + lastLine);
		control.finish = false;
		control.using = false;
	}
    
    private static void write(Socket[] sockets, Control control, String timestamp) {
    	control.using = true;
    	String newLine = myName.substring(1) + ", " + timestamp;
    	control.sentence = newLine;
    	for (int i = 0; i < Global.SERVERNUM; i++) {
    		Socket socket = sockets[i];
    		String to = Global.SERVERPREFIX + getOppositeServerID(socket);
        	try {
        		// send message to server
    			Message m = new Message(socket, CMD.Write, myName, to, control.name, timestamp, newLine);
    			Global.toSocket(socket, m);
            } catch (IOException e) {
                e.printStackTrace();
            }
    	}
    	while (!control.allFinishWrite()) {
    		try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    	}
    	System.out.println("Write new line to " + control.name + ": " + newLine);
    	control.initFinishWrite();
    	control.using = false;
	}
    
    private static int getOppositeID(Socket s) {
		String ip = s.getInetAddress().toString();
    	if (ip.charAt(0) == '/') {
    		ip = ip.substring(1);  // remove possible /
    	}
    	for (int i = 0; i < serverSockets.length; i++) {
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
        	this.from = Global.SERVERPREFIX + getOppositeID(this.socket);
        }
        
        @Override
        public void run() {
    		while (true) {
    			Message m = Global.fromSocket(this.socket);
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
	    				for (Control c : controlList) {
	    					if (c.name.equals(file)) {
	    						c.sentence = sentence;
	    						c.finish = true;
	    					}
	    				}
	    				System.out.println("Server " + m.from + " finish reading from " + from + ": " + sentence);
	    				break;
	    			}
        			case 3: {  // Write
	    				for (Control c : controlList) {
	    					if (c.name.equals(file)) {
	    						c.finishWrite[id] = true;
	    					}
	    				}
	    				System.out.println("Server " + m.from + " finish writing from " + from + ": " + sentence);
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
    	myName = Global.SERVERPREFIX + args[0];
    	int clientID = Integer.parseInt(args[0]);
		random.setSeed(clientID);
		// connect to server
		ExecutorService executor = Executors.newFixedThreadPool(Global.SERVERNUM);
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
		// enquire hosted file list
    	controlList = enquiry(getRandomSocket());
    	System.out.println("Begin random actions.");
    	// read or write
    	for (int r = 0; r < ROUND_TIME; r++) {  // terminate after loop enough times
    		try {
				Thread.sleep(random.nextInt(300) + 100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    		int actionNum = random.nextInt(2);  // 0 or 1
    		if (actionNum == 0) {  // read
    			read(getRandomSocket(), getRandomFile(), getLocalTimestamp());
    		} else {  // write
    			write(serverSockets, getRandomFile(), getLocalTimestamp());
    		}
    	}
    	System.out.println("Finish all tasks.");
    	// clean before close
    	for (Socket socket : serverSockets) {
    		try {
    			String to = Global.SERVERPREFIX + getOppositeID(socket);
    			Message m = new Message(socket, CMD.Exit, myName, to, "", "", "");
    			Global.toSocket(socket, m);
				socket.shutdownOutput();
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
    	while (!allClientClose()) {
			try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
    	try {
    		executor.shutdown();
			executor.awaitTermination(500, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

}

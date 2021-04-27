package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import global.Global;
import global.Global.Message;
import global.Global.CMD;

public class Server {
	
	private static String myName;
	private static Socket[] clientSockets = new Socket[Global.CLIENTNUM];
	private static String[] fileList;
	
	private static int getOppositeID(Socket s) {
		String ip = s.getInetAddress().toString();
    	if (ip.charAt(0) == '/') {
    		ip = ip.substring(1);  // remove possible /
    	}
    	for (int i = 0; i < clientSockets.length; i++) {
    		if (Global.clientIPs[i].equals(ip)) {
    			return i;
    		}
    	}
    	return -1;
	}
	
	public static class C2SThreadHandler implements Runnable {

    	private Socket socket;
    	private String to;
        
        public C2SThreadHandler(Socket s) throws IOException {
        	this.socket = s;
        	this.to = Global.CLIENTPREFIX + getOppositeID(this.socket);
        }
        
        @Override
        public void run() {
        	try {
        		boolean flag = true;
        		while (flag) {
        			if (this.socket.isInputShutdown()) {
        				this.socket.shutdownInput();
	        			Message newM = new Message(socket, CMD.Exit, myName, to, "", "", "");
	        			Global.toSocket(socket, newM);
	    				this.socket.shutdownOutput();
        				flag = false;
        				break;
        			}
        			Message m = Global.fromSocket(this.socket);
        			switch (m.cmd) {
	        			case 0: {  // Message
		    				System.out.println(m.sentence);
		    				break;
		    			}
	        			case 1: {  // Enquiry
		    				// traverse hosted files
		    				for (String name : fileList) {
		    					Message newM = new Message(this.socket, CMD.Enquiry, myName, to, name, "", "");
		    					Global.toSocket(this.socket, newM);
		    				}
		    				// tell client to end
		    				Message newM = new Message(this.socket, CMD.FinishEnquiry, myName, to, "", "", "");
	    					Global.toSocket(this.socket, newM);
		    				System.out.println("Finish enquiry from " + to);
		    				break;
		    			}
		    			case 2: {  // Read
		    				String file = m.file;
		    				String lastLine = "";
		    				BufferedReader reader = null;
		    				try {
		    		        	reader = new BufferedReader(new FileReader(new File(file)));
		    	        		String line = reader.readLine();
		    	        		while (line != null) {
		    	        			lastLine = line;
		    	        			line = reader.readLine();
		    	        		}
		    	        	} catch (IOException e) {
		    	                e.printStackTrace();
		    	            } finally {
		    	            	try {
		                    		reader.close();
		                    	} catch (IOException e) {
		    	                    e.printStackTrace();
		    	                }
		    	            }
		    				Message newM = new Message(this.socket, CMD.Read, myName, to, file, m.timestamp, lastLine);
	    					Global.toSocket(this.socket, newM);
		    				System.out.println(to + " read from " + file + ": " + lastLine);
		    				break;
		    			}
		    			case 3: {  // Write
		    				String file = m.file;
		    				String newLine = m.sentence;
		    				BufferedWriter writer = null;
		    				try {
		    					writer = new BufferedWriter(new FileWriter(new File(file), true));
		    					writer.write(newLine);
		    	        		writer.newLine();
		    	        		writer.flush();
		    	        		writer.close();
		    				} catch (IOException e) {
		    	                e.printStackTrace();
		    	            } finally {
		    	            	try {
		                    		writer.close();
		                    	} catch (IOException e) {
		    	                    e.printStackTrace();
		    	                }
		    	            }
		    				Message newM = new Message(this.socket, CMD.Write, myName, to, file, m.timestamp, newLine);
	    					Global.toSocket(this.socket, newM);
		    				System.out.println(to + " write to " + file + ": " + newLine);
		    				break;
		    			}
		    			case 5: {  // Exit
		    				this.socket.shutdownInput();
		        			Message newM = new Message(socket, CMD.Exit, myName, to, "", "", "");
		        			Global.toSocket(socket, newM);
		    				this.socket.shutdownOutput();
	        				flag = false;
		    				break;
		    			}
		    			default: {
		    				System.out.println("Unvalid message received: " + m.toString());
		    				flag = false;
		    				break;
		    			}
        			}
        		}
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
            	try {
            		this.socket.close();
            		System.out.println("Close socket to " + to + ".");
            	} catch (IOException e) {
                    e.printStackTrace();
                }
			}
        }
    }
	
	private static boolean allClientClose() {
		for (Socket socket : clientSockets) {
			if (socket == null || !socket.isClosed()) {
				return false;
			}
		}
		return true;
	}

	public static void main(String[] args) {
		
		// init
    	if(args.length != 1) {
			System.out.println("Wrong input! Try like: jar server.jar <server id>");
			System.exit(1);
		}
    	myName = Global.SERVERPREFIX + args[0];
    	String parentDir = new File(Global.SERVERSRC).getAbsoluteFile().getParent().toString();
    	File parent = new File(parentDir);
    	File[] files = parent.listFiles();
    	ArrayList<String> otherFiles = new ArrayList<String>();
    	for (File f : files) {
    		String name = f.getName();
    		if (name .equals(Global.SERVERSRC)) {
    			continue;
    		} else {
    			otherFiles.add(name);
    		}
    	}
    	fileList = otherFiles.toArray(new String[otherFiles.size()]);
    	// execute
    	int port = Global.serverPorts[Global.getID(myName)];
    	ExecutorService executor = null;
    	ServerSocket server = null;
    	try {
    		executor = Executors.newFixedThreadPool(Global.CLIENTNUM);
			server = new ServerSocket(port);
			// connect to clients
			System.out.println("Listening...");
			for (int i = 0; i < Global.CLIENTNUM; i++) {
				Socket socket = server.accept();
				String to = Global.CLIENTPREFIX + getOppositeID(socket);
				Message m = new Message(socket, CMD.Message, myName, to, "", "", "Hello from " + myName + ".");
				Global.toSocket(socket, m);
				int id = getOppositeID(socket);
				clientSockets[id] = socket;
				executor.submit(new C2SThreadHandler(socket));
				System.out.println("Client " + Global.CLIENTPREFIX + id + " connected.");
			}
			while (!allClientClose()) {
				Thread.sleep(250);
			}
			System.out.println("Clear up remaining threads and sockets.");
	    	// do before exit
	    	executor.shutdown();
	    	executor.awaitTermination(500, TimeUnit.MILLISECONDS);
			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			try {
				executor.shutdown();
				executor.awaitTermination(500, TimeUnit.MILLISECONDS);
				server.close();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

}

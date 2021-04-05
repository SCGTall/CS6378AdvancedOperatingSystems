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
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyServer {
	
	// finals
	private static final int SERVER_PORT = 8899;
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
	
    private static int serverID;
    private static int exitClientNum = 0;
    // possible dirty read. But it would cause bad result.
    private static ArrayList<Integer> agreeToExit = new ArrayList<Integer>();
    private static File folder;
    private static ArrayList<MySocketObject> serverLan = new ArrayList<MySocketObject>();
    
    private static class Input {
    	private int cmd;
    	private int len;
    	private String message;
    	
    	public Input(int cmd, int len, String msg) {
    		this.cmd = cmd;
    		this.len = len;
    		this.message = msg;
    	}
    	
    	public void print() {
    		System.out.println("Input:|" + Integer.toString(cmd) + "|" + Integer.toString(len) + "|" + message + "|");
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
		System.out.println("Output:|" + cmd.getValue() + "|" + Integer.toString(s.length()) + "|" + s + "|");
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
		inp.print();
		return inp;
    }
    
    // create lan and listen to clients
    public static class ListenerThreadHandler extends Thread {
    	
        private int port;
        private ExecutorService executor;
        private ServerSocket server;
        private int totalNum;
        private int currentNum = 0;
        
        public ListenerThreadHandler(int port, ExecutorService executor, ServerSocket server, int threadNum) throws IOException {
        	this.port = port;
        	this.server = server;
        	this.executor = executor;
        	this.totalNum = threadNum;
        }
        
        @Override
        public void run() {
            try {
            	// start listening
            	System.out.println("Start listening...");
        		// connect with existing server. Make sure that all servers are executed by turn.
        		for (int i = 0; i < serverID; i++) {
        			Socket s = new Socket(serverList[i], port);
        			executor.execute(new S2SThreadHandler(s));
        			currentNum += 1;
        		}
                while (currentNum < totalNum) {  // end loop when all sockets are established
                	Socket s = server.accept();
                	//System.out.println("ip: " + s.getInetAddress().toString());
                	String ip = s.getInetAddress().toString().substring(1);
                	int id = getServerIndex(ip);
                	if (id < 0) {  // id not found
                		executor.execute(new C2SThreadHandler(s));
                	} else {
                		executor.execute(new S2SThreadHandler(s));
                	}
                	currentNum += 1;
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
    }
    
    
    // server to server
    public static class S2SThreadHandler extends Thread {
    	
        private MySocketObject s;
        private boolean flag = true;
        
        public S2SThreadHandler(Socket s) throws IOException {
            this.s = new MySocketObject(s);
            serverLan.add(this.s);  // record in serverLan in order to be found when need to close.
            start();
        }
        
        @Override
        public void run() {
            try {
        		DataInputStream din = s.getDataInputStream();
        		DataOutputStream dout = s.getDataOutputStream();
        		int cmd;
        		while (this.flag) {
        			Input inp = fromSocket(din);
        			cmd = inp.getCMD();
        			// use cmd to decide coming action
        			switch (cmd) {
	        			case 1: {  // Enquiry
    	    				// traverse hosted files
    	    				for (File f : folder.listFiles()) {
    	    					toSocket(dout, CMD.Enquiry, f.getName());
    	    				}
    	    				// tell client to end
    	    				toSocket(dout, CMD.Next, "_");
    	    				break;
    	    			}
    	    			default: {
    	    				System.out.print("Unvalid cmd received: " + cmd);
    	    				flag = false;
    	    				break;
    	    			}
        			}
        		}
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
    }
    
    
    // client to server
    public static class C2SThreadHandler extends Thread {

    	private Socket s;
        
        public C2SThreadHandler(Socket s) {
            this.s = s;
            start();
        }
        
        @Override
        public void run() {
        	try {
            	InputStream in  = s.getInputStream();
            	DataInputStream din = new DataInputStream(in);
            	OutputStream out = s.getOutputStream();
            	DataOutputStream dout = new DataOutputStream(out);
        		int cmd;
        		boolean flag = true;
        		while (flag) {
        			Input inp = fromSocket(din);
        			cmd = inp.getCMD();
        			// use cmd to decide coming action
        			switch (cmd) {
	        			case 1: {  // Enquiry
    	    				// traverse hosted files
    	    				for (File f : folder.listFiles()) {
    	    					toSocket(dout, CMD.Enquiry, f.getName());
    	    				}
    	    				// tell client to end
    	    				toSocket(dout, CMD.Next, "_");
    	    				break;
    	    			}
    	    			case 2: {  // Read
    	    				String readDir = folder.getName() + "/" + inp.getMSG();
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
    	    				toSocket(dout, CMD.Read, lastLine);
    	    				// tell client to end
    	    				toSocket(dout, CMD.Next, "_");
    	    				break;
    	    			}
    	    			case 3: {  // Write
    	    				String writeDir = folder.getName() + "/" + inp.getMSG();
    		        		// tell client to do next
    	    				toSocket(dout, CMD.Next, "_");
    	    				Input inp2 = fromSocket(din);
    	    				if (inp2.getCMD() != CMD.Write.getValue()) {
    	    	    			System.out.println("Wrong CMD code happen in Write! CMD: " + Integer.toString(inp2.getCMD()));
    	    	    		}
    		        		String newLine = inp2.getMSG() + "\n";
    		        		// write to file
    		        		Files.write(Paths.get(writeDir), newLine.getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
    		        		// tell client to end
    		        		toSocket(dout, CMD.Next, "_");
    	    				break;
    	    			}
    	    			case 5: {  // Exit
    	    				flag = false;
    	    				break;
    	    			}
    	    			case 0:  // Message
    	    			case 4:  // Next
    	    			default: {
    	    				System.out.print("Unvalid cmd received: " + cmd);
    	    				flag = false;
    	    				break;
    	    			}
        			}
        		}
        		din.close();
            	dout.close();
            	s.close();  // close client to server socket after client sent Exit command.
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
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
    	for (int i = 0; i < serverList.length; i++) {
    		agreeToExit.add(0);
    	}
    	// copy beginning files from ORIGINAL_HOSTED
    	try {
    		String parentDir = new File(JAR_NAME).getAbsoluteFile().getParentFile().getParent().toString();
        	File original = new File(parentDir + "/" + ORIGINAL_HOSTED);
        	if (!original.exists()) {
        		System.out.println("Original hosted files are missing!");
    			System.exit(1);
        	}
        	folder = new File(FOLDER_DIR);
        	if (folder.exists()) {
        		deleteAll(folder);
        	}
        	folder.mkdir();
        	for (File f : original.listFiles()) {
        		File dest = new File(folder.getName() + "/" + f.getName());
        		copyFileUsingFileChannels(f, dest);
        	}
    	} catch (IOException e) {
            e.printStackTrace();
        }
		try {
			// create a listener to connect with clients and servers
	    	// use a thread pool to manage all threads (with at most 5(clients) + 2(other servers) = 8 threads)
	    	int threadNum = serverList.length - 1 + CLIENT_NUM;
	    	ExecutorService executor = Executors.newFixedThreadPool(threadNum);
	    	ServerSocket server;
			server = new ServerSocket(SERVER_PORT);
			ListenerThreadHandler listener = new ListenerThreadHandler(SERVER_PORT, executor, server, threadNum);
	    	listener.start();
	    	// wait until exit
	    	while (true) {
	    		if (exitClientNum >= CLIENT_NUM) {  // current server agree that it is ready to exit.
	    			if (agreeToExit.get(serverID) != 0) {  // do this only once
	    				for (MySocketObject s : serverLan) {  // broadcast to other servers
	    					DataOutputStream dout = s.getDataOutputStream();
	    					toSocket(dout, CMD.Exit, "_");
	    		        }
	    				agreeToExit.set(serverID, 1);
	        		}
	    		}
	    		// possbile dirty read exists. But it will not cause wrong exit
	    		int sum = 0;
	    		for (int i = 0; i < agreeToExit.size(); i++) {
	    			sum += agreeToExit.get(i);
	    		}
	    		if (sum >= agreeToExit.size()) {  // all servers agree that they can exit.
	    			break;
	    		}
	    		Thread.sleep(100);
	    	}
	    	// do before exit
	    	executor.shutdownNow();
	        server.close();
	        // close all sockets
	        for (MySocketObject s : serverLan) {
	        	s.getDataInputStream().close();
	        	s.getDataOutputStream().close();
	        	s.getSocket().close();
	        }
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
        System.out.println("Exit after all works are done.");
    }
}
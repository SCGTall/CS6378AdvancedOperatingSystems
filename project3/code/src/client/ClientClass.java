package client;

import java.util.ArrayList;

import global.Global;

public class ClientClass {
	public static class Task {
		public String client;
		public String timestamp;
		
		public Task(String c, String ts) {
			this.client = c;
			this.timestamp = ts;
		}
		
		public boolean higherPriority(Task t2) {
			if (t2 == null) {
				return true;
			}
			if (this.timestamp.equals(t2.timestamp)) {
				return Global.getID(client) < Global.getID(t2.client);
			} else {
				return Global.compareTimestamp(this.timestamp, t2.timestamp);
			}
		}
	}
	
	public static class Control {
		public String thisClient;
		public String name;
		public boolean[] finishWrite = new boolean[Global.SERVERNUM];
		public boolean finish = false;
		public String sentence;
		public Task lockFor = null;
		public ArrayList<Task> queue = new ArrayList<Task>();
		public boolean waitInquire = false;
		public boolean receiveFailed = false;
		public boolean using = false;
		public boolean[] receiveReply = new boolean[Global.CLIENTNUM];
		
		public Control(String fileName, String id) {
			this.name = fileName;
			this.thisClient = id;
			this.initFinishWrite();
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
		
		public void addToQueue(Task newT) {
			boolean flag = false;
			for (int i = 0; i < this.queue.size(); i++) {
				Task curT = this.queue.get(i);
				if (Global.compareTimestamp(newT.timestamp, curT.timestamp)) {
					this.queue.add(i, newT);
					flag = true;
					break;
				}
			}
			if (!flag) {
				this.queue.add(newT);
			}
		}
		
		public boolean getNextTask() {  // return true if new task is locked
			if (this.lockFor == null) {
				if (this.queue.size() > 0) {
					this.lockFor = this.queue.get(0);
					this.queue.remove(0);
					return true;
				} else {
					return false;
				}
			} else {
				Task curT  = this.lockFor;
				if (this.queue.size() > 0) {
					Task candT = this.queue.get(0);
					if (curT.higherPriority(candT)) {
						return false;
					} else {
						this.lockFor = candT;
						this.queue.set(0, curT);
						return true;
					}
				} else {
					return false;
				}
			}
		}
		
		public void initReceiveReply(int[] quorumList) {
			for (int i = 0; i < this.receiveReply.length; i++) {
				this.receiveReply[i] = true;
			}
			for (int i : quorumList) {
				this.receiveReply[i] = false;
			}
			printReceiveReply();
		}
		
		public void printReceiveReply() {
			if (Global.LOGLEVEL2) {
				System.out.print("State (" + name + "): ");
				for (boolean b : this.receiveReply) {
					System.out.print("\t");
					System.out.print(b);
				}
				System.out.print("\n");
			}
		}
		
		public boolean receiveAllReply() {
			boolean flag = true;
			for (boolean b : this.receiveReply) {
				if (b == false) {
					flag = false;
				}
			}
			return flag;
		}
	}

}

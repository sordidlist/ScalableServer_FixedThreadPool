package cs455.scaling.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import cs455.scaling.server.tasks.AcceptIncomingTrafficTask;
import cs455.scaling.server.tasks.ComputeHashTask;
import cs455.scaling.server.tasks.ReplyToClientTask;
import cs455.scaling.server.tasks.Task;

public class WorkerThread implements Runnable {

	private final int workerThreadID;
	private Task currentTask;
	private LinkedList<WorkerThread> idleThreads;
	private int numConnections;
	private Selector selector;
	private boolean debug;
	private boolean shutDown;
	private boolean idle;
	
	public Object sleepLock;
	
	public WorkerThread(LinkedList<WorkerThread> idleThreads, int id, boolean debug) {
		this.debug = debug; 
		this.workerThreadID = id;
		this.shutDown = false;
		this.idleThreads = idleThreads;
		this.currentTask = null;
		this.sleepLock = new Object();
		this.numConnections = 0;

		try {
			this.selector = Selector.open();
		} catch (IOException e) {
			System.out.println(e);
		}
	}
	
	@Override
	public void run() {
		if (debug) System.out.println("  New worker thread " + workerThreadID + " executed.");
		while (!shutDown) {
			if (currentTask != null) {
				if (debug) System.out.println("  Worker thread " + workerThreadID + " has a task.");
				processTask();
			}
			else {
				reportIdle();
				
				//if (debug) System.out.println(" Worker thread " + workerThreadID + " waiting for task.");

				//if (debug) System.out.println(" Worker thread " + workerThreadID + " received task.");
			
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private void processTask() {
		ByteBuffer buffer = ByteBuffer.allocate(8192);
		
		if (currentTask instanceof AcceptIncomingTrafficTask) {
			if (debug) System.out.println("Worker thread " + workerThreadID + " reading data from channel...");
			
			SelectionKey key = ((AcceptIncomingTrafficTask) currentTask).getKey();
			ArrayList<Byte> payload = new ArrayList<Byte>();
			synchronized (key) {
				SocketChannel clientChannel = (SocketChannel) key.channel();
				//ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
				int read = 0;
				try {
					while (buffer.hasRemaining() && read != -1) {
						read = clientChannel.read(buffer);
		                //buffer.flip();
					}
					if (debug) System.out.println("...Data read from channel.  read: " + read);
					buffer.rewind();
					/*while (buffer.hasRemaining()){
						if (debug) System.out.print((char) buffer.get());
					}
					buffer.rewind();
					*/
					while (buffer.hasRemaining()) {
						payload.add(buffer.get());
					}
					if (debug) System.out.println();
					buffer.clear();
					buffer.flip();
					
					byte[] payloadBytes = new byte[payload.size()];
					for (int i = 0; i < payload.size(); i++){
						payloadBytes[i] = payload.get(i);
					}
					ComputeHashTask computeHashTask = new ComputeHashTask(payloadBytes);
					currentTask = computeHashTask;
				} catch (IOException e) {
					// Abnormal termination
					
					/*
					 *  ADD SERVER DISCONNECT HERE
					 *  server.disconnect(key);
					 *  return;
					 */
				}
				if (read == -1) {
					// Connection terminated by client
					
					/*
					 *  server.disconnect(key);
					 *  return;
					 */
				}
				//if (debug) System.out.println(" Switching key interest to WRITE");
				//key.interestOps(SelectionKey.OP_WRITE);
			}
		}
		if (currentTask instanceof ComputeHashTask) {
			if (debug) System.out.println("Worker thread " + workerThreadID + " computing hash of byte array...");
			
			currentTask = null;
		}
		else if (currentTask instanceof ReplyToClientTask) {
			if (debug) System.out.println("Worker thread " + workerThreadID + " writing data to channel...");
			SelectionKey key = ((ReplyToClientTask) currentTask).getKey();
			synchronized (key) {
				SocketChannel clientChannel = (SocketChannel) key.channel();
				int read = 0;
				buffer.rewind();
				String testResponse = "test response";
				buffer.put(testResponse.getBytes());
				buffer.rewind();
				try {
					while (buffer.hasRemaining() && read != -1) {
						read = clientChannel.write(buffer);
		                //buffer.flip();
					}
					if (debug) System.out.println("...Data written to channel.  read: " + read);
					buffer.rewind();
					while (buffer.hasRemaining()){
						if (debug) System.out.print((char) buffer.get());
					}
					if (debug) System.out.println();
					buffer.flip();
				} catch (IOException e) {
					// Abnormal termination
					
					/*
					 *  ADD SERVER DISCONNECT HERE
					 *  server.disconnect(key);
					 *  return;
					 */
				}
				// Data stored in 'data' of type: byte[]
				/*ByteBuffer buffer = ByteBuffer.wrap(data);
				 * channel.write(buffer);
				 * key.interestOps(SelectionKey.OP_READ);
				 */
				//if (debug) System.out.println(" Switching key interest to READ");
				//key.interestOps(SelectionKey.OP_READ);
			}
			currentTask = null;
			
		}
	}
	
	// Allows the thread pool manager to monitor for idle worker threads
	private void reportIdle() {
		synchronized(idleThreads){
			if (!idle) {
				if (debug) System.out.println("  Worker thread " + workerThreadID + " reporting itself idle to the thread pool manager.");
				idle = true;
				idleThreads.add(this);
			}
		}
		if (idle) {
			synchronized(sleepLock) {
				try {
					if (debug) System.out.println("  Worker thread " + workerThreadID + " is idle and is going to sleep.");
					sleepLock.wait();
					if (debug) System.out.println("  Worker thread " + workerThreadID + " has been woken up by the thread pool manager.");
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	public boolean isIdle() {
		if (idle) return true;
		else return false;
	}
	
	public int getConnectionCount() {
		return this.numConnections;
	}
	
	public int getId() {
		return this.workerThreadID;
	}
	
	public synchronized void registerNewSocketChannel(SocketChannel socketChannel) {
		try {
			if (debug) System.out.println("Worker thread " + workerThreadID + " accepting new socket channel from TPM...");
			socketChannel.configureBlocking(false);
			SelectionKey key = socketChannel.register(selector, SelectionKey.OP_READ);
			if (debug) System.out.println("Worker thread " + workerThreadID + " registered new socket channel.");
		} catch (IOException e) {
			System.out.println(e);
		}
	}
	
	public synchronized void assignTask(Task newTask) {
		if (debug) System.out.println("Worker thread " + workerThreadID + " accepting new task...");
		//synchronized (currentTask) {
			if (newTask != null) {
				idle = false;
				currentTask = newTask;
				if (debug) System.out.println("Worker thread " + workerThreadID + " reports new task accepted.");
			}
			else {
				if (debug) System.out.println("Worker thread " + workerThreadID + " reports there is already a current task!!!");
			}
		//}
	}
}

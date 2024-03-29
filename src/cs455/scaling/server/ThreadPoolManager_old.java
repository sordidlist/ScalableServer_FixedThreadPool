package cs455.scaling.server;

import java.util.LinkedList;

import cs455.scaling.server.WorkerThread_old;
import cs455.scaling.server.tasks.ReplyToClientTask;
import cs455.scaling.server.tasks.Task;
import cs455.util.StatTracker;

public class ThreadPoolManager_old implements Runnable {
	
	private final WorkerThread_old[] workerThreads;					// References to worker thread objects
	private final Thread[] threadPool;							// References to running worker threads
	private final LinkedList<Task> taskQueue;					// FIFO task queue
	private final LinkedList<WorkerThread_old> idleThreads;			// FIFO queue for idle threads
	private final StatTracker statTracker;						// Reference to server's stat tracker
	private final boolean debug;								// Debug mode
	private boolean shutDown;									// Shut down switch
	
	// ThreadPoolManager runs on its own thread. It builds and manages
	//   the thread pool.
	public ThreadPoolManager_old(int threadPoolSize, StatTracker statTracker, boolean debug) {
		this.debug = debug;
		this.shutDown = false;
		workerThreads = new WorkerThread_old[threadPoolSize];
		threadPool = new Thread[threadPoolSize];
		taskQueue = new LinkedList<Task>();
		this.statTracker = statTracker;
		idleThreads = new LinkedList<WorkerThread_old>();
		if (debug) System.out.println(" Thread pool constructed");
	}
	
	// Populates the thread pool with worker threads
	private synchronized void populateThreadPool() {
		if (debug) System.out.println(" Populating thread pool with " + threadPool.length + " threads.");
		for (int id = 0; id < threadPool.length; id++) {
			workerThreads[id] = new WorkerThread_old(idleThreads, id, statTracker, debug);
			threadPool[id] = new Thread(workerThreads[id]);
		}
	}
	
	// Executes all the worker threads in the thread pool
	private synchronized void startThreadPool() {
		if (debug) System.out.println(" Executing the threads in the thread pool...");
		for (int id = 0; id < threadPool.length; id++) {
			threadPool[id].start();
		}
	}
	
	// Retrieves the worker thread which has been idle the longest from the queue
	private WorkerThread_old retrieveIdleThread() {
		if (idleThreads.size() > 0) {
			synchronized (idleThreads) {
				WorkerThread_old idleThread = idleThreads.removeFirst();
				if (debug) System.out.println(" Idle thread " + idleThread.getId() + " retrieved.");
				return idleThread;
			}
		}
		return null;
	}
	
	// Enqueues a new task into the task queue, where it will wait to eventually be assigned to an idle worker thread
	public void enqueueTask(Task newTask) {
		synchronized(taskQueue) {
			taskQueue.add(newTask);
			if (debug) System.out.println(" Thread pool manager enqueuing new task... there are now " + taskQueue.size() + " queued tasks and " + idleThreads.size() + " idle threads...");
		}
	}
	
	// Returns number of idle threads in thread pool
	public int getIdleThreadCount() {
		return idleThreads.size();
	}
	
	// returns number of pending tasks in task queue
	public int getTaskQueueSize() {
		return taskQueue.size();
	}

	@Override
	public void run() {
		// Populate thread pool with worker threads
		populateThreadPool();
		
		// Execute the worker threads
		startThreadPool();
		
		// Begin monitoring for idle threads
		if (debug) System.out.println(" Thread pool manager now monitoring for idle worker threads and pending tasks...");
		while (!shutDown) {
			
			// Client runs very slowly in debug mode so that debug statements can be read on the console
			if (debug) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					System.out.println(e);
				}
			}
			
			if (debug) System.out.println("  Thread pool manager --  Task Queue: " + taskQueue.size() + "   Idle Threads: " + idleThreads.size());
			
			if (idleThreads.size() > 0) {
				// Check for worker threads for ready reply tasks
				synchronized(idleThreads) {
					for (WorkerThread_old idle: idleThreads) {
						ReplyToClientTask newReply = idle.extractPendingReplyTask();
						if (newReply != null) {
							if (debug) System.out.println("New reply task detected by thread pool manager. Adding to task queue...");
							synchronized(taskQueue){
								taskQueue.add(newReply);
							}
							if (debug) System.out.println("Task queue size is now: " + taskQueue.size());
						}
					}
				}
				// Attempt to pair pending tasks with waiting threads
				if (taskQueue.size() > 0) {
					if (debug) System.out.println("  Thread pool manager detects idle threads and pending tasks.");
					WorkerThread_old idleThread = retrieveIdleThread();
					synchronized(idleThread) {
						synchronized(taskQueue) {
							if (debug) System.out.println(" Matching retrieved idle thread with a pending task.");
							idleThread.assignTask(taskQueue.removeFirst());
							synchronized(idleThread.sleepLock) {
								idleThread.sleepLock.notify();
							}
							if (debug) System.out.println(" Thread and task matched. Task queue size is now: " + taskQueue.size());
						}
					}
				}
			}
		}
	}
}

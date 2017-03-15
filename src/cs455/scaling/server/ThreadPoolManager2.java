package cs455.scaling.server;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.LinkedList;

import cs455.scaling.server.tasks.Task;
import cs455.util.StatTracker;

public class ThreadPoolManager2 implements Runnable {
	
	private int threadPoolSize;
	private StatTracker statTracker;
	private boolean debug;
	private WorkerThread2[] threadPool;
	private Thread[] threadPoolThreads;
	private LinkedList<Task> taskQueue;
	private LinkedList<WorkerThread2> idleThreads;
	private boolean shutDown;
	
	public ThreadPoolManager2(int threadPoolSize, StatTracker statTracker, boolean debug) {
		this.threadPoolSize = threadPoolSize;
		this.statTracker = statTracker;
		this.debug = debug;
		this.threadPool = new WorkerThread2[threadPoolSize];
		this.threadPoolThreads = new Thread[threadPoolSize];
		this.taskQueue = new LinkedList<Task>();
		this.idleThreads = new LinkedList<WorkerThread2>();
		this.shutDown = false;
	}

	@Override
	public void run() {
		if (debug) System.out.println("Thread pool manager started.");
		startAllWorkerThreads();
		
		long start = System.nanoTime();
		while (!shutDown) {
			start = printTPMdebug(start, debug);
			
			if (taskQueue.size() > 0 && idleThreads.size() > 0) {
				if (debug) System.out.println("Matching pending task to idle thread...");
				WorkerThread2 taskedWorker = idleThreads.removeFirst();
				Task nextTask = taskQueue.removeFirst();
				taskedWorker.assignTask(nextTask);
				synchronized(taskedWorker){
					taskedWorker.notify();
				}
			}
		}
	}
	
	private long printTPMdebug(long start, boolean print){
		// Print TPM statistics every 5 seconds
		if (System.nanoTime() - start >= (5000000000L)) {
			Calendar calendar = Calendar.getInstance();
			Timestamp currentTimestamp = new java.sql.Timestamp(calendar.getTime().getTime());
			if (print) System.out.println("TPM has " + idleThreads.size() + " idle threads, " + taskQueue.size() + " pending tasks.");
			start = System.nanoTime();
			statTracker.resetRW();
		}
		return start;
	}
	
	private void startAllWorkerThreads(){
		if (debug) System.out.println(" Starting worker threads.");
		for (int i = 0; i < threadPoolSize; i++){
			threadPool[i] = new WorkerThread2(idleThreads, taskQueue, i, statTracker, debug);
			threadPoolThreads[i] = new Thread(threadPool[i]);
			threadPoolThreads[i].start();
		}
	}

	public void enqueueTask(Task task) {
		synchronized(taskQueue){
			if (debug) System.out.println("TPM enqueuing new task");
			taskQueue.add(task);
		}
	}

}
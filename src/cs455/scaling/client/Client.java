package cs455.scaling.client;

import java.io.IOException;
import java.util.LinkedList;

import cs455.scaling.Node;
import cs455.util.HashComputer;

public class Client implements Node {

	private String serverHost;						// Server IP address
	private int serverPort;							// Server port number
	private int messageRate;						// Number of message to send per second
	
	private HashComputer hashComputer;				// Takes a byte array as input and returns an integer hash value using the SHA1 algorithm
	private LinkedList<String> hashCodes;			// A queue of hash codes for messages sent by the client
	
	private ClientComms comm;						// Client communications thread
	private Thread commThread;
	
	private Client () {
		hashComputer = new HashComputer();
		hashCodes = new LinkedList<String>();
	}
	
	public static void main(String[] args) throws IOException {
		
		Client client = new Client();
		
		// Parse command arguments
		if (args.length == 3) {
			client.serverHost = args[0];
			client.serverPort = Integer.parseInt(args[1]);
			client.messageRate = Integer.parseInt(args[2]);
		}
		else {
			System.out.println(usage());
			System.exit(0);
		}
		
		System.out.println("New client initialized.  Server host: " + client.serverHost + " \tServer Port: " + client.serverPort + "\tMessageRate: " + client.messageRate + " per second");
	
		// Create a ClientComms object and begin communicating with the server
		client.comm = new ClientComms(client.serverHost, client.serverPort, client.messageRate, client.hashComputer, client.hashCodes, debug);
		client.commThread = new Thread(client.comm);
		client.commThread.start();
		
		/*
		while (true){
			synchronized(client.comm.statTracker){
				if (System.nanoTime() - client.comm.statTracker.getTime() > 3000000000L){
					if (debug) System.out.println("Hang detected: " + (System.nanoTime() - client.comm.statTracker.getTime()));
					client.comm = new ClientComms(client.serverHost, client.serverPort, client.messageRate, client.hashComputer, client.hashCodes, debug);
					client.commThread = new Thread(client.comm);
					client.commThread.start();
					client.comm.statTracker.setTime(System.nanoTime());
				}
			}
		}*/
	}
	
	// Print usage message if wrong number of arguments is given
	public static String usage() {
		return "Usage:  Client <server-host> <server-port> <message-rate>";
	}
}

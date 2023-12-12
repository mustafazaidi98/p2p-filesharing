import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import mma.MessageFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private static final ConcurrentHashMap<String, Set<String>> index = new ConcurrentHashMap<>();
    private int count = 0; 
    private int server_id;
    String configFileName;
    ArrayList<String> messages = new ArrayList<String>();
    public static void main(String[] args) {
	Server server = new Server();
	server.Start(args);
    }
    public void Start(String[] args){
        configFileName = args[0];
        InputStream is = new FileInputStream(configFileName);
        prop.load(is);
        server_id = Integer.parseInt(args[1]);
        int port = Integer.parseInt(prop.getProperty("server" + server_id + ".port"));
        try{
        ServerSocket serverSocket = new ServerSocket(port);
		System.out.println("Server is listening on port " + port);
		waitForConnections(serverSocket); 
	   }
	catch (IOException ex) {
		System.out.println("Server exception: " + ex.getMessage());
		ex.printStackTrace();
        }
    }
    private void waitForConnections(ServerSocket serverSocket){
	try{
		while (true) {
			Socket socket = serverSocket.accept();
			System.out.println("New client connected");
			Thread serverThread = new Thread(new ServerThread(socket));
		    serverThread.start();
            		}
	    } 
	catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
   }
    private class ServerThread implements Runnable {
        private final Socket socket;
        String message_ID;
        public ServerThread(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
		        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		        PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                String clientIP = socket.getInetAddress().getHostAddress();
                String command;
                while ((command = reader.readLine()) != null) {
                    if (command.startsWith("register")) {
                        String fileName = reader.readLine();
                        register(fileName, clientIP);
                        writer.println("Registered: " + fileName + " from " + clientIP);
                    } else if (command.startsWith("search")) {
                        String fileName = command.split(" ")[1];
                        Set<String> ips = search(fileName);
                        Properties prop = new Properties();
                        InputStream is = new FileInputStream(configFileName);
                        prop.load(is);
                        message_ID = server_id+"-"+count;
                        messages.add(msgId);
                        count++;
                        List<String> newList = ips.toArray(new String[0]);
                        String temp = prop.getProperty("server" + server_id + ".next");
                        if (temp != null) {
                            ArrayList<Thread> thread = new ArrayList<Thread>();
                            ArrayList<ConnectWithNeighbor> neighborList = new ArrayList<ConnectWithNeighbor>();
                            String[] neighbours = temp.split(",");
                            int ttl = neighbours.length;
                            String[] list = floodFileRequest(prop,server_id, ttl, message_ID, fileName,thread,neighborList, neighbours);
                            list.foreach((l)->newList.add(l));
                      }
                        writer.println("IPs containing " + fileName + ": " + new HashSet<String>(newList));
                    } else {
                        writer.println("Invalid command: " + command);
                    }
                }
            } catch (IOException ex) {
                System.out.println("ClientHandler exception: " + ex.getMessage());
            }
        }

        private void register(String fileName, String clientIP) {
        if(!index.containsKey(fileName)){
        	Set<String> clientIPSet = Collections.synchronizedSet(new HashSet<>());
        	index.put(fileName,clientIPSet);
        	}
       	index.get(fileName).add(clientIP);
        }

        private Set<String> search(String fileName) {
            return index.getOrDefault(fileName, Collections.emptySet());
        }
    }
    private String[] floodFileRequest(Properties prop, int peer_id, int ttl, String msgId, String filerequested,
        ArrayList<Thread> thread, ArrayList<ConnectWithNeighbor> neighborList, String[] neighbours) {
            for (int i = 0; i < neighbours.length; i++) {
        int connectingport = Integer.parseInt(prop.getProperty("peer" + neighbours[i] + ".port"));
        int neighbouringpeer = Integer.parseInt(neighbours[i]);
        String peerIP = prop.getProperty("peer" + neighbouringpeer + ".ip");
        ConnectWithNeighbor cp = new ConnectWithNeighbor(peerIP, connectingport, neighbouringpeer,peer_id, ttl, filerequested, msgId);
        Thread t  = new Thread(cp);
        t.start();
        thread.add(t);
        neighborList.add(cp);
    }
    for(int i=0;i<thread.size();i++){
        try {
            ((Thread) thread.get(i)).join();
            } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    String[] peerswithfiles= {};
    boolean found = false;
    System.out.println("peers containing the file are: ");
    int peerfromdownload = 0;
    for (int i = 0; i < neighborList.size(); i++) {
        ArrayUtils.addAll(peerswithfiles,((ConnectWithNeighbor) neighborList.get(i)).getarray());
    }
    return peerswithfiles;
}
public class ConnectWithNeighbor extends Thread{
    int peerPort,peerID,myID,ttl;
    String fileName,msgId,peerIP;
    Properties prop;
    Socket socket;
    int [] peersArray;
    MessageFormat MF = new MessageFormat();
    public ConnectWithNeighbor(String peerIP, int peerPort, int peerID, int myID,int ttl,String fileName,String msgID){
       
            this.msgId = msgID;
            this.myID = myID;
            this.peerID = peerID;
            this.peerIP = peerIP;
            this.peerPort = peerPort;
            this.ttl = ttl;
            this.fileName = fileName;
    }
    public void run(){
        try{
            socket = new Socket(peerIP,peerPort);
            System.out.println("\nConnected to peer "+peerPort);
            ObjectOutputStream os =new  ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream is = new ObjectInputStream(socket.getInputStream());
            MF.file_name = fileName;
            MF.message_ID = msgId;
            MF.fromPeerId = myID;
            MF.ttl = ttl;
            os.writeObject(MF);
            peersArray = (int[])is.readObject();
        }
        catch (IOException io) {
            io .printStackTrace();
        } catch (ClassNotFoundException cp) {
            cp.printStackTrace();
        }
    }
    public String[] getarray() {
        return peersArray;
    }
  }
  public class FileQueryHandlerRunnable implements Runnable{
    Socket socket;
    int id;
    String dir;
    int[] peersArraylist = new int[20];
    int countofpeers = 0;

    int[] a = new int[20];
    public FileQueryHandlerRunnable(Socket socket, int id){
    this.socket = socket;
    this.id = id;
    }
    @Override
    public void run(){
      try {
              ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
              ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
              ArrayList<ConnectWithNeighbor> NeighborList = new ArrayList<ConnectWithNeighbor>();
              ArrayList<Thread> thread = new ArrayList<Thread>();
              MessageFormat MF = new MessageFormat();
              MF =  (MessageFormat) ois.readObject();
              System.out.println("Received query from " + MF.fromPeerId);
              File found;
              boolean foundFile = false;
              if(messages.contains(MF.message_ID)){
              System.out.println("Recieved Same query before.");;
              }
              else{
                    messages.add(MF.message_ID);
                    String file = MF.file_name;
                    Set<String> ips = search(fileName);
                    Properties prop = new Properties();
                    InputStream is = new FileInputStream(configFileName);
                    ArrayList<Thread> thread = new ArrayList<Thread>();
                    ArrayList<ConnectWithNeighbor> neighborList = new ArrayList<ConnectWithNeighbor>();
                    prop.load(is);
                    String temp = prop.getProperty("peer" + id + ".next");
                    if (temp != null && MF.ttl > 0) {
                        String[] neighbours = temp.split(",");
                        floodFileRequest(prop, id,MF.ttl--,MF.message_ID, file, thread, NeighborList, neighbours);
                    }
                    for (int i = 0; i < thread.size(); i++) {
                        ((Thread) thread.get(i)).join();
                    }
                        for (int i = 0; i < NeighborList.size(); i++) {
                          a = ((ConnectWithNeighbor) NeighborList.get(i)).getarray();
                          for (int j = 0; j < a.length; j++) {
                              if (a[j] == 0)
                                  break;
                              peersArraylist[countofpeers++] = a[j];
                          }
                      }
                  }
                  oos.writeObject(peersArraylist);
              }
    } catch (Exception e) {
  
      e.printStackTrace();
  
  }
    }
  
  }
}

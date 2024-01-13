import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Node {
    private class PeerHandler implements Runnable {
        private final Node peer;
        private final Socket peer_s;
        private DataInputStream in;
        private DataOutputStream out;

        public PeerHandler(Node peer) {
            this.peer = peer;
            this.peer_s = peer.getPeer_s();
        }

        @Override
        public void run() {
            try {
                this.in = new DataInputStream(this.peer_s.getInputStream());
                this.out = new DataOutputStream(this.peer_s.getOutputStream());
            } catch(IOException i) {
                System.out.println(i);
                System.out.println("Failed to create streams.");
                return;
            }
            while (true) {
                switch (Node.this.getState()) {
                    case "STANDBY" -> {
                        try {
                            Message message = this.readMessage();
                            this.sendMessage(Node.this.handleInstruction(message));
                        } catch (IOException i) {
                            System.out.println(i);
                            System.out.println("Killing connection");
                            killConnection();
                            return;
                        }
                    }
                    case "ACCEPTING" -> {
                        try {
                            // TAKE? -> ACCEPT!
                            Message message = this.readMessage();
//                            if (!message.getValue().equals("TAKE?")) {
//                                System.out.println("Not a TAKE?");
//                                killConnection();
//                            }
                            byte[] message_data = message.getDataBytes();
                            int incoming_chunks = message_data[0];
                            System.out.println(incoming_chunks + " chunks incoming");

                            this.sendMessage(Node.this.handleInstruction(message));

                            // Read data
                            byte[] data = new byte[CHUNKSIZE*incoming_chunks];
                            ByteBuffer buf = ByteBuffer.wrap(data);

                            for (int i=0; i<incoming_chunks; i++) {
                                System.out.println("Reading chunk");
                                buf.put(this.in.readNBytes(CHUNKSIZE));
                            }

                            data = stripPadding(data);

                            Node.this.storeData(this.peer, data);

                            // are we DONE! ?
                            message = this.readMessage();
                            // Send THANKS!
                            this.sendMessage(Node.this.handleInstruction(message));
                        } catch (IOException i) {
                            System.out.println(i);
                            System.out.println("Killing connection");
                            killConnection();
                            return;
                        }
                    }
                    case "DISTRIBUTING" -> {
                        try {
                            // READY?
                            Message ready = new Message("INSTRUCTION", "REDY?");
                            this.sendMessage(ready);

                            Message response = this.readMessage();

                            // We expect AFFIRM!
                            if (!Node.this.handleResponse(response)) {
                                this.killConnection();
                                break;
                            }

                            // TAKE? <chunk_size>
                            synchronized (this) {
                                while(!Node.this.peerDataMap.containsKey(this.peer.getId())) {
                                    try {
                                        wait();
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        return;
                                    }
                                }
                            }

                            byte[] data = peerDataMap.remove(this.peer.getId());
                            System.out.println("Data has " + data.length + " bytes.");
                            byte[][] chunks = chunkData(data);
                            System.out.println("Data chunked to " + chunks.length + " chunks.");

                            Message take = new Message("INSTRUCTION", "TAKE?", new byte[] {(byte) chunks.length});
                            this.sendMessage(take);
                            response = this.readMessage();

                            // We expect ACCEPT!
                            if (!Node.this.handleResponse(response)) {
                                this.killConnection();
                                break;
                            }

                            // Send chunks
                            for (byte[] chunk : chunks) {
                                System.out.println("Sending chunk ...");
                                this.out.write(chunk, 0, CHUNKSIZE);
                            }

                            // DONE?
                            Message done = new Message("INSTRUCTION", "DONE?");
                            this.sendMessage(done);
                            response = this.readMessage();

                            // We expect THANKS
                            if (!Node.this.handleResponse(response)) {
                                break;
                            }
                            Node.this.setState("STANDBY");
                        } catch (IOException i) {
                            System.out.println(i);
                            System.out.println("Killing connection");
                            killConnection();
                            return;
                        }
                    }
                    case "LOOKING" -> {
                        try {
                            // QERY?
                            Message message = new Message("INSTRUCTION", "QERY?");
                            this.sendMessage(message);

                            Message response = this.readMessage();

                            // We expect AFFIRM!
                            if (!Node.this.handleResponse(response)) {
                                this.killConnection();
                                break;
                            }

                            // GIVE? <hash>
                            // Problem is that the owner node does not store the hash of each slice
                            // As a PoC we can still do it for one data "blob" per node, but eventually
                            // we would have to keep a table of NodeID:slice_hash in order to request it
                            SharedData shared = Node.this.getDistributedData();
                            assert shared != null;

                            message = new Message("INSTRUCTION", "GIVE?", shared.getHash());
                            this.sendMessage(message);

                            response = this.readMessage();

                            // If peer doesn't have the hash (something we could have also computed locally)
                            // REJECT! -> Peer will reset its state to STANDBY
                            if (!Node.this.handleResponse(response)) {
                                break;
                            }

                            // Wait for potential nested peer lookups

                            // Wait for TAKE? <chunks>
                            Message take = this.readMessage();

                            byte[] message_data = take.getDataBytes();
                            int incoming_chunks = message_data[0];
                            System.out.println(incoming_chunks + " chunks incoming");

                            this.sendMessage(Node.this.handleInstruction(take));

                            // Read data
                            byte[] data = new byte[CHUNKSIZE*incoming_chunks];
                            ByteBuffer buf = ByteBuffer.wrap(data);

                            for (int i=0; i<incoming_chunks; i++) {
                                System.out.println("Reading chunk");
                                buf.put(this.in.readNBytes(CHUNKSIZE));
                            }

                            // Get rid of padding
                            data = stripPadding(data);
                            shared.getSliceMap().put(this.peer.id, data);

                            // DONE?
                            Message done = new Message("INSTRUCTION", "DONE?");
                            this.sendMessage(done);
                            response = this.readMessage();

                            // We expect THANKS!
                            if (!Node.this.handleResponse(response)) {
                                break;
                            }
                            Node.this.setState("STANDBY");
                        } catch (IOException i) {
                            System.out.println(i);
                            System.out.println("Killing connection");
                            killConnection();
                            return;
                        }
                    }
                    case "COLLECTING" -> {
                        try {
                            // Expect GIVE? <hash>
                            Message message = this.readMessage();

                            if (!message.getValue().equals("GIVE?")) {
                                System.out.println("Expected GIVE?, got " + message.getValue());
                                System.out.println("Killing connection.");
                                killConnection();
                                return;
                            }

                            String hash = message.getData();
                            SharedData shared = Node.this.storage.get(hash);

                            if (shared == null) {
                                Message response = new Message("RESPONSE", "REJECT!");
                                this.sendMessage(response);
                                Node.this.setState("STANDBY");
                                break;
                            } else {
                                Message response = new Message("RESPONSE", "ACCEPT!");
                                this.sendMessage(response);
                            }

                            byte[] data;
                            if (shared.isDistributed()) {
                                // Perform (nested) lookup
                                data = null;
                            } else {
                                data = shared.getData();
                            }

                            assert data != null;
                            byte[][] chunks = chunkData(data);

                            Message take = new Message("INSTRUCTION", "TAKE?", new byte[] {(byte) chunks.length});
                            this.sendMessage(take);
                            Message response = this.readMessage();

                            // We expect ACCEPT!
                            if (!Node.this.handleResponse(response)) {
                                this.killConnection();
                                break;
                            }

                            for (byte[] chunk : chunks) {
                                System.out.println("Sending chunk ...");
                                this.out.write(chunk, 0, CHUNKSIZE);
                            }

                            // are we DONE! ?
                            message = this.readMessage();
                            // Send THANKS!
                            this.sendMessage(Node.this.handleInstruction(message));
                        } catch (IOException i) {
                            System.out.println(i);
                            System.out.println("Killing connection");
                            killConnection();
                            return;
                        }
                    }
                    default -> {
                        this.killConnection();
                        return;
                    }
                }
            }
        }

        private void sendMessage(Message message) throws IOException {
            System.out.println("Sending " + message.toString());
            this.out.write(padBytes(message.toBytes()), 0, CHUNKSIZE);
        }

        private Message readMessage() throws IOException {
            Message message = new Message(stripPadding(this.in.readNBytes(CHUNKSIZE)));
            System.out.println("Received " + message);
            return message;
        }

        public byte[] padBytes(byte[] data) {
            return Arrays.copyOf(data, CHUNKSIZE);
        }

        private byte[] stripPadding(byte[] data) {
            int end = data.length;
            for (int i=6; i<data.length; ++i) {
                if (data[i] == 0) {
                    end = i;
                    break;
                }
            }
            return Arrays.copyOf(data, end);
        }

        private byte[][] chunkData(byte[] data) {
            if (data.length <= CHUNKSIZE) {
                return new byte[][] {padBytes(data)};
            }
            // Easier than doing double division; equivalent of getting the ceiling of data.length / CHUNKSIZE
            // https://stackoverflow.com/a/21830188/14289718
            int chunkCount = (data.length + CHUNKSIZE - 1) / CHUNKSIZE;

            byte[][] chunks  = new byte[chunkCount][CHUNKSIZE];
            for (int i=0; i<chunkCount; ++i) {
                chunks[i] = Arrays.copyOfRange(data, i*CHUNKSIZE, (i+1)*CHUNKSIZE);
            }
            return chunks;
        }

        private void killConnection() {
            try {
                this.peer_s.close();
                this.in.close();
                this.out.close();
            } catch(IOException i) {
                System.out.println(i);
            }
        }
    }
    private final int CHUNKSIZE = 64;

    private final String[] STATES = {"STANDBY", "ACCEPTING", "DISTRIBUTING", "LOOKING", "COLLECTING"};

    private String state = STATES[0];
    private long id;
    private final String ip;
    private final int port;
    // TODO: This is very costly, but we don't have a lot of mutations currently. Still, consider finding alt.
    private final CopyOnWriteArrayList<Node> peers;
    private Socket peer_s = null;
    private HashMap<String, SharedData> storage;
    private final HashMap<Long, Thread> peerThreadMap = new HashMap<>();
    private final HashMap<Long, PeerHandler> peerHandlerMap = new HashMap<>();
    private final ConcurrentMap<Long, byte[]> peerDataMap = new ConcurrentHashMap<>();

    public Node(String ip, int port) {
        Random rn = new Random();
        this.id = rn.nextLong();
        this.ip = ip;
        this.port = port;
        this.peers = new CopyOnWriteArrayList<>();
        this.storage = new HashMap<>();
    }

    // ArrayList of peers to try connecting to (bootstrap)
    public Node(String ip, int port, CopyOnWriteArrayList<Node> peers) {
        Random rn = new Random();
        this.id = rn.nextLong();
        this.ip = ip;
        this.port = port;
        this.peers = peers;
        this.storage = new HashMap<>();
    }

    public Node(long id, String ip, int port, Socket peer_s) {
        this.id = id;
        this.ip = ip;
        this.port = port;
        this.peer_s = peer_s;
        this.peers = new CopyOnWriteArrayList<>();
    }

    public void run() {
        List<Node> blacklist = new ArrayList<>();

        // Start server socket
        Thread connectionManagerThread;
        try {
            PeerConnectionManager connectionManager = new PeerConnectionManager(port, this);
            connectionManagerThread = new Thread(connectionManager);
            connectionManagerThread.start();
        } catch (IOException i) {
            System.out.println(i);
            System.out.println("Failed to start PeerConnectionManager.");
            return;
        }

        // TODO: This is repetitive and needs better peer dropout
        for (Node peer : this.peers) {
            try {
                this.connect(peer);
            } catch (IOException i) {
                System.out.printf("Failed to connect to %s:%d%n", peer.ip, peer.port);
                blacklist.add(peer);
            }
        }

        // Remove dead nodes
        this.removePeers(blacklist);

        // TODO: Don't love this placement, but where else could I put it?
        this.buildDistribution();

        // Stay alive until all threads are done
        long[] kill_list = new long[this.peerThreadMap.size()];
        int i = 0;
        while (!this.peerThreadMap.isEmpty()) {
            for (long handler_id : this.peerThreadMap.keySet()) {
                if (this.peerThreadMap.get(handler_id).isAlive()) {
                    continue;
                }
                try {
                    this.peerThreadMap.get(handler_id).join();
                } catch (InterruptedException j) {
                    System.out.println(j);
                }
                kill_list[i] = handler_id;
                ++i;
            }
            for (long handler_id : kill_list) {
                if (handler_id == 0) {
                    break;
                }
                this.peerThreadMap.remove(handler_id);
                this.peerHandlerMap.remove(handler_id);
            }
        }

        // Stop listener
//        this.connectionManager.shutdown();
        try {
            connectionManagerThread.join();
        } catch (InterruptedException j) {
            System.out.println(j);
            System.out.println("Failed to stop connectionManager");
        }
    }

    private void removePeers(List<Node> deadNodes) {
        for (Node peer : deadNodes) {
            this.peers.remove(peer);
            System.out.printf("Removing %s:%d%n", peer.ip, peer.port);
        }
    }

    private SharedData getUndistributedData() {
        for (SharedData undistributed : this.storage.values()) {
            if (!undistributed.isDistributed()) {
                return undistributed;
            }
        }
        return null;
    }

    private SharedData getDistributedData() {
        for (SharedData distributed : this.storage.values()) {
            if (distributed.isDistributed()) {
                return distributed;
            }
        }
        return null;
    }

    private void buildDistribution() {
        for (SharedData undistributed : this.storage.values()) {
            if (!undistributed.isDistributed()) {
                // TODO: This (Node ids and Data slices) could and should be connected. Maybe in SharedData.
                List<Long> nodes = undistributed.buildDistributionRoute();
                byte[][] data = undistributed.split_data();
                for (int i=0; i<nodes.size(); ++i) {
                    this.peerDataMap.put(nodes.get(i), data[i]);
                    System.out.println("Distributing to node " + nodes.get(i));
                    PeerHandler peerHandler = this.peerHandlerMap.get(nodes.get(i));
                    synchronized (peerHandler) {
                        System.out.println("Notifying thread");
                        peerHandler.notify();
                    }

                    // TODO: technically not yet, it's just in the pipeline. Maybe update this when it's really sent
                    undistributed.setDistributed(true);
                }
            }
        }
    }

    public void storeData(Node peer, byte[] data) {
        System.out.printf("Storing data from %s%n", peer);
        System.out.println(Arrays.toString(data));
        SharedData shared = new SharedData(this, peer, data);
        System.out.println("Hash: " + shared.getHash());
        this.storage.put(shared.getHash(), shared);
    }

    public long readHandshake(Socket peer_s) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(peer_s.getInputStream()));
        return in.readLong();
    }

    public void startPeerHandlerThread(Node peer) {
        PeerHandler peerHandler = new PeerHandler(peer);
        Thread thread = new Thread(peerHandler);
        thread.start();

        this.peerThreadMap.put(peer.id, thread);
        this.peerHandlerMap.put(peer.id, peerHandler);
    }

    // Maybe change this to try and connect to all peers and return a list of failed peers to then delete
    // Whoever initiates the connection starts the handshake to obtain peer ID, maybe some peer nodes, etc.
    // Handshake: id (8 bytes) + status? (16 bytes) + size of peers to come (n bytes) + pad
    public void connect(Node peer) throws IOException {
        System.out.printf("Connecting to peer %s:%d%n", peer.ip, peer.port);
        Socket peer_s = new Socket(peer.ip, peer.port);

        System.out.println("Connection established. Initiating handshake.");

        // Read peer's side of handshake
        long id = this.readHandshake(peer_s);
        System.out.printf("Peer id received: %d%n", id);

        // Send our side of the handshake
        this.sendHandshake(peer_s);

        // Update info
        peer.id = id;
        peer.peer_s = peer_s;

        this.startPeerHandlerThread(peer);
    }

    public void sendHandshake(Socket peer_s) throws IOException {
        DataOutputStream out = new DataOutputStream(peer_s.getOutputStream());
        out.writeLong(this.id);
    }

    public void killall() {
        for(Node peer : this.peers) {
            try {
                peer.getPeer_s().close();
            } catch(IOException i) {
                System.out.println(i);
            }
        }
    }

    public Message handleInstruction(Message message) {
        String value = message.getValue();

        Message response = null;

        switch (value) {
            case "REDY?" -> {
                if (this.state.equals("STANDBY")) {
                    this.setState("ACCEPTING");
                    response = new Message("RESPONSE", "AFFIRM!");
                } else {
                    response = new Message("RESPONSE", "NOOOPE!");
                }
            }
            case "TAKE?" -> {
                if (this.state.equals("ACCEPTING") || this.state.equals("LOOKING")) {
                    response = new Message("RESPONSE", "ACCEPT!");
                } else {
                    response = new Message("RESPONSE", "REJECT!");
                }
            }
            case "GIVE?" -> {
            }
            case "QERY?" -> {
                if (this.state.equals("STANDBY")) {
                    this.setState("COLLECTING");
                    response = new Message("RESPONSE", "AFFIRM!");
                } else {
                    response = new Message("RESPONSE", "NOOOPE!");
                }
            }
            case "DONE?" -> {
                if (this.state.equals("ACCEPTING") || this.state.equals("COLLECTING")) {
                    response = new Message("RESPONSE", "THANKS!");
                    this.state = "STANDBY";
                } else {
                    response = new Message("RESPONSE", "FAIL");
                }
            }
            default -> {
                response = new Message("RESPONSE", "UNINIT");
            }
        }

        return response;
    }

    public boolean handleResponse(Message response) {
        if (!response.getType().equals("RESPONSE")) {
            return false;
        }

        boolean all_good = true;

        switch (response.getValue()) {
            case "AFFIRM!" -> {
                if (this.state.equals("DISTRIBUTING")) {

                } else {

                }
            }
            case "NOOOPE!" -> {
                all_good = false;
                // TODO: Maybe BLOCKED? something to act as a reset state?
                this.state = "STANDBY";
            }
            case "ACCEPT!" -> {
            }
            case "REJECT!" -> {
                all_good = false;
            }
            case "THANKS" -> {
                if (this.state.equals("DISTRIBUTING")) {
                    this.state = "STANDBY";
                }
            }
        }
        return all_good;
    }

    public static byte[] readFile(File file) throws IOException {
        return Files.readAllBytes(file.toPath());
    }

    public long getId() {
        return id;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public Socket getPeer_s() {
        return peer_s;
    }

    public List<Node> getPeers() {
        return peers;
    }

    public HashMap<Long, Node> getPeerMap() {
        HashMap<Long, Node> map = new HashMap<>();
        for (Node peer : this.peers) {
            map.put(peer.id, peer);
        }
        return map;
    }

    @Override
    public String toString() {
        return String.format("Node: %d (%s:%d)%n", this.id, this.ip, this.port);
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public HashMap<String, SharedData> getStorage() {
        return storage;
    }
}

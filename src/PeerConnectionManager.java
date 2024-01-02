import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class PeerConnectionManager implements Runnable {
    private ServerSocket server;
    private Node parent;
    private boolean running = true;

    public PeerConnectionManager(int port, Node parent) throws IOException {
        this.server = new ServerSocket(port);
        this.parent = parent;
    }

    @Override
    public void run() {
        System.out.println("Node listening on port " + this.server.getLocalPort());

        while (running) {
            try {
                System.out.println("Waiting for peer ...");
                Socket peer_s = server.accept();

                System.out.println("Peer accepted. Sending ID.");
                parent.sendHandshake(peer_s);

                // Read peer's side of handshake
                long id = this.parent.readHandshake(peer_s);
                System.out.printf("Peer id received: %d%n", id);

                Node peer = new Node(id, peer_s.getInetAddress().toString(), peer_s.getPort(), peer_s);

                parent.startPeerHandlerThread(peer);

                parent.getPeers().add(peer);
            } catch (IOException i) {
                System.out.println(i);
                System.out.println("Error in PeerConnectionManager");
                return;
            }
        }
    }

    public void shutdown() {
        this.running = false;
    }
}

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Main {
    public static void main(String[] args) {
        CopyOnWriteArrayList<Node> peers = new CopyOnWriteArrayList<>();
        peers.add(new Node("192.168.178.178", 1234));
//        peers.add(new Node("192.168.178.178", 4444));
//        peers.add(new Node("192.168.178.178", 5555));

        Node test = new Node("127.0.0.1", 4444, peers);

        test.storeData(test, "TESTINGTESTINGTESTINGTESTING".getBytes(StandardCharsets.UTF_8));
        test.setState("DISTRIBUTING");
        test.run();
//        test.distributeData(new byte[] {44, 45, 46, 47 ,48 ,49});
        test.killall();
    }
}
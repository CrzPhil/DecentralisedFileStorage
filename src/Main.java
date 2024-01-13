import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Main {
    public static void main(String[] args) {
        CopyOnWriteArrayList<Node> peers = new CopyOnWriteArrayList<>();
        peers.add(new Node("192.168.0.34", 1234));
        peers.add(new Node("192.168.0.34", 4444));
        peers.add(new Node("192.168.0.34", 5555));

        Node test = new Node("127.0.0.1", 4444, peers);

        test.storeData(test, "TESTINGTESTINGTESTINGTESTING".getBytes(StandardCharsets.UTF_8));
        test.setState("DISTRIBUTING");
//        test.setState("LOOKING");
//        test.getStorage().put("bece6b8ce415f61db510f729b9c37651ee97d771", new SharedData(test, test, "bece6b8ce415f61db510f729b9c37651ee97d771"));
        test.run();

        test.killall();
    }
}
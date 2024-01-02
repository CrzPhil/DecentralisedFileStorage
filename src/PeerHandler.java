//import java.io.BufferedInputStream;
//import java.io.DataInputStream;
//import java.io.DataOutputStream;
//import java.io.IOException;
//import java.net.Socket;
//import java.nio.ByteBuffer;
//import java.nio.charset.StandardCharsets;
//import java.util.Arrays;
//
//public class PeerHandler implements Runnable {
//    private final Node parent;
//    private final Node peer;
//    private final Socket peer_s;
//    private final int CHUNKSIZE = 64;
//
//    public PeerHandler(Node parent, Node peer) {
//        this.parent = parent;
//        this.peer = peer;
//        this.peer_s = peer.getPeer_s();
//    }
//
//    @Override
//    public void run() {
//        while (true) {
//            switch (parent.getState()) {
//                case "STANDBY" -> {
//                    try {
//                        DataInputStream in = new DataInputStream(new BufferedInputStream(this.peer_s.getInputStream()));
//                        byte[] instruction = in.readNBytes(CHUNKSIZE);
//                        in.close();
//                        this.sendInstruction(parent.handleInstruction(instruction, CHUNKSIZE));
//                    } catch (IOException i) {
//                        System.out.println(i);
//                    }
//                }
//                case "ACCEPTING" -> {
//                    try {
//                        DataInputStream in = new DataInputStream(new BufferedInputStream(this.peer_s.getInputStream()));
//                        // TAKE? -> ACCEPT
//                        byte[] instruction = in.readNBytes(CHUNKSIZE);
//                        this.sendInstruction(parent.handleInstruction(instruction, CHUNKSIZE));
//
//                        // How many chunks incoming?
//                        int incoming_chunks = in.readInt();
//
//                        // Read data
//                        byte[] data = new byte[CHUNKSIZE*incoming_chunks];
//                        ByteBuffer buf = ByteBuffer.wrap(data);
//
//                        for (int i=0; i<incoming_chunks; ++i) {
//                            buf.put(in.readNBytes(CHUNKSIZE));
//                        }
//
//                        this.parent.storeData(this.peer, data);
//
//                        // are we DONE! ?
//                        instruction = in.readNBytes(CHUNKSIZE);
//                        this.sendInstruction(parent.handleInstruction(instruction, CHUNKSIZE));
//
//                        in.close();
//                    } catch (IOException i) {
//                        System.out.println(i);
//                        System.out.println("Killing connection");
//                        killConnection();
//                        return;
//                    }
//                }
//                case "DISTRIBUTING" -> {
//                    try {
//                        String[] instructions = {"READY?", "TAKE?", "DONE!"};
//
//                        // READY?
//                        this.sendInstruction(instructions[0].getBytes(StandardCharsets.UTF_8));
//                        DataInputStream in = new DataInputStream(new BufferedInputStream(this.peer_s.getInputStream()));
//
//                        byte[] response = in.readNBytes(CHUNKSIZE);
//
//                        // We expect AFFIRM
//                        if (!this.parent.handleResponse(response)) {
//                            in.close();
//                            break;
//                        }
//
//                        // TAKE?
//                        this.sendInstruction(instructions[1].getBytes(StandardCharsets.UTF_8));
//                        response = in.readNBytes(CHUNKSIZE);
//
//                        // We expect ACCEPT
//                        if (!this.parent.handleResponse(response)) {
//                            in.close();
//                            break;
//                        }
//                        // chunks to send
//
//
//                        // DONE!
//                        this.sendInstruction(instructions[2].getBytes(StandardCharsets.UTF_8));
//                        response = in.readNBytes(CHUNKSIZE);
//
//                        // We expect DONE!
//                        if (!this.parent.handleResponse(response)) {
//                            in.close();
//                            break;
//                        }
//
//                        in.close();
//                    } catch (IOException i) {
//
//                    }
//                }
//                case "LOOKING" -> {
//                    try {
//                        DataInputStream in = new DataInputStream(new BufferedInputStream(this.peer_s.getInputStream()));
//                        String instruction = Arrays.toString(in.readNBytes(CHUNKSIZE));
//                        in.close();
//                    } catch (IOException i) {
//
//                    }
//                }
//                default -> {
//                    this.killConnection();
//                    return;
//                }
//            }
//        }
//    }
//
//    private void sendInstruction(byte[] instruction) throws IOException {
//        DataOutputStream out = new DataOutputStream(this.peer_s.getOutputStream());
//        out.write(instruction, 0, CHUNKSIZE);
//        out.close();
//    }
//
//    private void killConnection() {
//        try {
//            this.peer_s.close();
//        } catch(IOException i) {
//            System.out.println(i);
//        }
//    }
//}

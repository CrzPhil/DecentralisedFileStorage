import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Formatter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;

public class SharedData {
    private String hash;
    private final ArrayList<Long> ordered_ids;

    private final Node parent;
    private final Node owner;
    private final int data_size;
    private final int slice_count;
    private final int slice_size;
    // TODO: does it make sense to actually keep data in memory? if not, where else to store it?
    private final byte[] data;
    private boolean distributed;

    public SharedData(Node parent, Node owner, byte[] data) {
        this.hash = SHAsum(data);
        this.parent = parent;
        this.owner = owner;
        this.data = data;

        // TODO: algorithmically determine ideal split for data (e.g. how many peers, ordering, etc.)
        this.ordered_ids = new ArrayList<>();

        this.data_size = data.length;
        this.slice_count = parent.getPeers().size();
        // https://stackoverflow.com/a/21830188/14289718
        this.slice_size = (data.length + this.slice_count - 1) / this.slice_count;

        this.distributed = false;
    }

    public byte[][] split_data() {
        byte[][] chunks  = new byte[slice_count][slice_size];
        for (int i=0; i<slice_count; ++i) {
            chunks[i] = Arrays.copyOfRange(data, i*slice_size, (i+1)*slice_size);
        }
        return chunks;
    }

    private byte[] assembleData(byte[][] data) {
        byte[] assembled = new byte[this.slice_size*this.slice_count];

        for (int i=0; i<this.slice_count; ++i) {

        }

        return assembled;
    }

    // TODO: Determine ideal/optimal peer number for the data size, etc. maybe routing to closer nodes?
    public ArrayList<Long> buildDistributionRoute() {
        for (Node peer : this.owner.getPeers()) {
            this.ordered_ids.add(peer.getId());
        }
        return ordered_ids;
    }

    // Reference: https://stackoverflow.com/a/1515495
    private static String SHAsum(byte[] raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return byteArray2Hex(md.digest(raw));
        } catch (NoSuchAlgorithmException n) {
            System.out.println(n);
        }
        return "";
    }

    private static String byteArray2Hex(final byte[] hash) {
        Formatter formatter = new Formatter();
        for (byte b : hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    public String getHash() {
        return hash;
    }

    public Node getOwner() {
        return owner;
    }

    public boolean isDistributed() {
        return distributed;
    }

    public void setDistributed(boolean distributed) {
        this.distributed = distributed;
    }

    public int getData_size() {
        return data_size;
    }

    public int getSlice_count() {
        return slice_count;
    }

    public int getSlice_size() {
        return slice_size;
    }
}

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Formatter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class SharedData {
    private String hash;
    private final ArrayList<Long> ordered_ids;
    private final Node parent;
    private final Node owner;
    private final int data_size;
    private final int slice_count;
    private final int slice_size;
    // TODO: does it make sense to actually keep data in memory? if not, where else to store it?
    private byte[] data;
    private boolean distributed;
    private final ConcurrentHashMap<Long, byte[]> sliceMap = new ConcurrentHashMap<>();

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

    public SharedData(Node parent, Node owner, String hash) {
        this.hash = hash;
        this.parent = parent;
        this.owner = owner;
        this.data = null;

        this.ordered_ids = new ArrayList<>();

        this.data_size = 0;
        this.slice_count = 0;
        this.slice_size = 0;

        this.distributed = true;
    }

    public byte[][] split_data() {
        byte[][] chunks  = new byte[slice_count][slice_size];
        for (int i=0; i<slice_count; ++i) {
            chunks[i] = Arrays.copyOfRange(data, i*slice_size, (i+1)*slice_size);
        }
        return chunks;
    }

    private byte[] assembleData() {
        // Data is mapped to the Node ID that sent it.
        // This will likely change later on but for now it's intuitive.

        // Technically if this has been distributed we will know both the slices and their size
        // But if we assume the state is lost after restart and only the hash and ID order remains,
        // then we cannot use these and need to infer the assembled size via the length of the data.
//        byte[] assembled = new byte[this.slice_size*this.slice_count];

        // Get size of data; equivalent of CHUNKSIZE
        // Assuming all byte arrays are of the same length and not null
        int data_size = sliceMap.isEmpty() ? 0 : sliceMap.values().iterator().next().length;

        byte[] assembled = new byte[sliceMap.size() * data_size];
        ByteBuffer buf = ByteBuffer.wrap(assembled);

        for (long node_id : this.ordered_ids) {
            byte[] chunk = sliceMap.get(node_id);
            if (chunk != null) {
                buf.put(chunk);
            } else {
                // TODO: Handle missing parts
                System.out.println("Part missing. ID: " + node_id);
            }
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

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public ConcurrentHashMap<Long, byte[]> getSliceMap() {
        return sliceMap;
    }
}

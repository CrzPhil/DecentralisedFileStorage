import java.util.Arrays;

public class Message {
    // Instruction || Response
    private final String type;
    // READY? TAKE? GIVE? ... AFFIRM REJECT NOPE ...
    private final byte[] value;
    private final byte[] data;

    public Message(String type, byte[] value, byte[] data) {
        this.type = type;
        this.value = value;
        this.data = data;
    }

    public Message(byte[] raw) {
        // Check if the fifth byte is ?
        if (raw[4] == 63) {
            this.type = "INSTRUCTION";
            this.value = Arrays.copyOfRange(raw, 0, 5);
            this.data = Arrays.copyOfRange(raw, 5, raw.length);
        } else {
            this.type = "RESPONSE";
            this.value = Arrays.copyOfRange(raw, 0, 7);
            this.data = Arrays.copyOfRange(raw, 7, raw.length);
        }
    }

    public Message(String type, String value, String data) {
        this.type = type;
        this.value = value.getBytes();
        this.data = data.getBytes();
    }

    public Message(String type, String value) {
        this.type = type;
        this.value = value.getBytes();
        this.data = null;
    }

    public Message(String type, String value, byte[] data) {
        this.type = type;
        this.value = value.getBytes();
        this.data = data;
    }

    public String getType() {
        return type;
    }

    public byte[] getDataBytes() {
        return data;
    }

    public String getData() {
        if (this.data == null) {
            return "";
        }
        return new String(data);
    }

    public String getValue() {
        return new String(value);
    }

    public byte[] getValueBytes() {
        return value;
    }

    public byte[] toBytes() {
        if (this.data != null) {
            byte[] raw = Arrays.copyOf(this.value, this.value.length + this.data.length);
            System.arraycopy(this.data, 0, raw, this.value.length, this.data.length);
            return raw;
        } else {
            return this.value;
        }
    }

    @Override
    public String toString() {
        if (this.data != null && this.data.length != 0) {
            return "MSG[" + getType() + "]" + " " + getValue() + ": " + Arrays.toString(getDataBytes());
        } else {
            return "MSG[" + getType() + "]" + " " + getValue();
        }
    }
}

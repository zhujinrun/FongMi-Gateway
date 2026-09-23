package android.content.pm;

public class Signature {
    private final byte[] bytes;

    public Signature() {
        this.bytes = new byte[0];
    }

    public Signature(byte[] signature) {
        this.bytes = signature == null ? new byte[0] : signature.clone();
    }

    public byte[] toByteArray() {
        return bytes.clone();
    }

    public String toCharsString() {
        return new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
    }
}

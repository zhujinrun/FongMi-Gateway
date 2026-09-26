package android.util;

import java.nio.charset.StandardCharsets;

public class Base64 {

    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int CRLF = 4;
    public static final int URL_SAFE = 8;
    public static final int NO_CLOSE = 16;

    public static byte[] decode(String input, int flags) {
        if (input == null) return new byte[0];
        String s = input.replace('\n', ' ').replace('\r', ' ');
        java.util.Base64.Decoder decoder;
        if ((flags & URL_SAFE) != 0) decoder = java.util.Base64.getUrlDecoder();
        else decoder = java.util.Base64.getMimeDecoder();
        try {
            return decoder.decode(s);
        } catch (IllegalArgumentException e) {
            try {
                return java.util.Base64.getMimeDecoder().decode(s.replaceAll("[^A-Za-z0-9+/=]", ""));
            } catch (IllegalArgumentException e2) {
                return new byte[0];
            }
        }
    }

    public static byte[] decode(byte[] input, int flags) {
        if (input == null) return new byte[0];
        return decode(new String(input, StandardCharsets.ISO_8859_1), flags);
    }

    public static byte[] decode(byte[] input) {
        return decode(input, DEFAULT);
    }

    public static String encodeToString(byte[] input, int flags) {
        if (input == null) return "";
        java.util.Base64.Encoder encoder;
        if ((flags & URL_SAFE) != 0) encoder = java.util.Base64.getUrlEncoder();
        else if ((flags & NO_WRAP) != 0) encoder = java.util.Base64.getEncoder();
        else encoder = java.util.Base64.getMimeEncoder();
        String out = encoder.encodeToString(input);
        if ((flags & NO_PADDING) != 0) out = out.replace("=", "");
        return out;
    }

    public static String encodeToString(byte[] input) {
        return encodeToString(input, DEFAULT);
    }

    public static byte[] encode(byte[] p0, int p1) { return null; }
}

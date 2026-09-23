package com.github.catvod.spider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class SpiderCrypto {

    private SpiderCrypto() {
    }

    public static String decrypt(String text) {
        if (text == null || text.isEmpty()) return "";
        try {
            byte[] raw = Base64.getDecoder().decode(text.replaceAll("\\s", ""));
            String plain = tryAes(raw, "AES/CBC/PKCS5Padding");
            if (plain != null) return plain;
            plain = tryAes(raw, "AES/ECB/PKCS5Padding");
            if (plain != null) return plain;
            String asText = new String(raw, StandardCharsets.UTF_8);
            if (isMostlyPrintable(asText)) return asText;
        } catch (Throwable ignored) {
        }
        return text;
    }

    public static String encrypt(String text) {
        if (text == null || text.isEmpty()) return "";
        try {
            byte[] key = md516("gateway-spider-key");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(new byte[16]));
            return Base64.getEncoder().encodeToString(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Throwable e) {
            return text;
        }
    }

    public static int[] calcResult(int[] input) {
        if (input == null) return new int[0];
        int[] out = input.clone();
        for (int i = 0; i < out.length; i++) {
            out[i] = out[i] ^ 0x5A;
        }
        return out;
    }

    public static String native_ting_md5(String text) {
        if (text == null) text = "";
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Throwable e) {
            return "";
        }
    }

    private static String tryAes(byte[] raw, String mode) {
        try {
            byte[] key = md516("gateway-spider-key");
            Cipher cipher = Cipher.getInstance(mode);
            if (mode.contains("ECB")) {
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            } else {
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(new byte[16]));
            }
            String plain = new String(cipher.doFinal(raw), StandardCharsets.UTF_8);
            if (isMostlyPrintable(plain)) return plain;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static byte[] md516(String s) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[16];
            System.arraycopy(d, 0, out, 0, 16);
            return out;
        } catch (Exception e) {
            return new byte[16];
        }
    }

    private static boolean isMostlyPrintable(String s) {
        if (s == null || s.isEmpty()) return false;
        int ok = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 32 && c < 127 || c == '\n' || c == '\r' || c == '\t') ok++;
        }
        return ok >= s.length() * 3 / 4;
    }
}

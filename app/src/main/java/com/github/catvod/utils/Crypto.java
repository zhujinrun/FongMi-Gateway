package com.github.catvod.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class Crypto {

    private static final String MD5 = "MD5";
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int AES_BLOCK_SIZE = 16;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public static String md5(String value) {
        if (value == null || value.isEmpty()) return "";
        return toHex(newDigest(MD5).digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    public static String md5(File file) {
        try {
            return toHex(digest(file, newDigest(MD5)));
        } catch (IOException e) {
            return "";
        }
    }

    public static boolean equals(File file, String expected) {
        return expected != null && !expected.isEmpty() && expected.equalsIgnoreCase(md5(file));
    }

    public static MessageDigest newDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] decryptAesCbc(byte[] data, byte[] key, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(data);
    }

    public static byte[] encryptAesCbc(byte[] data, byte[] key, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(data);
    }

    public static String base64Encode(byte[] data) {
        return java.util.Base64.getEncoder().encodeToString(data);
    }

    public static byte[] base64Decode(String data) {
        return java.util.Base64.getDecoder().decode(data);
    }

    private static byte[] digest(File file, MessageDigest digest) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            return digest.digest();
        }
    }

    private static String toHex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            result[index * 2] = HEX[value >>> 4];
            result[index * 2 + 1] = HEX[value & 0x0f];
        }
        return new String(result);
    }
}

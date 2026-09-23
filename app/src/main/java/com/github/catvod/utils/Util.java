package com.github.catvod.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.Random;

public class Util {

    public static final String CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    public static final String UA = CHROME;
    public static final int URL_SAFE = 8;
    public static final int NO_WRAP = 2;

    public static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public static String base64(String input) {
        return base64(input, 0);
    }

    public static String base64(String input, int flags) {
        if (input == null) return "";
        byte[] data = input.getBytes(StandardCharsets.UTF_8);
        if ((flags & URL_SAFE) != 0) {
            String s = Base64.getUrlEncoder().encodeToString(data);
            return (flags & NO_WRAP) != 0 || true ? s.replace("=", "") : s;
        }
        if ((flags & NO_WRAP) != 0) return Base64.getEncoder().encodeToString(data);
        return Base64.getEncoder().encodeToString(data);
    }

    public static String base64Decode(String input) {
        if (input == null) return "";
        try {
            return new String(Base64.getDecoder().decode(input), StandardCharsets.UTF_8);
        } catch (Exception e) {
            try {
                return new String(Base64.getUrlDecoder().decode(input), StandardCharsets.UTF_8);
            } catch (Exception e2) {
                return "";
            }
        }
    }

    public static byte[] hex2byte(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        String h = hex.replaceAll("\\s+", "");
        int len = h.length();
        if (len % 2 != 0) h = "0" + h;
        len = h.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(h.charAt(i), 16) << 4) + Character.digit(h.charAt(i + 1), 16));
        }
        return data;
    }

    public static String byte2hex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }

    public static String md5(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static String getRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }

    public static boolean isVideoFormat(String url) {
        if (url == null) return false;
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains(".m3u8") || u.contains(".mp4") || u.contains(".flv") || u.contains(".mkv")
                || u.contains(".avi") || u.contains(".ts") || u.contains(".mpd") || u.contains(".wav")
                || u.contains(".mp3") || u.contains(".aac") || u.contains(".ogg") || u.contains(".webm");
    }
}

package com.fongmi.gateway.decode;

import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.UriUtil;
import com.github.catvod.utils.Util;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Decoder {

    private static final Pattern JS_URI = Pattern.compile("\"(\\.|\\.\\.)/(.?|.+?)\\.js\\?(.?|.+?)\"");
    private static final Pattern STAR = Pattern.compile("[A-Za-z0-9]{8}\\*\\*");
    private static final Pattern B64_RUN = Pattern.compile("[A-Za-z0-9+/=]{32,}");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder().followRedirects(true).build();

    public static String getJson(String url, String tag) throws Exception {
        if (url != null && url.startsWith("file://")) {
            java.io.File f = new java.io.File(url.substring("file://".length()));
            String data = com.github.catvod.utils.Path.read(f);
            if (data.isEmpty()) throw new Exception("empty file: " + f.getAbsolutePath());
            return verify(url, data);
        }
        if (url != null && !url.startsWith("http")) {
            java.io.File f = new java.io.File(url);
            if (f.isFile()) {
                String data = com.github.catvod.utils.Path.read(f);
                if (data.isEmpty()) throw new Exception("empty file: " + f.getAbsolutePath());
                return verify("file://" + f.getAbsolutePath(), data);
            }
        }
        Request request = new Request.Builder().url(url).tag(tag == null ? "config" : tag).build();
        try (Response res = CLIENT.newCall(request).execute()) {
            if (res.body() == null) throw new Exception("empty body");
            HttpUrl httpUrl = res.request().url();
            int size = HttpUrl.parse(url) != null ? HttpUrl.parse(url).querySize() : 0;
            String effective = url;
            if (httpUrl.querySize() == size) effective = httpUrl.toString();
            return verify(effective, res.body().string());
        }
    }

    public static String verify(String url, String data) throws Exception {
        if (data == null || data.isEmpty()) throw new Exception("empty config");
        if (Json.isObj(data)) return fix(url, data);
        String unwrapped = unwrap(data);
        if (unwrapped != null) data = unwrapped;
        if (data.contains("**")) data = base64(data);
        if (data.startsWith("2423")) data = cbc(data.replaceAll("\\s+", ""));
        if (!Json.isObj(data) && data.contains("**")) {
            data = base64(data);
        }
        if (!Json.isObj(data)) {
            unwrapped = unwrap(data);
            if (unwrapped != null) data = unwrapped;
        }
        return fix(url, data);
    }

    /** 饭太硬 style config: base64 JSON appended after a binary (JPEG) header. */
    private static String unwrap(String data) {
        Matcher matcher = B64_RUN.matcher(data);
        while (matcher.find()) {
            String text = matcher.group();
            try {
                String dec = new String(java.util.Base64.getDecoder().decode(text), StandardCharsets.UTF_8).trim();
                if (Json.isObj(dec)) return dec;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String fix(String url, String data) {
        Matcher matcher = JS_URI.matcher(data);
        while (matcher.find()) data = replace(url, data, matcher.group());
        if (data.contains("../")) data = data.replace("../", UriUtil.resolve(url, "../"));
        if (data.contains("./")) data = data.replace("./", UriUtil.resolve(url, "./"));
        if (data.contains("__JS1__")) data = data.replace("__JS1__", "./");
        if (data.contains("__JS2__")) data = data.replace("__JS2__", "../");
        return data;
    }

    private static String replace(String url, String data, String ext) {
        String t = ext.replace("\"./", "\"" + UriUtil.resolve(url, "./"));
        t = t.replace("\"../", "\"" + UriUtil.resolve(url, "../"));
        t = t.replace("./", "__JS1__").replace("../", "__JS2__");
        return data.replace(ext, t);
    }

    private static String cbc(String data) throws Exception {
        String decode = new String(Util.hex2byte(data), StandardCharsets.UTF_8).toLowerCase();
        String key = padEnd(decode.substring(decode.indexOf("$#") + 2, decode.indexOf("#$")));
        String iv = padEnd(decode.substring(decode.length() - 13));
        data = data.substring(data.indexOf("2324") + 4, data.length() - 26);
        byte[] decrypted = Crypto.decryptAesCbc(Util.hex2byte(data), key.getBytes(StandardCharsets.UTF_8), iv.getBytes(StandardCharsets.UTF_8));
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private static String base64(String data) {
        String extract = extract(data);
        if (extract.isEmpty()) return data;
        try {
            return new String(java.util.Base64.getDecoder().decode(extract), StandardCharsets.UTF_8);
        } catch (Exception e) {
            try {
                return new String(java.util.Base64.getMimeDecoder().decode(extract), StandardCharsets.UTF_8);
            } catch (Exception e2) {
                return data;
            }
        }
    }

    private static String extract(String data) {
        Matcher matcher = STAR.matcher(data);
        if (matcher.find()) {
            int start = data.indexOf(matcher.group()) + 10;
            if (start >= 0 && start <= data.length()) return data.substring(start).trim();
        }
        int idx = data.indexOf("**");
        if (idx >= 0 && idx + 2 <= data.length()) return data.substring(idx + 2).trim();
        return "";
    }

    private static String padEnd(String key) {
        if (key.length() >= 16) return key.substring(0, 16);
        return key + "0000000000000000".substring(key.length());
    }

    public static JsonObject parseObject(String json) {
        return Json.parse(json).getAsJsonObject();
    }
}

package com.github.catvod.net;

import android.util.Log;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttp {

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(30);
    private static volatile OkHttpClient client;
    private static volatile OkHttpClient player;
    private static volatile OkDns dns;

    public static OkDns dns() {
        if (dns != null) return dns;
        synchronized (OkHttp.class) {
            if (dns == null) dns = new OkDns();
            return dns;
        }
    }

    public static synchronized OkHttpClient client() {
        if (client != null) return client;
        return client = builder().build();
    }

    public static synchronized OkHttpClient player() {
        if (player != null) return player;
        return player = builder().build();
    }

    public static OkHttpClient client(long timeout) {
        return client().newBuilder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .build();
    }

    public static OkHttpClient noRedirect() {
        return noRedirect(TIMEOUT);
    }

    public static OkHttpClient noRedirect(long timeout) {
        return client().newBuilder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    public static OkHttpClient client(boolean redirect, long timeout) {
        return redirect ? client(timeout) : noRedirect(timeout);
    }

    public static String string(String url) {
        if (url == null || !url.startsWith("http")) return "";
        try (Response res = newCall(url).execute()) {
            return res.body() != null ? res.body().string() : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static String string(String url, Map<String, String> headers) {
        if (url == null || !url.startsWith("http")) return "";
        try (Response res = newCall(url, headers).execute()) {
            return res.body() != null ? res.body().string() : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static Call newCall(String url) {
        return client().newCall(new Request.Builder().url(url).build());
    }

    public static Call newCall(String url, String tag) {
        return client().newCall(new Request.Builder().url(url).tag(tag).build());
    }

    public static Call newCall(OkHttpClient c, String url) {
        return c.newCall(new Request.Builder().url(url).build());
    }

    public static Call newCall(OkHttpClient c, String url, String tag) {
        return c.newCall(new Request.Builder().url(url).tag(tag).build());
    }

    public static Call newCall(String url, Map<String, String> headers) {
        return client().newCall(new Request.Builder().url(url).headers(Headers.of(headers)).build());
    }

    public static Call newCall(String url, Map<String, String> headers, Map<String, String> params) {
        return client().newCall(new Request.Builder().url(buildUrl(url, params)).headers(Headers.of(headers)).build());
    }

    public static Call newCall(String url, Map<String, String> headers, RequestBody body) {
        return client().newCall(new Request.Builder().url(url).headers(Headers.of(headers)).post(body).build());
    }

    public static Call newCall(String url, RequestBody body, String tag) {
        return client().newCall(new Request.Builder().url(url).post(body).tag(tag).build());
    }

    public static Call newCall(OkHttpClient c, String url, RequestBody body) {
        return c.newCall(new Request.Builder().url(url).post(body).build());
    }

    public static void cancel(String tag) {
        cancel(client(), tag);
    }

    public static void cancel(OkHttpClient c, String tag) {
        for (Call call : c.dispatcher().queuedCalls()) if (tag.equals(call.request().tag())) call.cancel();
        for (Call call : c.dispatcher().runningCalls()) if (tag.equals(call.request().tag())) call.cancel();
    }

    public static void cancelAll() {
        cancelAll(client());
    }

    public static void cancelAll(OkHttpClient c) {
        c.dispatcher().cancelAll();
    }

    public static FormBody toBody(Map<String, String> params) {
        FormBody.Builder body = new FormBody.Builder();
        for (Map.Entry<String, String> entry : params.entrySet()) body.add(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        return body.build();
    }

    public static Response newResponse(String url, Map<String, String> headers) throws Exception {
        return newCall(url, headers == null ? new HashMap<>() : headers).execute();
    }

    private static HttpUrl buildUrl(String url, Map<String, String> params) {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) throw new IllegalArgumentException("bad url: " + url);
        HttpUrl.Builder builder = parsed.newBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.addQueryParameter(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return builder.build();
    }

    private static OkHttpClient.Builder builder() {
        return new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .dns(dns())
                .hostnameVerifier((hostname, session) -> true)
                .sslSocketFactory(sslContext().getSocketFactory(), trustAll());
    }

    private static SSLContext sslContext() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{trustAll()}, new SecureRandom());
            return context;
        } catch (Throwable e) {
            try {
                return SSLContext.getDefault();
            } catch (Exception e2) {
                throw new IllegalStateException(e2);
            }
        }
    }

    private static X509TrustManager trustAll() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
}

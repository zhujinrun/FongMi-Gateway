package com.fongmi.gateway.loader;

import android.content.Context;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Path;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class JarLoader {

    private final ConcurrentHashMap<String, URLClassLoader> loaders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Method> methods = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> errors = new ConcurrentHashMap<>();
    private volatile String recent = "";

    public void clear() {
        spiders.values().forEach(Spider::destroy);
        for (URLClassLoader loader : loaders.values()) {
            try {
                loader.close();
            } catch (Exception ignored) {
            }
        }
        loaders.clear();
        methods.clear();
        spiders.clear();
        locks.clear();
        errors.clear();
        recent = "";
    }

    public void setRecent(String recent) {
        this.recent = recent == null ? "" : recent;
    }

    public String getRecent() {
        return recent;
    }

    public String getError(String key) {
        return errors.getOrDefault(key, "");
    }

    public void parseJar(String key, String jar) {
        if (jar == null || jar.isEmpty()) return;
        if (loaders.containsKey(key)) return;
        if (jar.startsWith("assets")) return;
        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            if (loaders.containsKey(key)) return;
            try {
                String[] texts = jar.split(";md5;");
                String md5 = texts.length > 1 ? texts[1].trim() : "";
                if (md5.startsWith("http")) {
                    md5 = fetchText(md5);
                }
                String jarUrl = texts[0].trim();
                File file;
                if (!md5.isEmpty()) {
                    String cacheName = Crypto.md5(jarUrl);
                    File cached = Path.jar(cacheName);
                    if (Path.exists(cached) && Crypto.equals(cached, md5)) {
                        file = cached;
                    } else if (jarUrl.startsWith("http")) {
                        file = download(jarUrl, cached);
                    } else {
                        file = new File(jarUrl.replace("file://", ""));
                    }
                } else if (jarUrl.startsWith("http")) {
                    file = download(jarUrl, Path.jar(Crypto.md5(jarUrl)));
                } else if (jarUrl.startsWith("file")) {
                    file = new File(jarUrl.substring("file://".length()));
                } else {
                    file = new File(jarUrl.replace("file://", ""));
                }
                if (!Path.exists(file)) throw new Exception("jar not found: " + jarUrl);
                load(key, file);
                errors.remove(key);
            } catch (Throwable e) {
                e.printStackTrace();
                System.err.println("[jar] load fail key=" + key + " jar=" + jar + " -> " + e);
                errors.put(key, e.getClass().getSimpleName() + ": " + e.getMessage());
                errors.put(Crypto.md5(String.valueOf(jar)), e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    private String fetchText(String url) {
        try (var res = com.github.catvod.net.OkHttp.newCall(url).execute()) {
            if (res.body() != null) return res.body().string().trim();
        } catch (Exception ignored) {
        }
        return "";
    }

    private File download(String url, File target) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (var res = com.github.catvod.net.OkHttp.newCall(url).execute()) {
            if (res.body() == null) throw new Exception("download failed: " + url);
            byte[] data = res.body().bytes();
            Files.write(target.toPath(), data);
        }
        return target;
    }

    private void load(String key, File file) throws Exception {
        File usable = ensureJvmClasses(file);
        URLClassLoader loader = new URLClassLoader(new URL[]{usable.toURI().toURL()}, JarLoader.class.getClassLoader());
        invokeInit(loader);
        invokeProxy(key, loader);
        loaders.put(key, loader);
    }

    private File ensureJvmClasses(File file) throws Exception {
        if (hasClassEntries(file)) return file;
        if (!hasDex(file)) throw new Exception("jar has neither classes nor dex: " + file.getName());
        File out = new File(file.getParentFile(), file.getName() + ".jvm.jar");
        if (Path.exists(out) && out.lastModified() >= file.lastModified()) return out;
        Dex2Jar.convert(file, out);
        if (!Path.exists(out)) throw new Exception("dex2jar failed: " + file.getName());
        return out;
    }

    private boolean hasClassEntries(File file) throws Exception {
        try (ZipFile zip = new ZipFile(file)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".class") && !name.startsWith("META-INF/")) return true;
            }
        }
        return false;
    }

    private boolean hasDex(File file) throws Exception {
        try (ZipFile zip = new ZipFile(file)) {
            return zip.getEntry("classes.dex") != null || zip.getEntry("classes2.dex") != null;
        }
    }

    private void invokeInit(URLClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Init");
            Method method = clz.getMethod("init", Context.class);
            method.invoke(clz, android.app.Application.get());
        } catch (Throwable e) {
            // optional
        }
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.InitOrigin");
            Method method = clz.getMethod("init", Context.class);
            method.invoke(clz, android.app.Application.get());
        } catch (Throwable e) {
            System.err.println("[jar] InitOrigin.init fail: " + e); if (e.getCause() != null) e.getCause().printStackTrace();
        }
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.InitOrigin");
            Method method = clz.getMethod("setClass", Class.class);
            method.invoke(clz, com.github.catvod.spider.SpiderCrypto.class);
            System.err.println("[jar] InitOrigin.setClass ok");
        } catch (Throwable e) {
            System.err.println("[jar] InitOrigin.setClass fail: " + e); if (e.getCause() != null) e.getCause().printStackTrace();
        }
        installPeUrlRewrite(loader);
    }

    private void installPeUrlRewrite(URLClassLoader loader) {
        try {
            Class<?> pe = loader.loadClass("com.github.catvod.spider.merge.Pe");
            String proxyKey = resolvePeProxyKey(loader);
            OkHttpClient base = com.github.catvod.net.OkHttp.client();
            // seedog/Cloudflare list pages need HTTP/2 (HTTP/1.1 => 403).
            // 4kcz.com resets HTTP/2 streams mid-body; use HTTP/1.1 for those hosts.
            OkHttpClient h1Only = base.newBuilder()
                    .protocols(java.util.Collections.singletonList(okhttp3.Protocol.HTTP_1_1))
                    .build();
            OkHttpClient client = base.newBuilder()
                    .addInterceptor((Interceptor) chain -> {
                        Request req = chain.request();
                        String original = req.url().toString();
                        String fixed = rewriteSpiderUrl(original);
                        if (!fixed.equals(original)) {
                            System.err.println("[jar] rewrite " + original + " -> " + fixed);
                            HttpUrl parsed = HttpUrl.parse(fixed);
                            if (parsed != null) req = req.newBuilder().url(parsed).build();
                        }
                        if (req.url().host().contains("xl01")) {
                            String origin = req.header("Origin");
                            if (origin == null || !origin.startsWith("http")) {
                                req = req.newBuilder().header("Origin", "https://" + req.url().host()).build();
                            }
                            if (req.header("Referer") == null) {
                                req = req.newBuilder().header("Referer", "https://" + req.url().host() + "/").build();
                            }
                            if (req.header("Accept") == null) {
                                req = req.newBuilder().header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8").build();
                            }
                        } else {
                            String ref = req.header("Referer");
                            if (ref != null && ref.contains("://") && ref.indexOf("://", ref.indexOf("://") + 3) > 0) {
                                int h = ref.lastIndexOf("http");
                                if (h >= 0) req = req.newBuilder().header("Referer", ref.substring(h)).build();
                            }
                            if (req.header("Accept") == null) {
                                req = req.newBuilder().header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8").build();
                            }
                        }
                        String host = req.url().host();
                        if (host.contains("4kcz") || host.contains("libvio")) {
                            try {
                                okhttp3.Response r = h1Only.newCall(req).execute();
                                if (r.code() >= 400) {
                                    System.err.println("[jar] http " + r.code() + " h1 " + req.url());
                                }
                                return r;
                            } catch (java.io.IOException ex) {
                                System.err.println("[jar] !!h1 " + req.url() + " -> " + ex);
                                throw ex;
                            }
                        }
                        long t0 = System.currentTimeMillis();
                        okhttp3.Response resp;
                        try {
                            resp = chain.proceed(req);
                        } catch (Throwable ex) {
                            String msg = String.valueOf(ex);
                            if (msg.contains("StreamReset") || msg.contains("INTERNAL_ERROR")) {
                                System.err.println("[jar] h2 reset, retry h1 " + req.url());
                                return h1Only.newCall(req).execute();
                            }
                            System.err.println("[jar] !! " + req.url() + " -> " + ex);
                            throw ex;
                        }
                        int code = resp.code();
                        int attempts = 0;
                        while (code == 521 && "GET".equals(req.method()) && attempts < 2) {
                            attempts++;
                            try { Thread.sleep(800L * attempts); } catch (InterruptedException ignored) {}
                            resp.close();
                            System.err.println("[jar] retry521#" + attempts + " " + req.url());
                            try {
                                resp = chain.proceed(req);
                            } catch (Throwable ex) {
                                System.err.println("[jar] !!retry " + req.url() + " -> " + ex);
                                throw ex;
                            }
                            code = resp.code();
                        }
                        if (code >= 400) {
                            System.err.println("[jar] http " + code + " " + (System.currentTimeMillis() - t0) + "ms " + req.url());
                        }
                        return resp;
                    }).build();
            Field fi = pe.getDeclaredField("i");
            fi.setAccessible(true);
            fi.set(null, proxyKey);
            for (String n : new String[]{"t0", "N", "zH", "tF"}) {
                Field f = pe.getDeclaredField(n);
                f.setAccessible(true);
                f.set(null, client);
            }
            System.err.println("[jar] Pe url rewrite installed proxy=" + proxyKey);
        } catch (ClassNotFoundException ignored) {
            // jar has no Pe helper
        } catch (Throwable e) {
            System.err.println("[jar] Pe url rewrite fail: " + e);
        }
    }

    private String resolvePeProxyKey(URLClassLoader loader) {
        try {
            Class<?> rs = loader.loadClass("com.github.catvod.spider.merge.RS");
            for (Method m : rs.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 0) continue;
                if (!Map.class.isAssignableFrom(m.getReturnType())) continue;
                m.setAccessible(true);
                Object v = m.invoke(null);
                if (v instanceof Map<?, ?> map && !map.isEmpty()) {
                    Object first = map.get("v.xl01.eu.cc");
                    if (first == null) first = map.values().iterator().next();
                    if (first != null) return first.toString();
                }
            }
        } catch (Throwable ignored) {
        }
        return "fan.cloudflare.182682.xyz";
    }

    static String rewriteSpiderUrl(String url) {
        if (url == null || url.isEmpty()) return url;
        String s = url;
        // seedhub.pro -> sidhub.cc -> seedog.cc (live domain)
        if (s.contains("seedhub.pro") || s.contains("sidhub.cc")) {
            s = s.replace("seedhub.pro", "seedog.cc").replace("sidhub.cc", "seedog.cc");
        }
        int first = s.indexOf("://");
        if (first > 0) {
            int second = s.indexOf("://", first + 3);
            if (second > 0) {
                int h = s.lastIndexOf("http", second);
                if (h >= 0 && (s.startsWith("https://", h) || s.startsWith("http://", h))) {
                    s = s.substring(h);
                }
            }
        }
        int sc = s.indexOf("://");
        if (sc > 0) {
            String head = s.substring(0, sc + 3);
            String tail = s.substring(sc + 3);
            int qm = tail.indexOf('?');
            String beforeQuery = qm >= 0 ? tail.substring(0, qm) : tail;
            String query = qm >= 0 ? tail.substring(qm) : "";
            int slash = beforeQuery.indexOf('/');
            String host = slash >= 0 ? beforeQuery.substring(0, slash) : beforeQuery;
            String path = slash >= 0 ? beforeQuery.substring(slash) : "/";
            while (path.contains("//")) path = path.replace("//", "/");
            if (path.isEmpty()) path = "/";
            if (query.startsWith("?&")) query = "?" + query.substring(2);
            if (path.matches("/s/[^/]+/\\d+/?") && query.length() > 1) {
                String qs = query.substring(1);
                StringBuilder kept = new StringBuilder();
                for (String kv : qs.split("&")) {
                    if (kv.startsWith("type=") && kv.length() > 5) {
                        if (kept.length() > 0) kept.append('&');
                        kept.append(kv);
                    }
                }
                query = kept.length() == 0 ? "" : "?" + kept;
            }
            s = head + host + path + query;
        }
        return s;
    }

    private void invokeProxy(String key, URLClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Proxy");
            Method method = clz.getMethod("proxy", Map.class);
            methods.put(key, method);
        } catch (Throwable e) {
            // optional
        }
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        String jaKey = Crypto.md5(String.valueOf(jar));
        String spKey = jaKey + key;
        return spiders.computeIfAbsent(spKey, k -> {
            try {
                parseJar(jaKey, jar);
                URLClassLoader loader = loaders.get(jaKey);
                if (loader == null) return new SpiderNull();
                String simple = api.split("csp_")[1];
                Class<?> clz = loader.loadClass("com.github.catvod.spider." + simple);
                Spider spider = (Spider) clz.getDeclaredConstructor().newInstance();
                spider.siteKey = key;
                spider.init(android.app.Application.get(), ext);
                return spider;
            } catch (Throwable e) {
                e.printStackTrace();
                System.err.println("[jar] spider fail key=" + key + " api=" + api + " -> " + e);
                errors.put(jaKey, e.getClass().getSimpleName() + ": " + e.getMessage());
                errors.put(key, e.getClass().getSimpleName() + ": " + e.getMessage());
                SpiderNull nullSpider = new SpiderNull();
                nullSpider.siteKey = key;
                return nullSpider;
            }
        });
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        Method method = null;
        if (recent != null && !recent.isEmpty()) method = methods.get(recent);
        Object[] result = invokeProxy(method, params);
        if (result != null) return result;
        for (Map.Entry<String, Method> entry : methods.entrySet()) {
            if (recent != null && entry.getKey().equals(recent)) continue;
            result = invokeProxy(entry.getValue(), params);
            if (result != null) return result;
        }
        return null;
    }

    private Object[] invokeProxy(Method method, Map<String, String> params) {
        if (method == null) return null;
        try {
            return (Object[]) method.invoke(null, params);
        } catch (Throwable e) {
            return null;
        }
    }

    public boolean isLoaded(String jar) {
        return loaders.containsKey(Crypto.md5(String.valueOf(jar)));
    }

    public int loadedCount() {
        return loaders.size();
    }
}

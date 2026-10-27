package com.fongmi.gateway.spider.drpy;

import com.github.catvod.net.OkHttp;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches and caches the rule script a drpy site points at ({@code ext}).
 *
 * <p>These sources keep their rule in a separate .js file on GitHub, and the configs reach it
 * through a gh-proxy URL. When that proxy dies the site still works, so a failure falls back to
 * the direct raw.githubusercontent URL and then to the mirror list below. Cached for half an hour:
 * the file is small but every source switch would otherwise re-download it.
 */
public final class DrpyLoader {

    private static final long TTL_MS = 30 * 60_000L;
    private static final String RAW = "raw.githubusercontent.com";

    /** Same mirrors as the jar pool; a rule file is just as likely to be behind a dead proxy. */
    private static final String[] MIRRORS = {
            "https://gh-proxy.org/",
            "https://ghfast.top/",
            "https://ghproxy.net/",
            "https://gh-proxy.com/",
            "https://gh.llkk.cc/",
    };

    private static final class Entry {
        final String text;
        final long at;

        Entry(String text, long at) {
            this.text = text;
            this.at = at;
        }
    }

    private static final ConcurrentHashMap<String, Entry> CACHE = new ConcurrentHashMap<>();

    /**
     * Rule files are small and every mirror attempt costs a connect timeout, so keep the whole
     * chain inside one short budget instead of the default 30s per hop.
     */
    private static final long FETCH_TIMEOUT_MS = 8_000;
    /** A dead rule URL should not re-run the mirror chain on every single request. */
    private static final long NEGATIVE_TTL_MS = 60_000;
    private static final ConcurrentHashMap<String, Long> FAILED = new ConcurrentHashMap<>();

    private DrpyLoader() {
    }

    public static String script(String url) {
        if (url == null) return "";
        String key = url.trim();
        if (key.isEmpty()) return "";
        if (!key.startsWith("http")) return "";
        Entry cached = CACHE.get(key);
        if (cached != null && System.currentTimeMillis() - cached.at < TTL_MS) return cached.text;
        Long failedAt = FAILED.get(key);
        if (failedAt != null && System.currentTimeMillis() - failedAt < NEGATIVE_TTL_MS) {
            return cached == null ? "" : cached.text;
        }
        String text = download(key);
        if (!text.isEmpty() && looksLikeScript(text)) {
            CACHE.put(key, new Entry(text, System.currentTimeMillis()));
            FAILED.remove(key);
            return text;
        }
        FAILED.put(key, System.currentTimeMillis());
        // keep a stale copy rather than failing outright when the network blips
        return cached == null ? "" : cached.text;
    }

    private static boolean looksLikeScript(String text) {
        String head = text.trim();
        if (head.isEmpty()) return false;
        // a proxy error page is HTML, not JS; caching it would poison the entry for 30 minutes
        return !head.startsWith("<!DOCTYPE") && !head.startsWith("<html") && !head.startsWith("<?xml");
    }

    private static String download(String url) {
        String direct = strip(url);
        String[] candidates = direct.equals(url)
                ? new String[]{url}
                : new String[]{url, direct, mirror(MIRRORS[0], direct), mirror(MIRRORS[1], direct),
                mirror(MIRRORS[2], direct), mirror(MIRRORS[3], direct), mirror(MIRRORS[4], direct)};
        for (String candidate : candidates) {
            String text = get(candidate);
            if (!text.isEmpty() && looksLikeScript(text)) return text;
        }
        return "";
    }

    private static String get(String url) {
        try (okhttp3.Response response = OkHttp.client(FETCH_TIMEOUT_MS)
                .newCall(new okhttp3.Request.Builder().url(url).build()).execute()) {
            okhttp3.ResponseBody body = response.body();
            return body == null ? "" : body.string();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String mirror(String prefix, String url) {
        return prefix + url;
    }

    /** {@code https://gh-proxy.org/https://raw.githubusercontent.com/...} -> the raw URL. */
    private static String strip(String url) {
        int at = url.indexOf("https://" + RAW);
        if (at <= 0) at = url.indexOf("http://" + RAW);
        return at > 0 ? url.substring(at) : url;
    }
}

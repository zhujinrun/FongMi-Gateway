package com.fongmi.gateway.loader;

import com.fongmi.gateway.Repo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点域名配置（siteconfig）同步。
 *
 * <p>加固壳里有一批 spider —— {@code AiNewWogg}（玩偶）、{@code AiNewJuTou}、{@code AiNewPianKu}
 * （盘库）、{@code AiNewWoNiu4K}（蜗牛4K）、{@code AiNewLibvio}、{@code AiQwMkv}（七味）——
 * 不在字节码里写死站址：{@code init()} 先调 {@code merge.x0.e.J(key)} 读
 * {@code <data>/files/AiWex/siteconfig} 里该 key 的域名数组，逐个探测挑一个能通的，
 * 探测不到才退回代码里那个兜底域名。
 *
 * <p>安卓壳由配置中心下发这个文件；桌面网关原先没有这条链路，{@code files/AiWex} 一直是空的，
 * 于是这些源一律撞死在兜底域名上。绝大多数兜底域名早已停用（例如玩偶的
 * {@code wogg.xxooo.cf} 连 DNS 都没有），请求全失败、解析结果为空骨架 ——
 * 表现和「上游没内容」完全一样，光看返回体判断不出来。
 *
 * <p>本类有两条来源，按顺序取：
 * <ol>
 *   <li><b>官方下发链路</b>（主）：{@code item.txt} 的 {@code config_site} 字段指向真正的映射，
 *       伪装成 .jpg，就是安卓壳收到的原件。站长换域名后这里立刻生效，不必等我们发版。</li>
 *   <li><b>仓库快照</b> {@code jar/prebuilt/siteconfig.json}（兜底）：上游连不上时的基线，
 *       也承载我们手工补的候选域名（官方表里没有、但实测能通的镜像）。</li>
 * </ol>
 * 合并规则是<b>上游优先、仓库补充</b>：同名键以上游顺序开头，仓库里上游没有的域名追加到尾部，
 * 一个候选都不丢 —— 探测本来就是逐个试活，多一个死域名只是多一次探测，不会误伤。
 *
 * <p><b>为什么不每次启动都拉</b>：这条检查发生在 spider 预热之前（这些源 init 时就要读文件），
 * 所以它多等的每一秒都直接推迟首批请求。上游挂了的时候三次串行超时会很难看。因此按
 * {@code <data>/files/AiWex/.sitechecked} 的 mtime 做 6 小时节流：到期才探一次，探完无论
 * 成败都记账（失败就是「静默放弃，等下一个窗口」，绝不在这条路径上重试）。
 *
 * <p><b>容错：</b>文件缺失、内容不是合法 JSON、或某个值不是 http(s) 字符串数组，一律整份丢弃并保持
 * 本地现状，绝不拿半截数据覆盖；本地内容与远程一致时也不重写，避免每次启动都动文件。
 */
public final class SiteDomains {

    /** 官方下发入口，同一份 item.txt 挂了三台 OSS。
     *
     * <p>只取到第一个能用的就停：三个地址都死的情形才是「上游整体不可达」，那本来也不该等三次。
     * 全部是 http —— 官方没配 https，实测三条都能直连。
     *
     * <p>{@code -Dgateway.siteconfig.entry=<url>[|<url>…]} 可整组覆盖（测试钩子，也留着应付官方
     * 换 {@code ossfiles} 目录的情形 —— 那个 id 是站长账号目录，不是常量）。
     */
    private static final String[] ENTRY = {
            "http://ossduo.baicanuc.cn/ossfiles/1768320816/item.txt",
            "http://ossone.baicanuc.cn/ossfiles/1768320816/item.txt",
            "http://upload.baicanuc.cn/ossfiles/1768320816/item.txt",
    };

    private static String[] entries() {
        String override = System.getProperty("gateway.siteconfig.entry");
        if (override == null || override.isBlank()) return ENTRY;
        return override.split("\\|");
    }

    /** 上游检查间隔。6 小时：比站长换域名的节奏密，又不至于每次冷启动都吃一次网络。 */
    private static final long CHECK_INTERVAL = 6 * 60 * 60 * 1000L;

    /** 连接/读取超时。上游是 OSS 直连，正常毫秒级；这两项决定「上游全死时启动慢多少」。 */
    private static final int CONNECT_TIMEOUT = 2000;
    private static final int READ_TIMEOUT = 3000;

    /** 单份映射的实际大小是 1~2KB；512KB 上限纯为防止对端返回一个超大响应把内存吃掉。 */
    private static final int MAX_BODY = 512 * 1024;

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/125.0.0.0 Safari/537.36";

    private SiteDomains() {
    }

    /** Pull the site-domain map and drop it where the guarded payload looks for it. */
    public static void sync(File dataRoot) {
        try {
            File dir = new File(dataRoot, "files/AiWex");
            File out = new File(dir, "siteconfig");
            File stamp = new File(dir, ".sitechecked");

            // 本地已有内容、且上次检查还在有效期内 → 整条路径零网络。
            if (out.exists() && fresh(stamp)) return;

            Map<String, List<String>> repo = parse(JarLoader.fetchText(base() + "prebuilt/siteconfig.json"));
            Map<String, List<String>> upstream = fetchUpstream();
            Map<String, List<String>> merged = merge(repo, upstream);

            if (merged.isEmpty()) {
                // 两条来源都拿不到：保持现状（宁可沿用旧文件），但记账，免得每次启动都重撞一遍。
                touch(stamp);
                System.err.println("[siteconfig] neither upstream nor repo map is reachable, keeping current file");
                return;
            }

            String text = serialize(merged);
            if (out.exists()) {
                String local = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8).trim();
                if (local.equals(text)) {
                    touch(stamp);
                    return;
                }
            }
            if (!dir.exists() && !dir.mkdirs()) return;
            Files.write(out.toPath(), text.getBytes(StandardCharsets.UTF_8));
            touch(stamp);
            System.out.println("[siteconfig] synced " + merged.size() + " keys (upstream="
                    + (upstream.isEmpty() ? "unavailable" : upstream.size() + " keys")
                    + ", repo=" + repo.size() + " keys)");
        } catch (Throwable e) {
            System.err.println("[siteconfig] sync failed (ignored): " + e);
        }
    }

    /**
     * 官方链路：{@code item.txt} 是个装饰用的 JSON（vod_Name/notice/gg），真正的映射在它的
     * {@code config_site} 字段里；那个地址伪装成 .jpg，内容是纯 JSON。
     *
     * <p>任何一步失败都只是「这轮没有上游」，返回空 map 让仓库快照顶上。
     */
    private static Map<String, List<String>> fetchUpstream() {
        for (String entry : entries()) {
            String text = httpGet(entry);
            if (text.isEmpty()) continue;
            String link;
            try {
                link = new JSONObject(text).optString("config_site", "");
            } catch (Throwable e) {
                continue;
            }
            if (!link.startsWith("http://") && !link.startsWith("https://")) continue;
            Map<String, List<String>> parsed = parse(httpGet(link));
            if (!parsed.isEmpty()) return parsed;
        }
        return new LinkedHashMap<>();
    }

    /** Minimal GET with hard timeouts. Uses plain HttpURLConnection on purpose: no shared pool, no retry. */
    private static String httpGet(String url) {
        HttpURLConnection conn = null;
        try {
            java.net.URLConnection raw = java.net.URI.create(url).toURL().openConnection();
            if (!(raw instanceof HttpURLConnection http)) return "";
            conn = http;
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", UA);
            if (conn.getResponseCode() / 100 != 2) return "";
            try (InputStream in = conn.getInputStream()) {
                return new String(in.readNBytes(MAX_BODY), StandardCharsets.UTF_8).trim();
            }
        } catch (Throwable e) {
            return "";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 上游优先、仓库补充：同名键用上游的域名打头（新鲜），仓库里上游没有的追加到尾部。
     * 不用 {@code putIfAbsent} 的 key 级覆盖 —— 那样会把我们手工补的可用镜像丢掉。
     */
    private static Map<String, List<String>> merge(Map<String, List<String>> repo, Map<String, List<String>> upstream) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : upstream.entrySet()) out.put(e.getKey(), new ArrayList<>(e.getValue()));
        for (Map.Entry<String, List<String>> e : repo.entrySet()) {
            List<String> cur = out.get(e.getKey());
            if (cur == null) {
                out.put(e.getKey(), new ArrayList<>(e.getValue()));
                continue;
            }
            for (String url : e.getValue()) if (!contains(cur, url)) cur.add(url);
        }
        return out;
    }

    /**
     * 尾斜杠不算差异：上游写 {@code https://x.xyz/}、仓库写 {@code https://x.xyz} 是同一台主机，
     * 按字符串比会重复探测。保留先出现的那条写法（即上游的）。
     */
    private static boolean contains(List<String> urls, String url) {
        String bare = trimSlash(url);
        for (String have : urls) if (trimSlash(have).equals(bare)) return true;
        return false;
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Parse and validate a {key:[url]} map. Anything off (non-array value, non-string entry,
     * non-http scheme, quote/backslash that would break the hand-rolled serializer) drops the
     * whole document, so a truncated download or an HTML error page can never reach the file.
     */
    @SuppressWarnings("unchecked") // org.json's keys() is a raw Iterator
    private static Map<String, List<String>> parse(String text) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) return out;
        try {
            JSONObject src = new JSONObject(text);
            for (Iterator<String> it = src.keys(); it.hasNext(); ) {
                String key = it.next();
                JSONArray arr = src.optJSONArray(key);
                if (arr == null || arr.length() == 0) return new LinkedHashMap<>();
                List<String> urls = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.opt(i);
                    if (!(item instanceof String)) return new LinkedHashMap<>();
                    String url = ((String) item).trim();
                    if (!url.startsWith("http://") && !url.startsWith("https://")) return new LinkedHashMap<>();
                    if (url.indexOf('"') >= 0 || url.indexOf('\\') >= 0) return new LinkedHashMap<>();
                    if (!urls.contains(url)) urls.add(url);
                }
                out.put(key, urls);
            }
        } catch (Throwable e) {
            return new LinkedHashMap<>();
        }
        return out;
    }

    /**
     * Hand-rolled serialization: org.json escapes every '/' as '\/', which is legal but unreadable
     * in the repo copy of this file. Keys and URLs are validated by {@link #parse}, so no escaping
     * is needed beyond the quotes we add ourselves.
     */
    private static String serialize(Map<String, List<String>> map) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, List<String>> e : map.entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append('"').append(e.getKey()).append("\":[");
            List<String> urls = e.getValue();
            for (int i = 0; i < urls.size(); i++) {
                if (i > 0) out.append(',');
                out.append('"').append(urls.get(i)).append('"');
            }
            out.append(']');
        }
        return out.append('}').toString();
    }

    /** `.sitechecked` 的 mtime 就是「上次探上游的时间」，内容无所谓，所以只 touch 不写。 */
    private static boolean fresh(File stamp) {
        return stamp.exists() && System.currentTimeMillis() - stamp.lastModified() < CHECK_INTERVAL;
    }

    private static void touch(File stamp) {
        try {
            File dir = stamp.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) return;
            if (!stamp.exists() && !stamp.createNewFile()) return;
            stamp.setLastModified(System.currentTimeMillis());
        } catch (Throwable ignored) {
        }
    }

    private static String base() {
        String override = System.getProperty("gateway.repo");
        if (override != null && !override.isEmpty()) return override.endsWith("/") ? override : override + "/";
        return Repo.BASE;
    }
}

package com.fongmi.gateway.loader;

import com.fongmi.gateway.Repo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 站点域名配置（siteconfig）同步。
 *
 * <p>加固壳里有一批 spider —— {@code AiNewWogg}（玩偶）、{@code AiNewJuTou}、{@code AiNewPianKu}
 * （盘库）、{@code AiNewWoNiu4K}（蜗牛4K）、{@code AiNewLibvio}、{@code AiQwMkv}（七味）——
 * 不在字节码里写死站址：{@code init()} 先调 {@code merge.x0.e.J(key)} 读
 * {@code <data>/files/AiWex/siteconfig} 里该 key 的域名数组，逐个探测挑一个能通的，
 * 探测不到才退回代码里那个兜底域名。
 *
 * <p>安卓壳由配置中心下发这个文件；桌面网关没有这条链路，{@code files/AiWex} 一直是空的，
 * 于是这些源一律撞死在兜底域名上。绝大多数兜底域名早已停用（例如玩偶的
 * {@code wogg.xxooo.cf} 连 DNS 都没有），请求全失败、解析结果为空骨架 ——
 * 表现和「上游没内容」完全一样，光看返回体判断不出来。
 *
 * <p>所以把该文件也纳入 {@code jar/} 分发通道：远程 {@code prebuilt/siteconfig.json} 一更新，
 * 换域名只需推仓库，不必重发播放器版本 —— 与 prebuilt 翻译产物同一取舍。
 *
 * <p><b>容错：</b>文件缺失、内容不是合法 JSON、或某个值不是 http(s) 字符串数组，一律整份丢弃并保持
 * 本地现状，绝不拿半截数据覆盖；本地内容与远程一致时也不重写，避免每次启动都动文件。
 */
public final class SiteDomains {

    private SiteDomains() {
    }

    /** Pull the remote site-domain map and drop it where the guarded payload looks for it. */
    public static void sync(File dataRoot) {
        try {
            String text = JarLoader.fetchText(base() + "prebuilt/siteconfig.json");
            if (text.isEmpty()) return;
            String normalized = normalize(text);
            if (normalized == null) {
                System.err.println("[siteconfig] remote payload is not a valid {key:[url]} map, ignored");
                return;
            }
            File dir = new File(dataRoot, "files/AiWex");
            File out = new File(dir, "siteconfig");
            if (out.exists()) {
                String local = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8).trim();
                if (local.equals(normalized)) return;
            }
            if (!dir.exists() && !dir.mkdirs()) return;
            Files.write(out.toPath(), normalized.getBytes(StandardCharsets.UTF_8));
            System.out.println("[siteconfig] site domains synced: " + normalized);
        } catch (Throwable e) {
            System.err.println("[siteconfig] sync failed (ignored): " + e);
        }
    }

    /**
     * Re-serialize after validating every entry, so a truncated download or an HTML error page
     * can never reach the file. Returns null when anything looks off.
     */
    private static String normalize(String text) {
        try {
            JSONObject src = new JSONObject(text);
            // Hand-rolled serialization: org.json escapes every '/' as '\/', which is legal but
            // unreadable in the repo copy of this file. Keys and URLs are validated below, so no
            // escaping is needed beyond the quotes we add ourselves.
            StringBuilder out = new StringBuilder("{");
            int keys = 0;
            for (java.util.Iterator<String> it = src.keys(); it.hasNext(); ) {
                String key = it.next();
                JSONArray arr = src.optJSONArray(key);
                if (arr == null || arr.length() == 0) return null;
                if (keys++ > 0) out.append(',');
                out.append('"').append(key).append("\":[");
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.opt(i);
                    if (!(item instanceof String)) return null;
                    String url = ((String) item).trim();
                    if (!url.startsWith("http://") && !url.startsWith("https://")) return null;
                    if (url.indexOf('"') >= 0 || url.indexOf('\\') >= 0) return null;
                    if (i > 0) out.append(',');
                    out.append('"').append(url).append('"');
                }
                out.append(']');
            }
            return keys == 0 ? null : out.append('}').toString();
        } catch (Throwable e) {
            return null;
        }
    }

    private static String base() {
        String override = System.getProperty("gateway.repo");
        if (override != null && !override.isEmpty()) return override.endsWith("/") ? override : override + "/";
        return Repo.BASE;
    }
}

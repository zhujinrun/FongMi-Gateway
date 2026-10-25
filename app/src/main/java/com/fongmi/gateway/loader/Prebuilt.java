package com.fongmi.gateway.loader;

import com.fongmi.gateway.Repo;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 预翻译产物投放（pre-built JVM translation）。
 *
 * <p>加固壳 spider（wexguard 等）在桌面端<b>无法自举翻译</b>，两条路都断：
 * <ul>
 *   <li>{@code Init.init} 需要 dalvik {@code DexClassLoader} + ARM {@code .so}，JVM 上不存在；</li>
 *   <li>退一步只翻壳里的 39KB dex 也没用 —— 104 个 {@code *Guard} 类全是空壳，业务逻辑在加密载荷里。</li>
 * </ul>
 *
 * <p>唯一可行的路径是：ARM 环境（模拟器 + frida）里让壳自己解密出 payload，再把解密后的 dex
 * 用 enjarify 翻成 class、用 InitAlign 修掉 R8 构造器上提（见 {@code tools/wexdump/README.md}）。
 *
 * <p>产物放在仓库 {@code jar/prebuilt/<sha256>.jvm.jar}，与 {@code fty_spider.jar} /
 * {@code wex_spider.jar} 走同一条 gh-proxy 分发通道 —— 好处是上游换载荷时只需推仓库、
 * 不必重发播放器版本。绑定键是<b>源 jar 的 sha256</b>（配置里那个伪装成 .jpg 的壳包），
 * 所以：配置不写 {@code ;md5;} 也能命中、上游重打包但内容等价也能命中，而载荷一旦真变，
 * 指纹跟着变 → 拉不到 → 回落 d2j 并保持「需 Android native」提示，
 * <b>绝不会把旧版本的类塞给新载荷</b>。
 *
 * <p>注意这里和公共池的区别：公共池是所有配置共用的类，被 {@code DEFAULT_SPIDER} 无条件挂载；
 * 本产物是<b>某个配置私有</b>的（类名与业务逻辑绑定那一份载荷），只在对应指纹出现时才取，
 * 取不到也无害。
 */
final class Prebuilt {

    private Prebuilt() {
    }

    /** 本次进程内已确认不存在的指纹，避免同一启动里反复等超时。 */
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();

    /** True if a translation matching {@code src} was fetched and written to {@code out}. */
    static boolean install(File src, File out) {
        // Only guarded shells are worth a lookup. Public pools (wex_spider.jar) are dex-only
        // too and would otherwise burn a pointless request on every pool update.
        if (!looksGuarded(src)) return false;
        String key = sha256(src);
        if (key.isEmpty() || MISSING.contains(key)) return false;
        String url = base() + "prebuilt/" + key + ".jvm.jar";
        Exception last = null;
        for (String cand : JarLoader.expand(url)) {
            try (var res = com.github.catvod.net.OkHttp.newCall(cand).execute()) {
                if (res.code() != 200 || res.body() == null) continue;
                byte[] data = res.body().bytes();
                if (!looksLikeClassesJar(data)) {
                    System.err.println("[jar] prebuilt translation is not a class jar, ignored: " + cand);
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                Files.write(out.toPath(), data);
                System.err.println("[jar] prebuilt translation fetched: " + key + " (" + data.length + " B)");
                return true;
            } catch (Exception e) {
                last = e;
            }
        }
        MISSING.add(key);
        System.err.println("[jar] no prebuilt translation for payload " + shortKey(key)
                + " (will report as Android-native only)" + (last == null ? "" : " last=" + last));
        return false;
    }

    /**
     * Base URL of the {@code jar/} channel. Overridable for self-hosted mirrors and for
     * testing without pushing to the real repo: {@code -Dgateway.repo=http://host/path/}.
     */
    private static String base() {
        String override = System.getProperty("gateway.repo");
        if (override != null && !override.isEmpty()) return override.endsWith("/") ? override : override + "/";
        return Repo.BASE;
    }

    /** Guarded shells carry the encrypted payload as an {@code assets/*.guard} entry. */
    private static boolean looksGuarded(File jar) {
        try (var zip = new java.util.zip.ZipFile(jar)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("assets/") && name.endsWith(".guard")) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Cheap sanity check: a zip whose entries include at least one .class. */
    private static boolean looksLikeClassesJar(byte[] data) {
        if (data.length < 4 || data[0] != 'P' || data[1] != 'K') return false;
        File tmp = null;
        try {
            tmp = File.createTempFile("prebuilt", ".jar");
            Files.write(tmp.toPath(), data);
            try (var zip = new java.util.zip.ZipFile(tmp)) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (name.endsWith(".class") && !name.startsWith("META-INF/")) return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    private static String shortKey(String key) {
        return key.length() > 12 ? key.substring(0, 12) + "…" : key;
    }

    private static String sha256(File file) {
        try (var in = Files.newInputStream(file.toPath())) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest()).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }
}

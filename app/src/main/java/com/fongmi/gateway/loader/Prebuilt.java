package com.fongmi.gateway.loader;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

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
 * <p>产物绑定键是<b>源 jar 的 sha256</b>，资源放在 {@code /prebuilt/<sha256>.jvm.jar}：
 * 用内容指纹而不是配置里的 {@code ;md5;}，这样即使配置没写 md5、或上游重新打包导致整包 md5 变化，
 * 只要壳的字节一致就仍然命中；反过来，上游一改载荷指纹就变，命中不了，自然回落到底层的
 * d2j 翻译与「需 Android native」判定 —— <b>绝不会把旧版本的类塞给新载荷</b>。
 */
final class Prebuilt {

    private Prebuilt() {
    }

    private static final String DIR = "/prebuilt/";
    private static final String SUFFIX = ".jvm.jar";

    /** True if a bundled translation matches {@code src} and was written to {@code out}. */
    static boolean install(File src, File out) {
        String key = sha256(src);
        if (key.isEmpty()) return false;
        try (InputStream in = Prebuilt.class.getResourceAsStream(DIR + key + SUFFIX)) {
            if (in == null) return false;
            File parent = out.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (out.length() == 0) {
                out.delete();
                return false;
            }
            System.err.println("[jar] prebuilt translation hit: " + key + " (" + out.length() + " B)");
            return true;
        } catch (Exception e) {
            System.err.println("[jar] prebuilt install failed: " + key + " -> " + e);
            out.delete();
            return false;
        }
    }

    private static String sha256(File file) {
        try (InputStream in = Files.newInputStream(file.toPath())) {
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

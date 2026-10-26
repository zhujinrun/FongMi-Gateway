package com.fongmi.gateway.loader;

import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Standalone check of {@link Prebuilt#install} integrity gate.
 *
 * <p>Builds a fake guarded shell (a zip with {@code assets/x.guard}) so {@code looksGuarded}
 * passes, serves a temp {@code prebuilt/} repo over loopback HTTP (OkHttp cannot read
 * {@code file://}), and exercises four cases: good md5 / tampered md5 / missing md5 / garbage md5.
 */
public final class Md5Probe {

    public static void main(String[] args) throws Exception {
        File work = Files.createTempDirectory("md5probe").toFile();
        File prebuiltDir = new File(work, "prebuilt");
        prebuiltDir.mkdirs();

        // Fake guarded shell: its sha256 becomes the lookup key.
        File src = new File(work, "shell.jar");
        writeGuardedShell(src);
        String key = sha256(src);
        System.out.println("key=" + key);

        // Real translation bytes (any class jar works; Prebuilt only checks md5 + zip/.class).
        byte[] payload = Files.readAllBytes(new File(
                "jar/prebuilt/9e82d9058843e897e3d75054604d4371949006a297e176e8c7ce668b50d2cad1.jvm.jar").toPath());
        String good = md5(payload);

        File artifact = new File(prebuiltDir, key + ".jvm.jar");
        File checksum = new File(prebuiltDir, key + ".jvm.jar.md5");
        Files.write(artifact.toPath(), payload);

        // Serve the work dir so Prebuilt can fetch via the same OkHttp path it uses in production.
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            File f = new File(work, path.replaceFirst("^/", ""));
            byte[] body = f.isFile() ? Files.readAllBytes(f.toPath()) : null;
            exchange.sendResponseHeaders(body == null ? 404 : 200, body == null ? -1 : body.length);
            if (body != null) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            }
        });
        server.start();
        System.setProperty("gateway.repo", "http://127.0.0.1:" + server.getAddress().getPort() + "/");
        System.out.println("repo=" + System.getProperty("gateway.repo"));

        int pass = 0;
        try {
            // Case 1: matching md5 -> must install.
            Files.writeString(checksum.toPath(), good + "\r\n");
            File out1 = new File(work, "out1.jar");
            boolean ok1 = Prebuilt.install(src, out1);
            boolean c1 = ok1 && out1.isFile();
            System.out.println("case1 good md5      expect=install   got=" + ok1 + (c1 ? "  PASS" : "  FAIL"));
            if (c1) pass++;

            // Case 2: tampered md5 -> must reject.
            clearMissing();
            Files.writeString(checksum.toPath(), "0000000000000000000000000000dead\r\n");
            File out2 = new File(work, "out2.jar");
            boolean ok2 = Prebuilt.install(src, out2);
            boolean c2 = !ok2 && !out2.exists();
            System.out.println("case2 tampered md5  expect=reject   got=" + ok2 + (c2 ? "  PASS" : "  FAIL"));
            if (c2) pass++;

            // Case 3: md5 file deleted -> must reject (fail closed).
            clearMissing();
            checksum.delete();
            File out3 = new File(work, "out3.jar");
            boolean ok3 = Prebuilt.install(src, out3);
            boolean c3 = !ok3 && !out3.exists();
            System.out.println("case3 md5 missing   expect=reject   got=" + ok3 + (c3 ? "  PASS" : "  FAIL"));
            if (c3) pass++;

            // Case 4: md5 file present but garbage -> must reject.
            clearMissing();
            Files.writeString(checksum.toPath(), "not-a-checksum\n");
            File out4 = new File(work, "out4.jar");
            boolean ok4 = Prebuilt.install(src, out4);
            boolean c4 = !ok4 && !out4.exists();
            System.out.println("case4 garbage md5   expect=reject   got=" + ok4 + (c4 ? "  PASS" : "  FAIL"));
            if (c4) pass++;
        } finally {
            server.stop(0);
        }
        System.out.println("result " + pass + "/4");
        if (pass != 4) System.exit(1);
    }

    /** Prebuilt caches negative lookups per process; the probe needs a clean slate per case. */
    private static void clearMissing() throws Exception {
        var f = Prebuilt.class.getDeclaredField("MISSING");
        f.setAccessible(true);
        ((java.util.Set<?>) f.get(null)).clear();
    }

    private static void writeGuardedShell(File file) throws Exception {
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(file.toPath()))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("assets/payload.guard"));
            zos.write(new byte[]{1, 2, 3});
            zos.closeEntry();
        }
    }

    private static String sha256(File file) throws Exception {
        try (var in = Files.newInputStream(file.toPath())) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        }
    }

    private static String md5(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(data));
    }
}

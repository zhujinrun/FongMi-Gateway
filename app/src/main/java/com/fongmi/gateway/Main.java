package com.fongmi.gateway;

import android.content.Context;

import com.fongmi.gateway.config.VodConfig;
import com.fongmi.gateway.server.GatewayServer;
import com.github.catvod.Init;
import com.github.catvod.utils.Prefers;

import java.io.File;
import java.security.SecureRandom;

public class Main {

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 9979;
        String token = "";
        File dataDir = new File(System.getProperty("user.home"), ".gateway");
        String configUrl = null;
        boolean quiet = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host" -> host = args[++i];
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--token" -> token = args[++i];
                case "--data" -> dataDir = new File(args[++i]);
                case "--config" -> configUrl = args[++i];
                case "--quiet" -> quiet = true;
                case "--help", "-h" -> {
                    printHelp();
                    return;
                }
                default -> {
                    if (args[i].startsWith("http")) configUrl = args[i];
                }
            }
        }

        if (quiet) System.setProperty("gateway.quiet", "true");

        com.github.catvod.Proxy.setPort(port);

        // spider uses AES/ECB/PKCS7Padding (BouncyCastle only; SunJCE has PKCS5Padding)
        java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        if (!dataDir.exists()) dataDir.mkdirs();
        com.github.catvod.utils.Path.setRoot(dataDir);
        Context.setRootDir(dataDir);
        Init.init(new Context());
        Prefers.init(new Context());
        android.app.Activity seed = new android.app.Activity();
        android.app.ActivityThread.currentActivityThread().putActivity("gateway", seed);

        if (token.isEmpty() && !"127.0.0.1".equals(host) && !"0.0.0.0".equals(host)) {
            token = randomToken();
            System.out.println("[gateway] generated token for non-local bind");
        }

        GatewayServer server = new GatewayServer(host, port, token);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        if (configUrl != null && !configUrl.isEmpty()) {
            try {
                VodConfig.get().load(configUrl);
                System.out.println("[gateway] config loaded: " + VodConfig.get().summary());
                String spider = VodConfig.get().getSpider();
                if (!spider.isEmpty()) {
                    com.fongmi.gateway.loader.BaseLoader.get().parseJar(spider, true);
                    System.out.println("[gateway] spider jar preloaded");
                }
            } catch (Exception e) {
                System.err.println("[gateway] config load failed: " + e.getMessage());
            }
        }

        System.out.println("[gateway] ready. endpoints:");
        System.out.println("  GET  /health");
        System.out.println("  GET|POST /config?url=...");
        System.out.println("  GET  /sites");
        System.out.println("  GET  /home?site=KEY");
        System.out.println("  GET  /category?site=KEY&tid=&pg=");
        System.out.println("  GET  /detail?site=KEY&id=");
        System.out.println("  GET  /search?site=KEY&key=&pg=");
        System.out.println("  GET  /player?site=KEY&flag=&id=");
        System.out.println("  POST /rpc");
        System.out.println("  POST /{siteKey}/init|home|category|detail|search|play  (Player catvod type8)");

        Thread.currentThread().join();
    }

    private static String randomToken() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static void printHelp() {
        System.out.println("""
                CatVod Gateway 0.1.0
                Usage: java -jar gateway.jar [options] [configUrl]

                  --host <ip>       bind address (default 127.0.0.1)
                  --port <n>        listen port (default 9979)
                  --token <t>       required X-Gateway-Token (auto if non-local)
                  --data <dir>      data directory (default ~/.gateway)
                  --config <url>    preload config
                  --quiet           reduce logs
                """);
    }
}

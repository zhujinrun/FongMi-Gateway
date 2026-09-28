package com.fongmi.gateway;

import android.content.Context;

import com.fongmi.gateway.config.VodConfig;
import com.fongmi.gateway.server.GatewayServer;
import com.github.catvod.Init;
import com.github.catvod.utils.Prefers;

import java.io.File;
import java.security.SecureRandom;

public class Main {

    private static final String REPO = "https://gh-proxy.org/https://github.com/zhujinrun/FongMi-Gateway/raw/refs/heads/fongmi/jar/";
    private static final String DEFAULT_SPIDER =
            REPO + "fty_spider.jar;md5;" + REPO + "fty_spider.jar.md5"
                    + "|" + REPO + "wex_spider.jar;md5;" + REPO + "wex_spider.jar.md5";

    public static void main(String[] args) throws Exception {
        com.fongmi.gateway.log.LogFilterStream.install();
        String host = "127.0.0.1";
        int port = 9979;
        String token = "";
        File dataDir = new File(System.getProperty("user.home"), ".gateway");
        String configUrl = null;
        String spiderFallback = "";
        boolean quiet = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host" -> host = args[++i];
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--token" -> token = args[++i];
                case "--data" -> dataDir = new File(args[++i]);
                case "--config" -> configUrl = args[++i];
                case "--spider" -> spiderFallback = args[++i];
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

        // default spider when --spider not given
        boolean spiderExplicit = spiderFallback != null && !spiderFallback.isEmpty();
        if (!spiderExplicit) {
            spiderFallback = DEFAULT_SPIDER;
        }

        GatewayServer server = new GatewayServer(host, port, token, spiderFallback);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

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

        // spider download + optional config: background so failures never kill/ block main
        final String spiderUrl = spiderFallback;
        final String cfg = configUrl;
        final File dataRoot = dataDir;
        Thread preload = new Thread(() -> {
            try {
                if (spiderUrl != null && !spiderUrl.isEmpty()) {
                    System.out.println("[gateway] preloading spider: " + spiderUrl);
                    com.github.catvod.utils.Path.setRoot(dataRoot);
                    com.fongmi.gateway.loader.BaseLoader.get().clear();
                    com.fongmi.gateway.loader.BaseLoader.get().parseJar(spiderUrl, true);
                    String err = com.fongmi.gateway.loader.BaseLoader.get().getError("", spiderUrl);
                    if (err == null || err.isEmpty()) {
                        System.out.println("[gateway] spider preloaded: " + spiderUrl);
                    } else {
                        System.err.println("[gateway] spider preload failed (server still up): " + err);
                    }
                }
                if (cfg != null && !cfg.isEmpty()) {
                    loadConfigWithSpiderFallback(cfg, spiderUrl);
                }
            } catch (Throwable t) {
                System.err.println("[gateway] preload error (ignored): " + t);
            }
        }, "gateway-preload");
        preload.setDaemon(true);
        preload.start();

        Thread.currentThread().join();
    }

    private static void loadConfigWithSpiderFallback(String configUrl, String spiderFallback) {
        try {
            VodConfig.get().load(configUrl);
            String spider = VodConfig.get().getSpider();
            boolean okSpider = false;
            if (spider != null && !spider.isEmpty()) {
                com.fongmi.gateway.loader.BaseLoader.get().clear();
                com.fongmi.gateway.loader.BaseLoader.get().parseJar(spider, true);
                String err = com.fongmi.gateway.loader.BaseLoader.get().getError("", spider);
                okSpider = err == null || err.isEmpty();
                if (okSpider) {
                    System.out.println("[gateway] spider jar preloaded: " + spider);
                } else {
                    System.err.println("[gateway] config spider failed: " + err);
                }
            }
            if (!okSpider && spiderFallback != null && !spiderFallback.isEmpty()) {
                com.fongmi.gateway.loader.BaseLoader.get().clear();
                com.fongmi.gateway.loader.BaseLoader.get().parseJar(spiderFallback, true);
                String err = com.fongmi.gateway.loader.BaseLoader.get().getError("", spiderFallback);
                if (err == null || err.isEmpty()) {
                    VodConfig.get().setSpider(spiderFallback);
                    System.out.println("[gateway] using --spider fallback: " + spiderFallback);
                    okSpider = true;
                } else {
                    System.err.println("[gateway] fallback spider also failed: " + err);
                }
            }
            if (!okSpider) {
                System.err.println("[gateway] no runnable spider jar; csp sites will fail until --spider is set");
            }
            System.out.println("[gateway] config loaded: " + VodConfig.get().summary());
        } catch (Throwable t) {
            System.err.println("[gateway] config load failed (ignored): " + t);
        }
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
                  --spider <url>    fallback spider jar if config spider fails
                                    (default: fty_spider.jar + wex_spider.jar from
                                    this repo, both downloaded with md5; github links
                                    auto-retry mirrors; '|' joins multiple jars into
                                    a pool, class lookup tries each in order)
                  --quiet           reduce logs
                """);
    }
}

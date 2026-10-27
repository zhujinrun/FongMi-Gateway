package com.fongmi.gateway;

import android.content.Context;

import com.fongmi.gateway.config.Site;
import com.fongmi.gateway.config.VodConfig;
import com.fongmi.gateway.server.GatewayServer;
import com.github.catvod.Init;
import com.github.catvod.utils.Prefers;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String REPO = Repo.BASE;
    private static final String DEFAULT_SPIDER =
            REPO + "fty_spider.jar;md5;" + REPO + "fty_spider.jar.md5"
                    + "|" + REPO + "wex_spider.jar;md5;" + REPO + "wex_spider.jar.md5";

    public static void main(String[] args) throws Exception {
        com.fongmi.gateway.log.LogFilterStream.install();
        String host = "0.0.0.0";
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
                // Must run before any spider's init(): several guarded spiders resolve their base
                // host from this file and silently fall back to a long-dead domain otherwise.
                com.fongmi.gateway.loader.SiteDomains.sync(dataRoot);
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
                warmAllSpiders();
            } catch (Throwable t) {
                System.err.println("[gateway] preload error (ignored): " + t);
            } finally {
                VodConfig.get().markReady();
            }
        }, "gateway-preload");
        preload.setDaemon(true);
        preload.start();

        Thread.currentThread().join();
    }

    /**
     * Warm every runnable spider source in the background. A spider's first
     * homeContent() pays a one-time cost (UrlChecker domain probing + widening + first CMS
     * fetch) that the user would otherwise hit on their first source click. Some spiders
     * (notably the T4 class behind 荐片/光影/奶酪) are far slower to cold-start on the
     * desktop (their Android-native init throws and falls back to a long network probe,
     * observed 69~129s) — well past the Player's 60s axios timeout, so a cold first click
     * ends in "network request failed". Warming every csp source here, off the request path,
     * moves that cost into startup and fills the result cache so the first real click is fast.
     * Runs with bounded concurrency so one slow spider (T4) can't wedge the whole queue
     * behind it. Failures are swallowed — a source that can't be warmed is simply cold on
     * first click (and can still be warmed on demand via /warm?site=KEY).
     */
    private static void warmAllSpiders() {
        Thread warmer = new Thread(() -> {
            List<Site> targets = new ArrayList<>();
            for (Site site : VodConfig.get().getSites()) {
                if (site.isSpider() && site.getApi().startsWith("csp_")) {
                    targets.add(site);
                }
            }
            if (targets.isEmpty()) return;

            // Put the default source first so the source the user is most likely to
            // click gets warmed before the rest of the queue. The gateway has no
            // knowledge of the Player's user-configured default, so fall back to the
            // config's first site (home) as the closest proxy.
            Site home = VodConfig.get().getHome();
            if (home != null) {
                String homeKey = home.getKey();
                for (int i = 0; i < targets.size(); i++) {
                    if (homeKey.equals(targets.get(i).getKey())) {
                        targets.add(0, targets.remove(i));
                        break;
                    }
                }
            }

            System.out.println("[gateway] warming " + targets.size() + " csp spider sources...");
            int concurrency = Math.min(4, targets.size());
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(concurrency);
            java.util.concurrent.atomic.AtomicInteger warmed = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (Site target : targets) {
                futures.add(pool.submit(() -> {
                    long t0 = System.currentTimeMillis();
                    try {
                        com.fongmi.gateway.api.SiteApi.homeContent(target.getKey());
                        warmed.incrementAndGet();
                        System.out.println("[gateway] warmed " + target.getKey() + " (" + target.getApi() + ") in "
                                + (System.currentTimeMillis() - t0) + "ms");
                    } catch (Throwable t) {
                        System.err.println("[gateway] warm " + target.getKey() + " failed (ignored): " + t.getMessage());
                    }
                }));
            }
            for (java.util.concurrent.Future<?> f : futures) {
                try {
                    f.get();
                } catch (Throwable ignored) {
                }
            }
            pool.shutdown();
            System.out.println("[gateway] warmed " + warmed.get() + "/" + targets.size() + " csp sources");
        }, "gateway-warm");
        warmer.setDaemon(true);
        warmer.start();
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
                } else if (isAndroidOnlyJar(err)) {
                    // config spider is an Android-guard jar (DexClassLoader + .so): expected on desktop,
                    // not a fault. Real implementations come from the --spider pool below.
                    System.out.println("[gateway] config spider is Android-only (DexClassLoader/native), desktop will use --spider pool");
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
            } else {
                reportSpiderCoverage();
            }
            System.out.println("[gateway] config loaded: " + VodConfig.get().summary());
        } catch (Throwable t) {
            System.err.println("[gateway] config load failed (ignored): " + t);
        }
    }

    private static boolean isAndroidOnlyJar(String err) {
        return err.contains("requires Android native/Dex") || err.contains("dalvik.system.DexClassLoader");
    }

    /** Known limitation report: list csp sites the loaded pool cannot resolve. */
    public static void reportSpiderCoverage() {
        try {
            List<String> missing = new ArrayList<>();
            int csp = 0;
            for (Site site : VodConfig.get().getSites()) {
                String api = site.getApi();
                if (api == null || !api.startsWith("csp_")) continue;
                csp++;
                if (!com.fongmi.gateway.loader.BaseLoader.get().hasSpider(api)) missing.add(site.getKey() + "(" + api + ")");
            }
            if (missing.isEmpty()) {
                System.out.println("[gateway] spider coverage: " + csp + "/" + csp + " csp sites resolvable");
            } else {
                System.err.println("[gateway] spider coverage: " + (csp - missing.size()) + "/" + csp
                        + " csp sites resolvable; missing " + missing.size() + " (known limitation): " + String.join(", ", missing));
            }
        } catch (Throwable t) {
            System.err.println("[gateway] spider coverage check failed (ignored): " + t);
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

                  --host <ip>       bind address (default 0.0.0.0)
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

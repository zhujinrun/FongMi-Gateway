package com.fongmi.gateway.spider.drpy;

import android.content.Context;

import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.Util;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.nodes.Element;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A drpy (TVBox {@code .js}) source, executed natively instead of through the vendor's JS bundle.
 *
 * <p>The reference implementation is drpy2.min.js: it pulls in cheerio, pako, crypto-js and six
 * more modules through {@code assets://} imports and then interprets the rule. None of that is
 * needed here -- every rule this gateway serves is a bare {@code var rule = {...}} whose fields are
 * data, so the rule is read once (Rhino turns the object literal into JSON) and everything except
 * the {@code js:} hooks is done in Java. The hooks themselves are small and run in Rhino with a
 * hand-written host ({@link DrpyJs}).
 *
 * <p>Semantics follow drpy2.min.js deliberately, including its quirks: {@code 二级:"*"} appends
 * {@code @@name@@pic} to the id and plays the detail page through the sniffer, and a player result
 * without {@code play_json} gets {@code jx:0, parse:1} forced on it.
 *
 * <p>Not implemented, because no rule we ship uses them: {@code class_parse}, {@code filter} as a
 * gzipped string, {@code play_json} regex tables, and xpath selectors. They degrade to "no data"
 * rather than throwing.
 */
public class DrpySpider extends Spider {

    private static final Pattern MEDIA = Pattern.compile("\\.(m3u8|mp4|flv|m4a|mp3|ts)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPECIAL = Pattern.compile("^(ftp|magnet|thunder|ws):");
    private static final Pattern FL_VAR = Pattern.compile("\\{\\{\\s*fl\\.([A-Za-z0-9_-]+)\\s*}}");

    private volatile String ext = "";
    private volatile DrpyRule rule;
    private volatile DrpyJs js;

    @Override
    public void init(Context context) throws Exception {
        init(context, ext);
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        ext = extend == null ? "" : extend.trim();
        // fail here rather than on the first request, so the loader can record a real error
        ready();
    }

    private DrpyRule ready() throws Exception {
        DrpyRule r = rule;
        if (r != null) return r;
        synchronized (this) {
            if (rule != null) return rule;
            String script = DrpyLoader.script(ext);
            if (script.isEmpty()) throw new IllegalStateException("rule script unreachable: " + ext);
            DrpyRule parsed = DrpyRule.from(DrpyJs.extractRule(script, siteKey));
            js = new DrpyJs(parsed);
            rule = parsed;
            log("loaded rule '" + parsed.title() + "' from " + host(ext));
            return rule;
        }
    }

    // ------------------------------------------------------------------ home

    @Override
    public String homeContent(boolean filter) throws Exception {
        DrpyRule r = ready();
        JsonArray classes = new JsonArray();
        String names = r.str("class_name");
        String ids = r.str("class_url");
        if (!names.isEmpty() && !ids.isEmpty()) {
            String[] n = names.split("&");
            String[] u = ids.split("&");
            for (int i = 0; i < Math.min(n.length, u.length); i++) {
                JsonObject c = new JsonObject();
                c.addProperty("type_id", u[i]);
                c.addProperty("type_name", n[i]);
                classes.add(c);
            }
        }
        JsonObject out = new JsonObject();
        out.add("class", classes);
        JsonObject f = r.filter();
        out.add("filters", f == null ? new JsonObject() : f);
        return out.toString();
    }

    @Override
    public String homeVideoContent() throws Exception {
        DrpyRule r = ready();
        String p = r.pick("推荐").trim();
        String home = r.absolute(r.homeUrl());
        if (p.isEmpty() || home.isEmpty()) return "";
        JsonArray list = parseList(p, home, "", r.doubleList(), "GET", null);
        return wrap(list, 1).toString();
    }

    // ------------------------------------------------------------------ category

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        DrpyRule r = ready();
        int page = number(pg, 1);
        String url = categoryUrl(r, tid, page, extend);
        JsonArray list = parseList(r.pick("一级"), url, tid, false, "GET", null);
        return wrap(list, page).toString();
    }

    /** drpy's category URL build: fyclass, the [..] page trick, filter_url, then fypage. */
    private String categoryUrl(DrpyRule r, String tid, int page, Map<String, String> extend) {
        String url = r.url().replace("fyclass", tid);
        if (url.contains("[") && url.contains("]")) {
            String inside = url.substring(url.indexOf('[') + 1, url.indexOf(']'));
            url = page == 1 ? inside : url.substring(0, url.indexOf('['));
        }
        String filterUrl = r.filterUrl();
        if (!filterUrl.isEmpty()) {
            if (!url.contains("fyfilter")) {
                if (!url.endsWith("&") && !filterUrl.startsWith("&")) url += "&";
                url += filterUrl;
            } else {
                url = url.replace("fyfilter", filterUrl);
            }
            url = url.replace("fyclass", tid);
            url = renderFilter(url, filterValues(r, tid, extend));
        }
        if (url.contains("fypage")) url = url.replace("fypage", String.valueOf(page));
        return r.absolute(url);
    }

    private Map<String, String> filterValues(DrpyRule r, String tid, Map<String, String> extend) {
        Map<String, String> fl = new LinkedHashMap<>();
        JsonObject def = r.filterDef();
        if (def != null && def.has(tid) && def.get(tid).isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : def.getAsJsonObject(tid).entrySet()) {
                fl.put(e.getKey(), e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString());
            }
        }
        if (extend != null) {
            for (Map.Entry<String, String> e : extend.entrySet()) {
                if (e.getValue() != null) fl.put(e.getKey(), e.getValue());
            }
        }
        return fl;
    }

    /** {@code gameId={{fl.cateId}}} -- only the {@code fl.*} form of jinja2 is in use here. */
    private String renderFilter(String url, Map<String, String> fl) {
        Matcher m = FL_VAR.matcher(url);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = fl.getOrDefault(m.group(1), "");
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ------------------------------------------------------------------ detail

    @Override
    public String detailContent(List<String> ids) throws Exception {
        DrpyRule r = ready();
        String id = ids == null || ids.isEmpty() ? "" : ids.get(0);
        String fyclass = "";
        String rest = id;
        int dollar = id.indexOf('$');
        if (dollar >= 0) {
            fyclass = id.substring(0, dollar);
            rest = id.substring(dollar + 1);
        }
        String[] extra = rest.split("@@");
        String detailId = extra[0];
        String url = buildDetailUrl(r, detailId, fyclass);

        JsonObject vod = new JsonObject();
        vod.addProperty("vod_id", id);
        vod.addProperty("vod_name", extra.length > 1 ? extra[1] : "");
        vod.addProperty("vod_pic", extra.length > 2 ? extra[2] : "");
        vod.addProperty("type_name", "");
        vod.addProperty("vod_year", "");
        vod.addProperty("vod_area", "");
        vod.addProperty("vod_remarks", "");
        vod.addProperty("vod_actor", "");
        vod.addProperty("vod_director", "");
        vod.addProperty("vod_content", "");

        String p = r.pick("二级").trim();
        if ("*".equals(p)) {
            // no detail page: hand the URL to the player's sniffer, exactly like drpy does
            vod.addProperty("vod_play_from", "免费分享");
            vod.addProperty("vod_remarks", detailId);
            vod.addProperty("vod_actor", "没有二级,只有一级链接直接嗅探播放");
            vod.addProperty("vod_content", url);
            vod.addProperty("vod_play_url", "嗅探播放$" + url);
        } else if (p.startsWith("js:")) {
            DrpyJs.Outcome o = js.run(p.substring(3).trim(), vars("input", url, "MY_URL", url,
                    "detailUrl", r.detailUrl(), "HOST", r.host()));
            JsonObject fromHook = parseObject(o.vodJson);
            if (fromHook != null) vod = mergeVod(vod, fromHook);
        } else if (p.startsWith("{")) {
            fillFromObject(vod, r, url);
        }

        JsonArray list = new JsonArray();
        list.add(vod);
        JsonObject out = new JsonObject();
        out.add("list", list);
        return out.toString();
    }

    private String buildDetailUrl(DrpyRule r, String detailId, String fyclass) {
        String path;
        if (!detailId.startsWith("http") && !detailId.contains("/")) {
            path = r.detailUrl().replace("fyid", detailId).replace("fyclass", fyclass);
        } else {
            path = detailId;
        }
        return r.absolute(path);
    }

    /**
     * Object-shaped {@code 二级}. Only the pieces T3 rules actually use are covered: title, desc,
     * content and the tab/list episode pair. Richer keys (tab_text/list_text/tab_sub/...) fall back
     * to Text/href, which is what the common case means anyway.
     */
    private void fillFromObject(JsonObject vod, DrpyRule r, String url) {
        try {
            JsonObject p = r.raw().getAsJsonObject("二级");
            String html = js.fetch(url, "GET", null, null);
            if (html.isEmpty()) return;
            org.jsoup.nodes.Document doc = DrpyDom.parse(html);
            if (p.has("title")) {
                String[] parts = p.get("title").getAsString().split(";");
                vod.addProperty("vod_name", DrpyDom.pdfh(doc, parts[0]).replaceAll("[\\n\\t]", "").trim());
                if (parts.length > 1) {
                    vod.addProperty("type_name", DrpyDom.pdfh(doc, parts[1]).replaceAll("[\\n\\t]", "").trim());
                }
            }
            if (p.has("desc")) {
                String[] parts = p.get("desc").getAsString().split(";");
                String[] keys = {"vod_remarks", "vod_year", "vod_area", "vod_actor", "vod_director"};
                for (int i = 0; i < keys.length && i < parts.length; i++) {
                    vod.addProperty(keys[i], DrpyDom.pdfh(doc, parts[i]).replaceAll("[\\n\\t]", "").trim());
                }
            }
            if (p.has("content")) {
                vod.addProperty("vod_content", DrpyDom.pdfh(doc, p.get("content").getAsString()));
            }
            fillTabs(vod, p, doc, url);
        } catch (Throwable t) {
            log("二级 object parse failed: " + t);
        }
    }

    private void fillTabs(JsonObject vod, JsonObject p, org.jsoup.nodes.Document doc, String url) {
        if (!p.has("lists")) return;
        String tabText = p.has("tab_text") ? p.get("tab_text").getAsString() : ".text&&Text";
        String listText = p.has("list_text") ? p.get("list_text").getAsString() : ".text&&Text";
        String listUrl = p.has("list_url") ? p.get("list_url").getAsString() : "a&&href";

        List<Element> episodes = DrpyDom.select(doc, p.get("lists").getAsString());
        List<String> lines = new ArrayList<>();
        for (Element ep : episodes) {
            String label = clean(DrpyDom.pdfh(ep, listText));
            String link = DrpyDom.pd(ep, listUrl, url);
            if (link.isEmpty()) continue;
            lines.add((label.isEmpty() ? String.valueOf(lines.size() + 1) : label) + "$" + link);
        }
        if (lines.isEmpty()) return;
        String line = String.join("#", lines);

        List<Element> tabs = p.has("tabs") ? DrpyDom.select(doc, p.get("tabs").getAsString()) : List.of();
        if (tabs.isEmpty()) {
            vod.addProperty("vod_play_from", "线路1");
            vod.addProperty("vod_play_url", line);
            return;
        }
        // one flat episode list per detail page: every tab label points at the same episodes,
        // which is how these rules behave when the site has no per-line containers
        List<String> from = new ArrayList<>();
        List<String> play = new ArrayList<>();
        for (Element tab : tabs) {
            String name = clean(DrpyDom.pdfh(tab, tabText));
            from.add(name.isEmpty() ? "线路" + (from.size() + 1) : name);
            play.add(line);
        }
        vod.addProperty("vod_play_from", String.join("$$$", from));
        vod.addProperty("vod_play_url", String.join("$$$", play));
    }

    // ------------------------------------------------------------------ search

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return search(key, 1);
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return search(key, number(pg, 1));
    }

    private String search(String wd, int page) throws Exception {
        DrpyRule r = ready();
        String searchUrl = r.searchUrl();
        if (searchUrl.isEmpty()) return "";
        String p = r.pick("搜索").trim();
        if ("*".equals(p)) p = r.pick("一级").trim();
        if (p.isEmpty()) return "";

        String url = searchUrl.replace("**", wd);
        if (url.contains("[") && url.contains("]") && !url.contains("#")) {
            String inside = url.substring(url.indexOf('[') + 1, url.indexOf(']'));
            url = page == 1 ? inside : url.substring(0, url.indexOf('['));
        }
        String method = "GET";
        String body = null;
        if (url.contains(";")) {
            String[] parts = url.split(";");
            url = parts[0];
            method = parts[1].trim().toUpperCase();
        }
        if ("POST".equals(method) && url.contains("#")) {
            // "/api/search?...#did=...&sk=**;post" -> params after the hash, POSTed as a form
            String[] halves = url.split("#", 2);
            url = halves[0];
            body = halves[1];
        }
        if (url.contains("fypage")) url = url.replace("fypage", String.valueOf(page));
        String abs = r.absolute(url);

        JsonArray list = parseList(p, abs, "", false, method, body);
        return wrap(list, page).toString();
    }

    // ------------------------------------------------------------------ play

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        DrpyRule r = ready();
        String url = id == null ? "" : id;
        if (!url.contains("http")) {
            try {
                url = Util.base64Decode(url);
            } catch (Throwable ignored) {
            }
        }
        try {
            url = java.net.URLDecoder.decode(url, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }

        JsonObject play = playBase(url, flag);
        String lazy = r.lazy().trim();
        if (r.playParse() && !lazy.isEmpty()) {
            String code = lazy.startsWith("js:") ? lazy.substring(3).trim() : lazy;
            try {
                DrpyJs.Outcome o = js.run(code, vars("input", url, "MY_URL", url,
                        "flag", flag, "MY_FLAG", flag, "HOST", r.host()));
                if (o.hasInputObject()) {
                    JsonObject fromHook = parseObject(o.inputJson);
                    if (fromHook != null) play = fromHook;
                } else if (o.inputText != null && !o.inputText.isEmpty()) {
                    play = playBase(o.inputText, flag);
                }
            } catch (Throwable t) {
                log("lazy hook failed, falling back to sniffer: " + t);
            }
        }

        // drpy forces these when the rule has no play_json table
        if (!r.has("play_json")) {
            play.addProperty("jx", 0);
            play.addProperty("parse", 1);
        }

        // A hook that resolved a real .flv/.m3u8 needs no parser; leaving parse:1 would route a
        // direct live stream through the sniffer and break it. This is the one place we deviate.
        String finalUrl = play.has("url") && play.get("url").isJsonPrimitive() ? play.get("url").getAsString() : "";
        if (isDirectMedia(finalUrl)) play.addProperty("parse", 0);

        return play.toString();
    }

    private JsonObject playBase(String url, String flag) {
        JsonObject o = new JsonObject();
        o.addProperty("parse", SPECIAL.matcher(url).find() ? 0 : 1);
        o.addProperty("url", url);
        o.addProperty("flag", flag == null ? "" : flag);
        o.addProperty("jx", 0);
        return o;
    }

    private static boolean isDirectMedia(String url) {
        if (url == null || url.isEmpty()) return false;
        String path = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        return MEDIA.matcher(path).find();
    }

    // ------------------------------------------------------------------ list parsing

    /** One list entry, either a DOM node or a JSON node -- field rules apply the same way. */
    private interface Node {
        String text(String rule);

        String url(String rule, String base);
    }

    private static Node domNode(Element el) {
        return new Node() {
            @Override
            public String text(String rule) {
                return DrpyDom.pdfh(el, rule);
            }

            @Override
            public String url(String rule, String base) {
                return DrpyDom.pd(el, rule, base);
            }
        };
    }

    private static Node jsonNode(JsonElement el) {
        return new Node() {
            @Override
            public String text(String rule) {
                return DrpyJson.pdfh(el, rule);
            }

            @Override
            public String url(String rule, String base) {
                return DrpyDom.resolve(base, DrpyJson.pdfh(el, rule));
            }
        };
    }

    private JsonArray parseList(String expr, String pageUrl, String tid, boolean doubleMode,
                                String method, String postBody) {
        JsonArray out = new JsonArray();
        String p = expr == null ? "" : expr.trim();
        if (p.isEmpty()) return out;

        if (p.startsWith("js:")) {
            boolean sniff = "*".equals(rule.pick("二级").trim());
            try {
                DrpyJs.Outcome o = js.run(p.substring(3).trim(),
                        vars("input", pageUrl, "MY_URL", pageUrl, "HOST", rule.host()));
                JsonElement vods = o.vodsJson == null ? null : JsonParser.parseString(o.vodsJson);
                if (vods != null && vods.isJsonArray()) {
                    for (JsonElement e : vods.getAsJsonArray()) {
                        if (!e.isJsonObject()) continue;
                        out.add(sniff ? withExtras(e.getAsJsonObject()) : e);
                    }
                }
            } catch (Throwable t) {
                log("list hook failed: " + t);
            }
            return out;
        }

        String[] parts = p.split(";");
        if (parts.length < (doubleMode ? 6 : 5)) {
            log("list rule needs " + (doubleMode ? 6 : 5) + " parts, got " + parts.length);
            return out;
        }
        String first = parts[0].trim();
        boolean isJson = first.startsWith("json:");
        String selector = first.replaceFirst("^(jsp:|json:|jq:)", "");
        String inner = doubleMode ? parts[1].trim() : null;
        int offset = doubleMode ? 2 : 1;

        String body = js.fetch(pageUrl, method, postBody, null);
        if (body.isEmpty()) return out;

        List<Node> items = new ArrayList<>();
        if (isJson) {
            JsonElement root = DrpyJson.deal(body);
            if (root == null) return out;
            for (JsonElement group : DrpyJson.pdfa(root, selector)) {
                if (inner == null) {
                    items.add(jsonNode(group));
                } else {
                    for (JsonElement child : DrpyJson.pdfa(group, inner)) items.add(jsonNode(child));
                }
            }
        } else {
            org.jsoup.nodes.Document doc = DrpyDom.parse(body);
            for (Element group : DrpyDom.select(doc, selector)) {
                if (inner == null) {
                    items.add(domNode(group));
                } else {
                    for (Element child : DrpyDom.select(group, inner)) items.add(domNode(child));
                }
            }
        }

        boolean sniffPlay = "*".equals(rule.pick("二级").trim());
        for (Node it : items) {
            out.add(toVod(it, parts, offset, tid, pageUrl, sniffPlay));
        }
        return out;
    }

    /**
     * Hooks fill vod_id themselves and drpy's reference does not add the {@code @@name@@pic}
     * suffix for them, which leaves the detail page without a title. Add it here so both paths
     * produce self-describing ids; detail strips the suffix before using the id.
     */
    private static JsonObject withExtras(JsonObject vod) {
        String id = vod.has("vod_id") && vod.get("vod_id").isJsonPrimitive() ? vod.get("vod_id").getAsString() : "";
        if (id.contains("@@")) return vod;
        String name = vod.has("vod_name") && vod.get("vod_name").isJsonPrimitive() ? vod.get("vod_name").getAsString() : "";
        String pic = vod.has("vod_pic") && vod.get("vod_pic").isJsonPrimitive() ? vod.get("vod_pic").getAsString() : "";
        JsonObject copy = vod.deepCopy();
        copy.addProperty("vod_id", id + "@@" + name + "@@" + pic);
        return copy;
    }

    private JsonObject toVod(Node it, String[] parts, int offset, String tid, String pageUrl, boolean sniffPlay) {
        String name = clean(it.text(parts[offset]));
        String pic = it.url(parts[offset + 1], pageUrl);
        String remarks = clean(it.text(parts[offset + 2]));

        StringBuilder link = new StringBuilder();
        for (String part : parts[offset + 3].split("\\+")) {
            String value = rule.detailUrl().isEmpty() ? it.url(part, pageUrl) : it.text(part);
            if (link.length() > 0) link.append('$');
            link.append(value == null ? "" : value.trim());
        }
        // drpy prefixes the category only for 一级; search results stay unprefixed
        String vodId = rule.detailUrl().isEmpty() || tid.isEmpty() ? link.toString() : tid + "$" + link;
        if (sniffPlay) vodId = vodId + "@@" + name + "@@" + pic;

        JsonObject vod = new JsonObject();
        vod.addProperty("vod_id", vodId);
        vod.addProperty("vod_name", name);
        vod.addProperty("vod_pic", pic);
        vod.addProperty("vod_remarks", remarks);
        return vod;
    }

    private JsonObject wrap(JsonArray list, int page) {
        JsonObject out = new JsonObject();
        out.addProperty("page", page);
        out.addProperty("pagecount", 999);
        out.addProperty("limit", rule == null ? 20 : rule.limit());
        out.addProperty("total", list.size());
        out.add("list", list);
        return out;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("[\\n\\t]", "").trim();
    }

    // ------------------------------------------------------------------ misc

    private Map<String, Object> vars(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1] == null ? "" : kv[i + 1]);
        }
        return map;
    }

    private static JsonObject parseObject(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JsonElement el = JsonParser.parseString(json);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static JsonObject mergeVod(JsonObject base, JsonObject from) {
        for (Map.Entry<String, JsonElement> e : from.entrySet()) base.add(e.getKey(), e.getValue());
        if (!base.has("vod_id")) base.addProperty("vod_id", "");
        return base;
    }

    private static int number(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static String host(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            return uri.getHost() == null ? url : uri.getHost();
        } catch (Throwable t) {
            return url;
        }
    }

    private void log(String message) {
        System.err.println("[drpy:" + siteKey + "] " + message);
    }
}

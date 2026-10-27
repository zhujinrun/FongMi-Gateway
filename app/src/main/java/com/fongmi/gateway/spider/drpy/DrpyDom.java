package com.fongmi.gateway.spider.drpy;

import com.github.catvod.utils.UriUtil;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The cheerio-flavoured selector dialect that drpy rules are written in.
 *
 * <p>Rules look like {@code ".pic-list.list-box;.items;.text&&Text;mip-img&&src;a&&href"}, where
 * {@code ;} separates the five field rules and {@code &&} separates a CSS selector from the thing
 * to read out of the match ({@code Text}, {@code Html} or an attribute name).
 *
 * <p>Selectors may themselves be chained with {@code &&} -- {@code body&&#videoWrap&&video-src}
 * means "find body, then #videoWrap inside it, then a video-src tag inside that". A segment that
 * comes back empty aborts the chain, which is how drpy ends up with "" instead of a half-match.
 *
 * <p>Implemented on jsoup rather than by shipping cheerio: the three drpy sources here only use
 * CSS selectors plus these two operators, and jsoup already understands the {@code :eq()},
 * {@code :contains()} and {@code :gt()} pseudo-classes that older T3 rules rely on.
 */
public final class DrpyDom {

    private DrpyDom() {
    }

    public static Document parse(String html) {
        if (html == null || html.isEmpty()) return Jsoup.parse("");
        // jsoup guesses the charset from <meta> only when parsing bytes; we already decoded the
        // body, so a parse(String) is enough and keeps non-utf8 pages from being re-decoded.
        return Jsoup.parse(html);
    }

    /** All matches of a (possibly &&-chained) selector, in document order, de-duplicated. */
    public static List<Element> select(Element root, String selector) {
        List<Element> current = new ArrayList<>();
        if (root == null) return current;
        current.add(root);
        if (selector == null || selector.trim().isEmpty()) return current;
        for (String raw : selector.split("&&")) {
            String seg = raw.trim();
            if (seg.isEmpty()) continue;
            Set<Element> next = new LinkedHashSet<>();
            for (Element parent : current) {
                try {
                    next.addAll(parent.select(seg));
                } catch (Throwable ignored) {
                    // jsoup throws on selectors it cannot parse (:has-text etc. from T3 rules);
                    // a broken selector should read as "no match", not kill the whole request
                }
            }
            current = new ArrayList<>(next);
            if (current.isEmpty()) return current;
        }
        return current;
    }

    public static List<Element> pdfa(String html, String selector) {
        return select(parse(html), selector);
    }

    /** First match of {@code selector}; {@code ;} separates fallbacks, first non-empty wins. */
    public static String pdfh(String html, String rule) {
        return pdfh(parse(html), rule);
    }

    public static String pdfh(Element root, String rule) {
        if (root == null || rule == null) return "";
        for (String alt : rule.split(";")) {
            String one = alt.trim();
            if (one.isEmpty()) continue;
            String value = pdfhOne(root, one);
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }

    private static String pdfhOne(Element root, String spec) {
        int split = spec.lastIndexOf("&&");
        String selector = split < 0 ? spec : spec.substring(0, split).trim();
        String attr = split < 0 ? "Text" : spec.substring(split + 2).trim();
        List<Element> nodes = selector.isEmpty() ? List.of(root) : select(root, selector);
        if (nodes.isEmpty()) return "";
        Element node = nodes.get(0);
        if (attr.isEmpty() || "Text".equals(attr)) return text(node);
        if ("Html".equalsIgnoreCase(attr)) return node.html();
        String value = node.attr(attr);
        if (value.isEmpty() && attr.toLowerCase().contains("style")) {
            // rules written for background-image: "div&&style" expects the url(...) part only
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("url\\((.*?)\\)").matcher(node.attr("style"));
            if (m.find()) value = m.group(1).replaceAll("^['\"]|['\"]$", "");
        }
        return value;
    }

    /** pdfh + URL resolution against the page that the rule was applied to. */
    public static String pd(Element root, String rule, String base) {
        String value = pdfh(root, rule);
        return resolve(base, value);
    }

    public static String resolve(String base, String value) {
        if (value == null || value.isEmpty()) return "";
        String v = value.trim();
        if (v.startsWith("http://") || v.startsWith("https://")) return v;
        if (v.startsWith("data:") || v.startsWith("mailto:") || v.startsWith("javascript:")) return v;
        if (v.startsWith("//")) return "https:" + v;
        if (base == null || base.isEmpty()) return v;
        try {
            return UriUtil.resolve(base, v);
        } catch (Throwable t) {
            return v;
        }
    }

    /**
     * cheerio's text() concatenates without adding whitespace; jsoup's normalises. Some rules
     * strip whitespace themselves afterwards, and those that do not want the tidier output, so
     * keep jsoup's behaviour but drop the leading/trailing blank.
     */
    public static String text(Element node) {
        return node == null ? "" : node.text().trim();
    }
}

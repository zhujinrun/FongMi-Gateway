package com.fongmi.gateway.log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Drops known-harmless Android-jar noise from System.out/err so users do not
 * mistake it for gateway instability:
 * - "Cannot run program chmod/getprop/killall" (Linux commands on Windows)
 * - "CreateProcess error=2" stacks
 * - libwexproxy.so / ARM native library load failures
 * - dalvik DexClassLoader stacks (Android dex on desktop)
 * - spider init fetch/parse noise ("Failed to get response body", 捕获到异常)
 *
 * Exception blocks are buffered until the block ends, then dropped as a whole
 * when a noise marker appears anywhere inside; clean blocks pass through.
 * Concise single-line summaries ([jar] ... fail / [gateway] ...) are never
 * dropped unless they themselves are a noise line.
 */
public final class LogFilterStream extends OutputStream {

    private static final String[] BLOCK_NOISE = {
            "Cannot run program \"",
            "CreateProcess error=2",
            "libwexproxy.so",
            "machine code=",
            "DexClassLoader",
            "Failed to get response body",
    };

    private static final String[] LINE_NOISE = {
            "Cannot run program \"",
            "CreateProcess error=2",
            "libwexproxy.so",
            "machine code=",
            "\u6355\u83b7\u5230\u5f02\u5e38", // 捕获到异常
    };

    private static LogFilterStream outStream;
    private static LogFilterStream errStream;

    private final OutputStream target;
    private final ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
    private final ByteArrayOutputStream blockBuf = new ByteArrayOutputStream();
    private boolean inBlock;
    private boolean dirty;

    private LogFilterStream(OutputStream target) {
        this.target = target;
    }

    public static synchronized void install() {
        if (outStream != null) return;
        outStream = new LogFilterStream(System.out);
        errStream = new LogFilterStream(System.err);
        System.setOut(new PrintStream(outStream, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(errStream, true, StandardCharsets.UTF_8));
        Runtime.getRuntime().addShutdownHook(new Thread(LogFilterStream::flushAll));
    }

    private static void flushAll() {
        flush(outStream);
        flush(errStream);
    }

    private static void flush(LogFilterStream s) {
        if (s == null) return;
        synchronized (s) {
            s.flushPending();
        }
    }

    @Override
    public synchronized void write(int b) {
        if (b == '\n') endLine();
        else lineBuf.write(b);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
        for (int i = 0; i < len; i++) write(b[off + i]);
    }

    @Override
    public synchronized void flush() {
        try {
            target.flush();
        } catch (IOException ignored) {
        }
    }

    private void endLine() {
        byte[] raw = lineBuf.toByteArray();
        lineBuf.reset();
        String text = new String(raw, StandardCharsets.UTF_8);
        if (text.endsWith("\r")) text = text.substring(0, text.length() - 1);
        handleLine(text, raw);
    }

    private void handleLine(String text, byte[] raw) {
        if (!inBlock) {
            if (isHeader(text)) {
                inBlock = true;
                dirty = hasBlockNoise(text);
                appendBlock(raw);
            } else if (hasLineNoise(text)) {
                inBlock = true;
                dirty = true;
                appendBlock(raw);
            } else {
                emit(raw);
            }
            return;
        }
        if (isContinuation(text)) {
            if (hasBlockNoise(text)) dirty = true;
            appendBlock(raw);
            return;
        }
        flushBlock();
        inBlock = false;
        handleLine(text, raw);
    }

    private void appendBlock(byte[] raw) {
        blockBuf.write(raw, 0, raw.length);
        blockBuf.write('\n');
    }

    private void flushBlock() {
        if (!dirty && blockBuf.size() > 0) emitBuf(blockBuf);
        blockBuf.reset();
        dirty = false;
    }

    private void flushPending() {
        flushBlock();
        try {
            target.flush();
        } catch (IOException ignored) {
        }
    }

    private void emit(byte[] raw) {
        try {
            target.write(raw);
            target.write('\n');
        } catch (IOException ignored) {
        }
    }

    private void emitBuf(ByteArrayOutputStream buf) {
        try {
            buf.writeTo(target);
            target.write('\n');
        } catch (IOException ignored) {
        }
    }

    private static boolean hasBlockNoise(String t) {
        return matches(t, BLOCK_NOISE);
    }

    private static boolean hasLineNoise(String t) {
        return matches(t, LINE_NOISE);
    }

    private static boolean matches(String t, String[] markers) {
        for (String n : markers) {
            if (t.contains(n)) return true;
        }
        return false;
    }

    private static boolean isHeader(String t) {
        if (t.isEmpty()) return false;
        if (t.startsWith("Exception in thread") || t.startsWith("Caused by:")) return true;
        int end = 0;
        while (end < t.length()) {
            char c = t.charAt(end);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '$' || c == '_') end++;
            else break;
        }
        if (end == 0) return false;
        String head = t.substring(0, end);
        return head.endsWith("Exception") || head.endsWith("Error");
    }

    private static boolean isContinuation(String t) {
        if (t.isEmpty()) return false;
        if (t.charAt(0) == '\t') return true;
        if (t.startsWith("Caused by:")) return true;
        return t.matches("\\.\\.\\. \\d+ more");
    }
}

package com.fongmi.gateway.loader;

import com.googlecode.d2j.dex.Dex2jar;
import com.googlecode.d2j.reader.BaseDexFileReader;
import com.googlecode.d2j.reader.MultiDexFileReader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class Dex2Jar {

    private Dex2Jar() {
    }

    public static void convert(File dexJar, File outJar) throws Exception {
        Exception first = null;
        try {
            convertViaLibrary(dexJar, outJar);
            if (outJar.exists() && outJar.length() > 0) return;
        } catch (Exception e) {
            first = e;
            System.err.println("[d2j] library failed: " + e);
            e.printStackTrace();
        }
        try {
            convertViaCli(dexJar, outJar);
            if (outJar.exists() && outJar.length() > 0) return;
        } catch (Exception e) {
            if (first == null) first = e;
            System.err.println("[d2j] cli failed: " + e);
        }
        throw first != null ? first : new Exception("dex2jar conversion failed");
    }

    private static void convertViaLibrary(File dexJar, File outJar) throws Exception {
        byte[] jarBytes = Files.readAllBytes(dexJar.toPath());
        BaseDexFileReader reader = MultiDexFileReader.open(jarBytes);
        Path outPath = outJar.toPath();
        Dex2jar.from(reader).skipDebug().to(outPath);
        if (!Files.exists(outPath) || Files.size(outPath) == 0) {
            throw new Exception("dex2jar produced empty output");
        }
        copyAssets(dexJar, outJar);
        System.out.println("[d2j] converted " + dexJar.getName() + " -> " + outJar.getName() + " size=" + outJar.length());
    }

    private static void copyAssets(File srcJar, File outJar) throws Exception {
        File tmp = new File(outJar.getParentFile(), outJar.getName() + ".tmp");
        try (var converted = new java.util.zip.ZipFile(outJar);
             var src = new java.util.zip.ZipFile(srcJar);
             var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(tmp.toPath()))) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            var cen = converted.entries();
            while (cen.hasMoreElements()) {
                var e = cen.nextElement();
                seen.add(e.getName());
                zos.putNextEntry(new java.util.zip.ZipEntry(e.getName()));
                if (!e.isDirectory()) converted.getInputStream(e).transferTo(zos);
                zos.closeEntry();
            }
            var sen = src.entries();
            while (sen.hasMoreElements()) {
                var e = sen.nextElement();
                String n = e.getName();
                if (seen.contains(n) || e.isDirectory()) continue;
                if (n.endsWith(".class") || n.startsWith("META-INF/")) continue;
                if (n.equals("classes.dex") || (n.startsWith("classes") && n.endsWith(".dex") && !n.contains("/"))) continue;
                zos.putNextEntry(new java.util.zip.ZipEntry(n));
                src.getInputStream(e).transferTo(zos);
                zos.closeEntry();
            }
        }
        Files.move(tmp.toPath(), outJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void convertViaCli(File dexJar, File outJar) throws Exception {
        String[] candidates = {"d2j-dex2jar.bat", "d2j-dex2jar", "d2j-dex2jar.sh"};
        for (String name : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(name, "-f", "-o", outJar.getAbsolutePath(), dexJar.getAbsolutePath());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                int code = p.waitFor();
                if (code == 0 && outJar.exists()) return;
                if (!out.isBlank()) System.err.println("[d2j-cli] " + out);
            } catch (Exception ignored) {
            }
        }
        File tools = new File("tools/dex-tools/d2j-dex2jar.bat");
        if (tools.exists()) {
            ProcessBuilder pb = new ProcessBuilder(tools.getAbsolutePath(), "-f", "-o", outJar.getAbsolutePath(), dexJar.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor();
        }
    }

    public static boolean canConvert() {
        try {
            Class.forName("com.googlecode.d2j.dex.Dex2jar");
            Class.forName("com.googlecode.d2j.reader.MultiDexFileReader");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

package com.github.catvod.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Path {

    private static File root;

    public static void setRoot(File dir) {
        root = dir;
        if (root != null && !root.exists()) root.mkdirs();
    }

    public static File getRoot() {
        if (root == null) {
            root = new File(System.getProperty("user.home"), ".gateway");
        }
        if (!root.exists()) root.mkdirs();
        return root;
    }

    private static File mkdir(File file) {
        if (file != null && !file.exists()) file.mkdirs();
        return file;
    }

    public static boolean exists(String path) {
        return path != null && new File(path.replace("file://", "")).exists();
    }

    public static boolean exists(File file) {
        return file != null && file.exists() && file.length() > 0;
    }

    public static File root() {
        return getRoot();
    }

    public static File cache() {
        return mkdir(new File(getRoot(), "cache"));
    }

    public static File files() {
        return mkdir(new File(getRoot(), "files"));
    }

    public static String rootPath() {
        return root().getAbsolutePath();
    }

    public static File tv() {
        return mkdir(new File(root(), "TV"));
    }

    public static File jar() {
        return mkdir(new File(cache(), "jar"));
    }

    public static File jar(String name) {
        return new File(jar(), safeName(name));
    }

    public static File js() {
        return mkdir(new File(cache(), "js"));
    }

    public static File py() {
        return mkdir(new File(cache(), "py"));
    }

    public static File so() {
        return mkdir(new File(files(), "so"));
    }

    public static File player() {
        return mkdir(new File(files(), "player"));
    }

    public static File local(String url) {
        if (url == null) return null;
        String path = url.replace("file://", "");
        return new File(path);
    }

    public static File files(String name) {
        return new File(files(), name);
    }

    public static File cache(String name) {
        return new File(cache(), name);
    }

    public static String read(File file) {
        if (!exists(file)) return "";
        try (InputStream in = new FileInputStream(file)) {
            byte[] data = in.readAllBytes();
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    public static String read(String path) {
        return read(new File(path));
    }

    public static void write(File file, String content) {
        write(file, content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8));
    }

    public static void write(File file, byte[] data) {
        if (file == null) return;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void write(String path, String content) {
        write(new File(path), content);
    }

    public static byte[] readBytes(File file) {
        if (!exists(file)) return new byte[0];
        try (InputStream in = new FileInputStream(file)) {
            return in.readAllBytes();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    public static List<File> list(File dir) {
        if (dir == null || !dir.isDirectory()) return new ArrayList<>();
        File[] files = dir.listFiles();
        return files == null ? new ArrayList<>() : Arrays.asList(files);
    }

    public static boolean delete(File file) {
        if (file == null || !file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File c : children) delete(c);
        }
        return file.delete();
    }

    private static String safeName(String name) {
        if (name == null) return "unnamed";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}

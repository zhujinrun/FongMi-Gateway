import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;

/** Force-link every class in a jar and report JVM verify failures. */
public class VerifyAll {
    public static void main(String[] args) throws Exception {
        File jar = new File(args[0]);
        List<String> names = new ArrayList<>();
        try (ZipFile zf = new ZipFile(jar)) {
            var en = zf.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (n.endsWith(".class") && !n.startsWith("META-INF/"))
                    names.add(n.substring(0, n.length() - 6).replace('/', '.'));
            }
        }
        URLClassLoader ldr = new URLClassLoader(new URL[]{jar.toURI().toURL()},
                VerifyAll.class.getClassLoader());
        int ok = 0, verify = 0, missing = 0, other = 0;
        Map<String, Integer> kinds = new LinkedHashMap<>();
        for (String n : names) {
            try {
                Class<?> c = Class.forName(n, false, ldr);
                c.getDeclaredMethods();
                c.getDeclaredFields();
                ok++;
            } catch (VerifyError e) {
                verify++;
                System.out.println("VERIFY " + n + " :: " + e.getMessage());
            } catch (NoClassDefFoundError | ClassNotFoundException e) {
                missing++;
                String m = String.valueOf(e.getMessage());
                kinds.merge(m.substring(0, Math.min(60, m.length())), 1, Integer::sum);
            } catch (Throwable e) {
                other++;
                String k = e.getClass().getSimpleName() + ": " + e.getMessage();
                kinds.merge(k.substring(0, Math.min(80, k.length())), 1, Integer::sum);
            }
        }
        System.out.println("total=" + names.size() + " ok=" + ok + " verifyError=" + verify
                + " missing=" + missing + " other=" + other);
        kinds.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(12)
                .forEach(e -> System.out.println("  x" + e.getValue() + " " + e.getKey()));
    }
}

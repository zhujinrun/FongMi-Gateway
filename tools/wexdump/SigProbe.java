import java.io.File;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Verification step for {@code gsonparams.py}: load the patched classes and print
 * what {@code getGenericSuperclass()} actually returns.
 *
 * <p>Checking the bytes is not enough. Both mistakes this pipeline made the first
 * time round only show up here:
 * <ul>
 *   <li>writing the signature string into the attribute body instead of a constant
 *       pool index gives {@code ClassFormatError: Wrong Signature attribute length};</li>
 *   <li>patching only the subclasses and not {@code f1.a} itself gives
 *       {@code MalformedParameterizedTypeException: 0 formal argument(s) 1 actual}.</li>
 * </ul>
 *
 * <pre>
 * java SigProbe.java &lt;patched.jar&gt; com.github.catvod.spider.merge.k.f ...
 * </pre>
 *
 * Expected: a {@code TypeToken<List<...>>} ParameterizedType, not a raw class.
 */
public class SigProbe {

    public static void main(String[] a) throws Exception {
        URLClassLoader cl = new URLClassLoader(new URL[]{new File(a[0]).toURI().toURL()},
                SigProbe.class.getClassLoader());
        for (int i = 1; i < a.length; i++) {
            Class<?> c = cl.loadClass(a[i]);
            Type t = c.getGenericSuperclass();
            System.out.printf("%-24s %s%n", c.getSimpleName(), t);
            if (t instanceof ParameterizedType p) {
                System.out.println("    raw=" + p.getRawType() + " arg=" + p.getActualTypeArguments()[0]);
            }
        }
    }
}

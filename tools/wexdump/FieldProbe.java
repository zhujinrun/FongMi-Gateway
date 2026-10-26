import java.net.*;
import java.lang.reflect.*;
import java.lang.annotation.Annotation;

/**
 * Verifies that the injected @SerializedName annotations actually take effect:
 * build a bean through its own constructor and serialize it with the Gson copy
 * that ships inside the payload.
 *
 * A byte-level check is not enough here -- the annotation can be well formed and
 * still be ignored if the descriptor does not resolve to the class that
 * ReflectiveTypeAdapterFactory asks for.
 *
 * Usage: java FieldProbe.java <jar> <annotation class> <gson class> <bean> [args...]
 */
public class FieldProbe {
    @SuppressWarnings("unchecked")
    public static void main(String[] a) throws Exception {
        URLClassLoader cl = new URLClassLoader(new URL[]{new java.io.File(a[0]).toURI().toURL()},
                FieldProbe.class.getClassLoader());
        Class<? extends Annotation> annType =
                (Class<? extends Annotation>) cl.loadClass(a[1]);
        Class<?> gsonClass = cl.loadClass(a[2]);
        Method gsonToJson = null;
        // R8 renamed toJson(Object) too; it is the only (Object) -> String on Gson.
        for (Method m : gsonClass.getMethods())
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == Object.class
                    && m.getReturnType() == String.class) gsonToJson = m;
        if (gsonToJson == null) throw new IllegalStateException("no toJson(Object) on " + a[2]);
        Object gson = gsonClass.getConstructor().newInstance();
        Method value = annType.getMethod("value");

        for (int i = 3; i < a.length; i++) {
            String spec = a[i];
            int eq = spec.indexOf('=');
            Class<?> bean = cl.loadClass(eq < 0 ? spec : spec.substring(0, eq));
            String args = eq < 0 ? "" : spec.substring(eq + 1);

            System.out.println("== " + bean.getName());
            for (Field f : bean.getDeclaredFields()) {
                Annotation[] all = f.getDeclaredAnnotations();
                Annotation ann = f.getAnnotation(annType);
                if (ann != null) System.out.printf("   %-3s -> %-14s%n", f.getName(), value.invoke(ann));
                else if (all.length > 0)
                    System.out.printf("   %-3s -> MISMATCH: %d annotation(s) but not %s: %s%n",
                            f.getName(), all.length, annType.getName(), java.util.Arrays.toString(all));
            }

            for (Constructor<?> c : bean.getConstructors()) {
                Class<?>[] pt = c.getParameterTypes();
                boolean allStrings = true;
                for (Class<?> p : pt) if (p != String.class) allStrings = false;
                if (!allStrings) continue;
                Object[] call = new Object[pt.length];
                String[] parts = args.isEmpty() ? new String[0] : args.split(",", -1);
                for (int k = 0; k < pt.length; k++)
                    call[k] = k < parts.length ? parts[k] : parts[0];
                Object inst = c.newInstance(call);
                System.out.println("   toJson: " + gsonToJson.invoke(gson, inst));
                break;
            }
        }
    }
}

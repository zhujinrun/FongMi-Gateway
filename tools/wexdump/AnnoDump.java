import java.net.*;
import java.lang.reflect.*;
import java.lang.annotation.*;
public class AnnoDump {
    public static void main(String[] a) throws Exception {
        URLClassLoader cl = new URLClassLoader(new URL[]{new java.io.File(a[0]).toURI().toURL()},
                AnnoDump.class.getClassLoader());
        Class<?> ann = cl.loadClass(a[1]);
        System.out.println("annotation class: " + ann);
        System.out.println("  isAnnotation=" + ann.isAnnotation() + " isInterface=" + ann.isInterface());
        for (Method m : ann.getDeclaredMethods()) System.out.println("  method " + m);
        System.out.println("  @Retention=" + ann.getAnnotation(Retention.class));
        System.out.println("  @Target=" + ann.getAnnotation(Target.class));
        Class<?> bean = cl.loadClass(a[2]);
        for (Field f : bean.getDeclaredFields()) {
            try {
                Annotation[] all = f.getDeclaredAnnotations();
                System.out.println("  field " + f.getName() + " -> " + all.length);
                for (Annotation an : all) System.out.println("      " + an.annotationType().getName() + " " + an);
            } catch (Throwable t) { System.out.println("  field " + f.getName() + " THREW " + t); }
        }
    }
}

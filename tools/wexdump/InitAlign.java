import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 用数据流分析修正「new T 却调用 Super.<init>」的调用点：
 *   - JVM 要求 <init> 的 owner 必须等于被 new 的类型；
 *   - ART 允许调用父类构造器（R8 会把匿名子类构造器上提到父类）。
 * 处理方式：把 owner 改回 T，并给 T 补一个委托父类的桥接构造器；
 * 最后用 COMPUTE_FRAMES 重算 StackMapTable。
 */
public class InitAlign {

    static class Uninit extends BasicValue {
        /** the NEW instruction that produced this value, so we can retarget it */
        final TypeInsnNode origin;

        Uninit(Type t, TypeInsnNode origin) {
            super(t);
            this.origin = origin;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Uninit && getType().equals(((Uninit) o).getType());
        }

        @Override
        public int hashCode() {
            return getType().hashCode() * 31 + 7;
        }
    }

    static class Interp extends BasicInterpreter {
        Interp() {
            super(Opcodes.ASM9);
        }

        @Override
        public BasicValue newValue(Type type) {
            if (type != null && type.getSort() == Type.OBJECT) return new BasicValue(type);
            return super.newValue(type);
        }

        @Override
        public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
            if (insn.getOpcode() == Opcodes.NEW) {
                TypeInsnNode tn = (TypeInsnNode) insn;
                return new Uninit(Type.getObjectType(tn.desc), tn);
            }
            return super.newOperation(insn);
        }

        @Override
        public BasicValue copyOperation(AbstractInsnNode insn, BasicValue value) {
            return value;
        }

        @Override
        public BasicValue merge(BasicValue a, BasicValue b) {
            if (a.equals(b)) return a;
            try {
                return super.merge(a, b);
            } catch (Throwable t) {
                return BasicValue.UNINITIALIZED_VALUE;
            }
        }
    }

    /** "T\u0000desc" -> original owner */
    static final Map<String, String> BRIDGES = new LinkedHashMap<>();
    static int rewritten = 0, analyzed = 0, failed = 0, rejected = 0, retargeted = 0;

    static final Map<String, String> SUPER = new HashMap<>();
    static final Map<String, List<String>> IFACE = new HashMap<>();


    static boolean hasCtor(ClassNode cn, String desc) {
        for (MethodNode mn : cn.methods) if ("<init>".equals(mn.name) && desc.equals(mn.desc)) return true;
        return false;
    }

    static void addCtor(ClassNode cn, String owner, String desc) {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", desc, null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        int slot = 1;
        for (Type t : Type.getArgumentTypes(desc)) {
            il.add(new VarInsnNode(t.getOpcode(Opcodes.ILOAD), slot));
            slot += t.getSize();
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, owner, "<init>", desc, false));
        il.add(new InsnNode(Opcodes.RETURN));
        mn.maxLocals = slot;
        cn.methods.add(mn);
    }

    /** 让 cls 拥有 <init>(desc)，沿继承链逐级委托，直到 owner。 */
    static void ensureBridge(Map<String, ClassNode> byName, Set<String> added, String cls, String owner, String desc) {
        if (cls == null || cls.equals(owner)) return;
        ClassNode cn = byName.get(cls);
        if (cn == null) return;
        String sup = cn.superName;
        if (sup == null || sup.equals("java/lang/Object") && !owner.equals("java/lang/Object")) {
            if (sup == null || !isSub(owner, sup)) return;
        }
        if (!added.contains(cls + desc) && hasCtor(cn, desc) && !cls.equals(owner)) return;
        if (sup != null && !sup.equals(owner)) {
            ensureBridge(byName, added, sup, owner, desc);
            ClassNode sn = byName.get(sup);
            if (sn != null && !hasCtor(sn, desc)) return;   // 上级没建成，放弃
        }
        if (hasCtor(cn, desc)) return;
        addCtor(cn, sup == null ? "java/lang/Object" : sup, desc);
        added.add(cls + desc);
    }

    static boolean isSub(String a, String b) {
        Deque<String> q = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        q.add(a);
        while (!q.isEmpty()) {
            String c = q.poll();
            if (c == null || !seen.add(c)) continue;
            if (c.equals(b)) return true;
            for (String i : IFACE.getOrDefault(c, Collections.emptyList())) q.add(i);
            if (SUPER.get(c) != null) q.add(SUPER.get(c));
        }
        return false;
    }

    static class CW extends ClassWriter {
        CW() {
            super(COMPUTE_FRAMES | COMPUTE_MAXS);
        }

        @Override
        protected String getCommonSuperClass(String a, String b) {
            if (a.equals(b)) return a;
            if (isSub(a, b)) return b;
            if (isSub(b, a)) return a;
            // 沿 a 的继承链找第一个同时也是 b 祖先的类（含接口），这才是最近的公共祖先。
            // 直接返回 Object 会让 COMPUTE_FRAMES 生成过宽的栈帧，调用点再校验就会炸。
            Set<String> seen = new HashSet<>();
            for (String c = a; c != null && seen.add(c); c = SUPER.get(c)) {
                if (isSub(b, c)) return c;
                for (String i : IFACE.getOrDefault(c, Collections.emptyList())) {
                    if (isSub(b, i)) return i;
                }
            }
            return "java/lang/Object";
        }
    }

    public static void main(String[] args) throws Exception {
        Path dir = Paths.get(args[0]);
        String outJar = args[1];

        List<Path> files = new ArrayList<>();
        Files.walk(dir).filter(p -> p.toString().endsWith(".class")).forEach(files::add);

        List<ClassNode> nodes = new ArrayList<>();
        for (Path p : files) {
            ClassReader cr = new ClassReader(Files.readAllBytes(p));
            ClassNode cn = new ClassNode();
            cr.accept(cn, ClassReader.SKIP_FRAMES);
            nodes.add(cn);
            SUPER.put(cn.name, cn.superName);
            if (cn.interfaces != null && !cn.interfaces.isEmpty()) IFACE.put(cn.name, cn.interfaces);
        }
        System.out.println("classes=" + nodes.size());

        for (ClassNode cn : nodes) {
            for (MethodNode mn : cn.methods) {
                if (mn.instructions.size() == 0) continue;
                try {
                    Frame<BasicValue>[] frames = new Analyzer<>(new Interp()).analyze(cn.name, mn);
                    analyzed++;
                    for (int i = 0; i < mn.instructions.size(); i++) {
                        AbstractInsnNode insn = mn.instructions.get(i);
                        if (insn.getOpcode() != Opcodes.INVOKESPECIAL) continue;
                        MethodInsnNode m = (MethodInsnNode) insn;
                        if (!"<init>".equals(m.name)) continue;
                        Frame<BasicValue> f = frames[i];
                        if (f == null) continue;
                        int nArgs = Type.getArgumentTypes(m.desc).length;
                        int idx = f.getStackSize() - 1 - nArgs;
                        if (idx < 0) continue;
                        BasicValue recv = f.getStack(idx);
                        if (recv instanceof Uninit) {
                            String want = ((Uninit) recv).getType().getInternalName();
                            if (want.equals(m.owner)) continue;
                            // R8 把子类构造器上提成了 Object.<init>：dex 里合法，JVM 里非法。
                            if (!isSub(want, m.owner) && !"java/lang/Object".equals(m.owner)) {
                                // 推断不出真实类型：退化成「new 与 <init> 保持一致」的保守策略，
                                // 至少能过 JVM 校验，不至于整类作废。
                                Uninit u = (Uninit) recv;
                                if (u.origin != null && !m.owner.equals(u.origin.desc)) {
                                    u.origin.desc = m.owner;
                                    retargeted++;
                                } else {
                                    rejected++;
                                }
                                if (rejected + retargeted <= 12) System.err.println("reject new-bridge " + want
                                        + " -> " + m.owner + " " + m.desc + " in " + cn.name + "." + mn.name);
                                continue;
                            }
                            BRIDGES.putIfAbsent(want + '\u0000' + m.desc, m.owner);
                            m.owner = want;
                            rewritten++;
                        } else if ("<init>".equals(mn.name) && recv != null && recv.getType() != null
                                && recv.getType().getSort() == Type.OBJECT
                                && cn.name.equals(recv.getType().getInternalName())
                                && !m.owner.equals(cn.name) && !m.owner.equals(cn.superName)) {
                            // 构造器里 aload_0 调到了祖父类构造器（R8 把父类的构造器也上提了）
                            if (!isSub(cn.superName, m.owner) && !"java/lang/Object".equals(m.owner)) {
                                rejected++;
                                if (rejected <= 10) System.err.println("reject super-bridge " + cn.superName
                                        + " -> " + m.owner + " " + m.desc + " in " + cn.name);
                                continue;
                            }
                            BRIDGES.putIfAbsent(cn.superName + '\u0000' + m.desc, m.owner);
                            m.owner = cn.superName;
                            rewritten++;
                        }
                    }
                } catch (Throwable t) {
                    failed++;
                    if (failed <= 3) {
                        System.err.println("FAIL " + cn.name + "." + mn.name + mn.desc + " : " + t);
                        t.printStackTrace();
                    }
                }
            }
        }
        System.out.println("analyzed=" + analyzed + " failed=" + failed
                + " rewritten=" + rewritten + " rejected=" + rejected
                + " retargeted=" + retargeted + " bridges=" + BRIDGES.size());

        // add bridge constructors（必要时逐级向上补链，保证每次只委托直接父类）
        Map<String, ClassNode> byName = new HashMap<>();
        for (ClassNode cn : nodes) byName.put(cn.name, cn);
        Set<String> added = new HashSet<>();
        for (Map.Entry<String, String> e : BRIDGES.entrySet()) {
            int k = e.getKey().indexOf('\u0000');
            ensureBridge(byName, added, e.getKey().substring(0, k), e.getValue(), e.getKey().substring(k + 1));
        }
        System.out.println("bridge ctors added=" + added.size());

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(Paths.get(outJar)))) {
            for (ClassNode cn : nodes) {
                ClassWriter cw = new CW();
                cn.accept(cw);
                zos.putNextEntry(new ZipEntry(cn.name + ".class"));
                zos.write(cw.toByteArray());
                zos.closeEntry();
            }
        }
        System.out.println("wrote " + outJar);
    }
}

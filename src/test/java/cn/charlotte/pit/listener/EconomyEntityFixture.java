package cn.charlotte.pit.listener;

import org.bukkit.entity.LivingEntity;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 1.8's patched API has return-type-only overloads that Java proxies cannot represent. */
final class EconomyEntityFixture {
    private static final Class<?> ENTITY = define();
    private EconomyEntityFixture() { }
    static LivingEntity entity(UUID id, double health) {
        try { return (LivingEntity) ENTITY.getConstructor(UUID.class, double.class).newInstance(id, health); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static Class<?> define() {
        String name = "cn/charlotte/pit/listener/EconomyEntityStub";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", new String[]{"org/bukkit/entity/LivingEntity"});
        writer.visitField(Opcodes.ACC_PRIVATE, "id", "Ljava/util/UUID;", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, "health", "D", null, null).visitEnd();
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/util/UUID;D)V", null, null);
        constructor.visitCode(); constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitFieldInsn(Opcodes.PUTFIELD, name, "id", "Ljava/util/UUID;");
        constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitVarInsn(Opcodes.DLOAD, 2);
        constructor.visitFieldInsn(Opcodes.PUTFIELD, name, "health", "D");
        constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(0, 0); constructor.visitEnd();
        Set<String> signatures = new HashSet<>();
        for (Method method : LivingEntity.class.getMethods()) {
            if (!Modifier.isAbstract(method.getModifiers())) continue;
            String descriptor = Type.getMethodDescriptor(method);
            if (!signatures.add(method.getName() + descriptor)) continue;
            MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), descriptor, null, null);
            mv.visitCode(); Class<?> result = method.getReturnType();
            if (method.getName().equals("getUniqueId")) {
                mv.visitVarInsn(Opcodes.ALOAD, 0); mv.visitFieldInsn(Opcodes.GETFIELD, name, "id", "Ljava/util/UUID;"); mv.visitInsn(Opcodes.ARETURN);
            } else if (method.getName().equals("getMaxHealth") || method.getName().equals("getHealth")) {
                mv.visitVarInsn(Opcodes.ALOAD, 0); mv.visitFieldInsn(Opcodes.GETFIELD, name, "health", "D");
                if (result == int.class) { mv.visitInsn(Opcodes.D2I); mv.visitInsn(Opcodes.IRETURN); }
                else mv.visitInsn(Opcodes.DRETURN);
            } else if (result == void.class) mv.visitInsn(Opcodes.RETURN);
            else if (!result.isPrimitive()) { mv.visitInsn(Opcodes.ACONST_NULL); mv.visitInsn(Opcodes.ARETURN); }
            else if (result == double.class) { mv.visitInsn(Opcodes.DCONST_0); mv.visitInsn(Opcodes.DRETURN); }
            else if (result == float.class) { mv.visitInsn(Opcodes.FCONST_0); mv.visitInsn(Opcodes.FRETURN); }
            else if (result == long.class) { mv.visitInsn(Opcodes.LCONST_0); mv.visitInsn(Opcodes.LRETURN); }
            else { mv.visitInsn(Opcodes.ICONST_0); mv.visitInsn(Opcodes.IRETURN); }
            mv.visitMaxs(0, 0); mv.visitEnd();
        }
        writer.visitEnd(); byte[] bytes = writer.toByteArray();
        return new ClassLoader(EconomyEntityFixture.class.getClassLoader()) {
            Class<?> define() { return defineClass(name.replace('/', '.'), bytes, 0, bytes.length); }
        }.define();
    }
}

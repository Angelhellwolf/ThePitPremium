package cn.charlotte.pit.supporter;

import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.item.MythicColor;
import cn.charlotte.pit.menu.supporter.SupporterConfirmMenu;
import cn.charlotte.pit.menu.supporter.SupporterMenu;
import cn.charlotte.pit.util.menu.Button;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Console-only smoke check: renders real Bukkit items using temporary, offline in-memory profiles. */
public final class SupporterRuntimeCheck {
    private SupporterRuntimeCheck() { }

    public static List<String> run() {
        SupporterService service = SupporterService.get();
        if (service == null || !service.isReady()) throw new IllegalStateException("Supporter store is not ready");
        List<String> results = new ArrayList<>();
        for (SupporterTier tier : SupporterTier.values()) {
            UUID id = UUID.randomUUID();
            String name = "PitCommerceCheck";
            Player player = createPlayer(id, name);
            PlayerProfile profile = new PlayerProfile(id, name);
            profile.setLoaded(true);
            profile.applySupporterAccount(new SupporterAccount(true, false, tier,
                    System.currentTimeMillis() + 60_000L, 1L));
            if (PlayerProfile.getCacheProfile().containsKey(id)) throw new IllegalStateException("Synthetic profile collision");
            PlayerProfile.getCacheProfile().put(id, profile);
            try {
                Set<Material> materials = new HashSet<>();
                for (Button button : new SupporterMenu().getButtons(player).values()) {
                    ItemStack item = button.getButtonItem(player);
                    if (item == null || item.getItemMeta() == null || !item.getItemMeta().hasDisplayName()
                            || !materials.add(item.getType())) {
                        throw new IllegalStateException("Invalid or duplicate sponsor menu icon");
                    }
                }
                if (materials.size() != 7) throw new IllegalStateException("Missing sponsor menu entries");
                double expectedDrop = NewConfiguration.INSTANCE.getSupporterDropChance(tier);
                double expectedRare = NewConfiguration.INSTANCE.getSupporterRareChance(tier);
                if (Math.abs(NewConfiguration.INSTANCE.getMythicDropChance(player) - expectedDrop) > 0.00000001
                        || Math.abs(NewConfiguration.INSTANCE.getChance(player, MythicColor.ORANGE) - expectedRare) > 0.00000001) {
                    throw new IllegalStateException("Unexpected live probability for " + tier);
                }
                results.add("SUPPORTER_SMOKE " + tier.name() + " menuEntries=" + materials.size()
                        + " drop=" + expectedDrop + " rare=" + expectedRare + " PASS");
            } finally {
                PlayerProfile.getCacheProfile().remove(id, profile);
            }
        }

        SupporterPricing pricing = NewConfiguration.INSTANCE.supporterPricing();
        for (SupporterProduct product : new SupporterProduct[]{SupporterProduct.MYTHIC, SupporterProduct.SACRED}) {
            boolean denied = false;
            try { pricing.quote(SupporterAccount.empty(), product, true, System.currentTimeMillis()); }
            catch (IllegalArgumentException expected) { denied = true; }
            if (!denied) throw new IllegalStateException("Non-member contract purchase was allowed");
        }
        SupporterPricing.Quote quote = pricing.quote(new SupporterAccount(true, false, SupporterTier.NONE, 0L, 0L), SupporterProduct.SACRED,
                false, System.currentTimeMillis());
        if (quote.getPoints() != 8800 || quote.isIncludesPermanent()) throw new IllegalStateException("Invalid member contract price");
        Set<Material> confirmationIcons = new HashSet<>();
        for (Button button : new SupporterConfirmMenu(quote, ignored -> { }).getButtons(null).values()) {
            if (!confirmationIcons.add(button.getButtonItem(null).getType())) {
                throw new IllegalStateException("Duplicate purchase confirmation icon");
            }
        }
        results.add("SUPPORTER_SMOKE confirmationIcons=" + confirmationIcons.size() + " memberRequired=true sacredPoints=8800 PASS");
        return results;
    }

    static Player createPlayer(UUID id, String name) {
        // Bukkit 1.8 keeps primitive-return bridge methods which java.lang.reflect.Proxy rejects.
        // Generate each JVM descriptor separately, using the ASM already bundled for Nashorn.
        String internalName = "cn/charlotte/pit/supporter/runtime/MenuPlayer";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null,
                "java/lang/Object", new String[]{"org/bukkit/entity/Player"});
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "id", "Ljava/util/UUID;", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "name", "Ljava/lang/String;", null, null).visitEnd();
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(Ljava/util/UUID;Ljava/lang/String;)V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitFieldInsn(Opcodes.PUTFIELD, internalName, "id", "Ljava/util/UUID;");
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 2);
        constructor.visitFieldInsn(Opcodes.PUTFIELD, internalName, "name", "Ljava/lang/String;");
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(2, 3);
        constructor.visitEnd();
        Set<String> emitted = new HashSet<>();
        for (Method method : Player.class.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) continue;
            String descriptor = Type.getMethodDescriptor(method);
            if (!emitted.add(method.getName() + descriptor)) continue;
            MethodVisitor body = writer.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), descriptor, null, null);
            body.visitCode();
            Type result = Type.getReturnType(descriptor);
            if (method.getName().equals("getUniqueId") && descriptor.equals("()Ljava/util/UUID;")) {
                body.visitVarInsn(Opcodes.ALOAD, 0);
                body.visitFieldInsn(Opcodes.GETFIELD, internalName, "id", "Ljava/util/UUID;");
            } else if ((method.getName().equals("getName") || method.getName().equals("getDisplayName"))
                    && descriptor.equals("()Ljava/lang/String;")) {
                body.visitVarInsn(Opcodes.ALOAD, 0);
                body.visitFieldInsn(Opcodes.GETFIELD, internalName, "name", "Ljava/lang/String;");
            } else {
                switch (result.getSort()) {
                    case Type.VOID: break;
                    case Type.LONG: body.visitInsn(Opcodes.LCONST_0); break;
                    case Type.DOUBLE: body.visitInsn(Opcodes.DCONST_0); break;
                    case Type.FLOAT: body.visitInsn(Opcodes.FCONST_0); break;
                    case Type.ARRAY: case Type.OBJECT: body.visitInsn(Opcodes.ACONST_NULL); break;
                    default: body.visitInsn(Opcodes.ICONST_0);
                }
            }
            body.visitInsn(result.getSort() == Type.VOID ? Opcodes.RETURN : result.getOpcode(Opcodes.IRETURN));
            int locals = 1;
            for (Type parameter : Type.getArgumentTypes(descriptor)) locals += parameter.getSize();
            body.visitMaxs(Math.max(1, result.getSize()), locals);
            body.visitEnd();
        }
        writer.visitEnd();
        try {
            return (Player) new StubLoader().define(writer.toByteArray())
                    .getConstructor(UUID.class, String.class).newInstance(id, name);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create offline menu player", exception);
        }
    }

    private static final class StubLoader extends ClassLoader {
        StubLoader() { super(Player.class.getClassLoader()); }
        Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }
}

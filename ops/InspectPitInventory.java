import net.minecraft.server.v1_8_R3.NBTCompressedStreamTools;
import net.minecraft.server.v1_8_R3.NBTTagCompound;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/** One-shot runtime helper for inspecting a serialized ThePit inventory. */
public final class InspectPitInventory {
    private static void printItem(String label, String encoded) throws Exception {
        if (encoded == null || encoded.equals("null") || encoded.isEmpty()) {
            return;
        }
        byte[] bytes = Base64.getDecoder().decode(encoded);
        NBTTagCompound nbt = NBTCompressedStreamTools.a(new ByteArrayInputStream(bytes));
        String text = nbt.toString();
        NBTTagCompound tag = nbt.getCompound("tag");
        NBTTagCompound display = tag.getCompound("display");
        NBTTagCompound extra = tag.getCompound("extra");
        String id = nbt.getString("id");
        String name = display.getString("Name");
        String internal = extra.getString("internal");
        String color = extra.getString("mythic_color");
        String ench = extra.getList("ench", 8).toString();
        System.out.println(label + " id=" + id + " name=" + name + " internal=" + internal
                + " color=" + color + " ench=" + ench);
        if (text.contains("minecraft:leather_leggings") || text.contains("id:leather_leggings")) {
            System.out.println("  full=" + text);
        }
    }

    public static void main(String[] args) throws Exception {
        String serialized = Files.readString(Path.of("/tmp/scinv.txt"), StandardCharsets.UTF_8);
        String[] sections = serialized.split("\\|", -1);
        String[] armor = sections[0].split(";", -1);
        for (int i = 0; i < armor.length; i++) {
            printItem("armor[" + i + "]", armor[i]);
        }
        if (sections.length < 2) {
            return;
        }
        for (String entry : sections[1].split(";", -1)) {
            int hash = entry.indexOf('#');
            if (hash <= 0) {
                continue;
            }
            printItem("slot[" + entry.substring(0, hash) + "]", entry.substring(hash + 1));
        }
    }
}

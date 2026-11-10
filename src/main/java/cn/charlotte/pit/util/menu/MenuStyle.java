package cn.charlotte.pit.util.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class MenuStyle {
    private static final Pattern DECORATION = Pattern.compile("^[\\s─━═\\-_=·•▪●◆◇❖✦✧★☆▶►▸▍»«│┃]+$");
    private static final Pattern STATUS = Pattern.compile("暂不可用|不可用|维护中|未解锁|未购买|不足|无法|取消|删除|unavailable|locked|cancel|delete|insufficient", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECTED = Pattern.compile("已选择|已装备|已解锁|已领取|已完成|selected|equipped|claimed", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION = Pattern.compile("^(点击|[左右]键|按下|Click|Left.click|Right.click|Press)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> EXCLUSIONS = exclusions();

    private MenuStyle() { }

    public static String title(String value) {
        return ChatColor.DARK_GRAY + text(value);
    }

    public static ItemStack item(ItemStack original, Button button) {
        if (original == null || original.getType() == Material.AIR || !original.hasItemMeta() || !safe(button)) {
            return original;
        }
        String material = original.getType().name();
        if (material.matches(".*_(SWORD|PICKAXE|AXE|SPADE|HOE|HELMET|CHESTPLATE|LEGGINGS|BOOTS)")
                || material.equals("BOW") || material.equals("FISHING_ROD")
                || material.equals("ENCHANTED_BOOK") && !button.getClass().getName().equals("cn.charlotte.pit.menu.supporter.button.SupporterProductButton")) {
            return original;
        }
        ItemStack result = original.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta.hasDisplayName()) {
            String name = text(meta.getDisplayName());
            String color = STATUS.matcher(name).find() ? ChatColor.RED.toString()
                    : SELECTED.matcher(name).find() ? ChatColor.GREEN.toString() : ChatColor.AQUA.toString();
            meta.setDisplayName(name.trim().isEmpty() ? " " : color + name);
        }
        if (meta.hasLore()) {
            meta.setLore(lore(meta.getLore()));
        }
        result.setItemMeta(meta);
        return result;
    }

    private static boolean safe(Button button) {
        String buttonName = button.getClass().getName();
        if (buttonName.equals("cn.charlotte.pit.menu.supporter.button.SupporterProductButton")
                || buttonName.equals("cn.charlotte.pit.menu.workshop.button.WorkshopEntranceButton")) {
            return true;
        }
        for (Class<?> type = button.getClass(); type != null && type != Button.class; type = type.getSuperclass()) {
            String name = type.getName();
            if (EXCLUSIONS.contains(name) || name.contains(".menu.trade.") || name.contains(".menu.cactus.")
                    || name.contains(".menu.viewer.") || name.contains(".menu.pack.")
                    || name.contains(".menu.workshop.") || name.contains(".enchantment.menu.")) {
                return false;
            }
        }
        return true;
    }

    public static List<String> lore(List<String> original) {
        List<String> result = new ArrayList<>();
        for (String source : original) {
            String value = text(source);
            if (value.isEmpty() || DECORATION.matcher(value).matches()) {
                if (!result.isEmpty() && !result.get(result.size() - 1).isEmpty()) {
                    result.add("");
                }
                continue;
            }
            String color = ACTION.matcher(value).find() ? ChatColor.GREEN.toString()
                    : STATUS.matcher(value).find() ? ChatColor.RED.toString()
                    : value.contains("需要") || value.contains("冷却") || value.contains("确认")
                    ? ChatColor.YELLOW.toString() : ChatColor.GRAY.toString();
            for (String line : wrap(value)) {
                result.add(color + line);
            }
        }
        while (!result.isEmpty() && result.get(result.size() - 1).isEmpty()) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    public static String text(String value) {
        if (value == null) {
            return "";
        }
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', value))
                .replaceAll("^[\\s▍▶►▸◆◇❖✦✧★☆●▪»«│┃]+", "")
                .replaceAll("[\\s▶►▸◀◆◇❖✦✧★☆│┃]+$", "")
                .replaceAll("\\s{2,}", " ").trim();
    }

    private static List<String> wrap(String value) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int length = 0;
        for (int offset = 0; offset < value.length();) {
            int point = value.codePointAt(offset);
            int size = point >= 0x2E80 && point <= 0xA4CF || point >= 0xAC00 && point <= 0xD7AF
                    || point >= 0xF900 && point <= 0xFAFF || point >= 0xFF00 && point <= 0xFFEF ? 2 : 1;
            if (length + size > 42 && line.length() > 0) {
                lines.add(line.toString().trim());
                line.setLength(0);
                length = 0;
            }
            if (line.length() > 0 || !Character.isWhitespace(point)) {
                line.appendCodePoint(point);
                length += size;
            }
            offset += Character.charCount(point);
        }
        if (line.length() > 0) {
            lines.add(line.toString().trim());
        }
        return lines;
    }

    private static Set<String> exclusions() {
        Set<String> result = new HashSet<>();
        InputStream resource = MenuStyle.class.getResourceAsStream("/menu-style-exclusions.txt");
        if (resource == null) {
            Collections.addAll(result,
                    "cn.charlotte.pit.enchantment.menu.button.EnchantBookButton",
                    "cn.charlotte.pit.enchantment.menu.button.ItemSlotButton",
                    "cn.charlotte.pit.menu.admin.item.button.MythicItemButton",
                    "cn.charlotte.pit.menu.cactus.CactusMenu$1",
                    "cn.charlotte.pit.menu.cactus.DimensionalCactusMenu$1",
                    "cn.charlotte.pit.menu.cactus.SacrosanctCactusMenu$pantsButton$1",
                    "cn.charlotte.pit.menu.gem.button.ItemGemButton",
                    "cn.charlotte.pit.menu.gem.global.button.GlobalItemGemButton",
                    "cn.charlotte.pit.menu.mail.button.MailClaimButton",
                    "cn.charlotte.pit.menu.rune.button.DJEnchantButton",
                    "cn.charlotte.pit.menu.rune.button.ItemRuneButton",
                    "cn.charlotte.pit.menu.trade.button.CoinDisplayButton",
                    "cn.charlotte.pit.menu.trade.button.ItemDisplayButton",
                    "cn.charlotte.pit.util.menu.buttons.DisplayButton");
            return Collections.unmodifiableSet(result);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    result.add(line.trim());
                }
            }
        } catch (Exception error) {
            throw new IllegalStateException("Could not read menu item exclusions", error);
        }
        return Collections.unmodifiableSet(result);
    }
}

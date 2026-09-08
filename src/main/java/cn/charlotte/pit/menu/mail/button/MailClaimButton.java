package cn.charlotte.pit.menu.mail.button;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.mail.Mail;
import cn.charlotte.pit.data.mail.MailClaimTransaction;
import cn.charlotte.pit.data.sub.PlayerInv;
import cn.charlotte.pit.reward.RewardService;
import cn.charlotte.pit.reward.RewardSource;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.inventory.InventoryUtil;
import cn.charlotte.pit.util.item.ItemBuilder;
import cn.charlotte.pit.util.menu.Button;
import cn.charlotte.pit.util.menu.Menu;
import cn.charlotte.pit.util.time.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @Author: EmptyIrony
 * @Date: 2021/3/25 21:23
 */
public class MailClaimButton extends Button {
    private final Menu mainMenu;
    private final Mail mail;

    public MailClaimButton(Menu mainMenu, Mail mail) {
        this.mainMenu = mainMenu;
        this.mail = mail;
    }

    @Override
    public ItemStack getButtonItem(Player player) {
        final ItemBuilder builder = new ItemBuilder(mail.isClaimed() ? Material.HOPPER_MINECART : Material.STORAGE_MINECART);
        builder.name(CC.translate(mail.getTitle() == null ? "无标题" : mail.getTitle() + (mail.isClaimed() ? " &7#已读" : "")));
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (mail.getCoins() != 0) {
            lore.add("&6" + mail.getCoins() + " 硬币");
        }
        if (mail.getExp() != 0) {
            lore.add("&b" + mail.getExp() + " 经验值");
        }
        if (mail.getRenown() != 0) {
            lore.add("&e" + mail.getRenown() + " 声望");
        }
        if (attachments().length > 0) {
            for (ItemStack content : attachments()) {
                if (content != null) {
                    lore.add("&7" + content.getAmount() + "x &f" + (content.getItemMeta().getDisplayName() == null ? content.getType().name() : content.getItemMeta().getDisplayName()));
                }
            }
        }
        lore.add("&8&l&m" + CC.stripColor(CC.MENU_BAR));
        if (mail.getContent() != null) {
            lore.add("&7邮件信息: ");
            lore.addAll(Arrays.asList(mail.getContent().split("\\\\n")));
            lore.add("&8&l&m" + CC.stripColor(CC.MENU_BAR));
        }
        lore.add("&7邮件到期时间: ");
        final long now = System.currentTimeMillis();
        if (mail.getExpireTime() > now) {
            lore.add("&e" + TimeUtil.millisToRoundedTime(mail.getExpireTime() - now) + "后");
        } else {
            lore.add("&c邮件已过期" + (mail.isClaimed() ? ",无法领取邮件内物品" : ""));
        }
        lore.add("&8&l&m" + CC.stripColor(CC.MENU_BAR));


        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (!mail.isClaimed() && hasReceipt(profile)) {
            lore.add("");
            lore.add("&e点击重试保存本次领取!");
        } else if (!mail.isClaimed() && mail.getExpireTime() > now) {
            lore.add("");
            lore.add("&e点击领取此邮件内全部物品!");
            builder.shiny();
        } else {
            lore.add("");
            lore.add("&e右键点击以删除该邮件!");
        }

        builder.lore(lore);

        return builder.build();
    }

    @Override
    public void clicked(Player player, int slot, ClickType clickType, int hotbarButton, ItemStack currentItem) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> clicked(player, slot, clickType, hotbarButton, currentItem));
            return;
        }
        final PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (!player.isOnline() || profile == null || !profile.isLoaded() || profile.isBot()) return;
        Mail currentMail = profile.getMailData().getMails().stream()
                .filter(entry -> mail.getUuid().equals(entry.getUuid())).findFirst().orElse(null);
        if (currentMail == null) return;
        if (currentMail != mail) {
            new MailClaimButton(mainMenu, currentMail).clicked(player, slot, clickType, hotbarButton, currentItem);
            return;
        }
        boolean pendingReceipt = hasReceipt(profile) && !mail.isClaimed();
        if (clickType == ClickType.RIGHT && !pendingReceipt && (mail.isClaimed() || mail.getExpireTime() < System.currentTimeMillis())) {
            List<Mail> before = new ArrayList<>(profile.getMailData().getMails());
            try {
                profile.getMailData().getMails().removeIf(mailObj -> mailObj.getUuid().equals(mail.getUuid()));
                profile.getMailData().save();
                mainMenu.setClosedByMenu(true);
                mainMenu.openMenu(player);
                player.playSound(player.getLocation(), Sound.NOTE_PLING, 1, 1);
            } catch (Exception e) {
                profile.getMailData().setMails(before);
                CC.printErrorWithCode(player, e);
            }
            return;
        }
        if (clickType != ClickType.LEFT) return;
        if (mail.isClaimed()) {
            player.sendMessage(CC.translate("&c此邮件已被领取!"));
            player.playSound(player.getLocation(), Sound.ENDERMAN_TELEPORT, 1, 1);
            return;
        }
        if (!pendingReceipt && mail.getExpireTime() < System.currentTimeMillis()) {
            player.sendMessage(CC.translate("&c此邮件已过期!"));
            player.playSound(player.getLocation(), Sound.ENDERMAN_TELEPORT, 1, 1);
            return;
        }

        if (profile.isTempInvUsing()) {
            player.sendMessage(CC.translate("&c请在活动结束、恢复原背包后领取邮件。"));
            return;
        }
        final int mailSlots = InventoryUtil.getInventoryFilledSlots(attachments());
        if (!pendingReceipt && mailSlots > 0) {
            final int slots = InventoryUtil.getInventoryEmptySlots(player.getInventory().getContents());
            if (slots < mailSlots) {
                player.sendMessage(CC.translate("&c你的背包空间不足,无法领取此邮件!"));
                player.playSound(player.getLocation(), Sound.ENDERMAN_TELEPORT, 1, 1);
                return;
            }
        }

        if (profile.getClaimedMail() == null) profile.setClaimedMail(new ArrayList<>());
        try {
            // Keep the grant and both saves on the server thread: no worker mutates a live profile
            // or serializes it while combat is changing the same balances/inventory.
            MailClaimTransaction.complete(mail.getUuid(), profile.getClaimedMail(),
                    () -> grantRewards(player, profile), () -> profile.save(player),
                    mail::setClaimed, () -> profile.getMailData().save());
            mainMenu.setClosedByMenu(true);
            mainMenu.openMenu(player);
            player.playSound(player.getLocation(), Sound.LEVEL_UP, 1, 1);
            player.sendMessage(CC.translate("&a领取成功!"));
        } catch (Exception e) {
            player.sendMessage(CC.translate("&c邮件保存失败，请稍后再次点击重试；本次已发放的奖励不会重复发放。"));
            CC.printErrorWithCode(player, e);
        }
    }

    private boolean hasReceipt(PlayerProfile profile) {
        return profile != null && profile.getClaimedMail() != null
                && profile.getClaimedMail().contains(MailClaimTransaction.receiptId(mail.getUuid()));
    }

    private ItemStack[] attachments() {
        return mail.getItem() == null || mail.getItem().getContents() == null
                ? new ItemStack[0] : mail.getItem().getContents();
    }

    private void grantRewards(Player player, PlayerProfile profile) {
        if (!Double.isFinite(mail.getCoins()) || mail.getCoins() < 0
                || !Double.isFinite(mail.getExp()) || mail.getExp() < 0 || mail.getRenown() < 0) {
            throw new IllegalArgumentException("Invalid mail reward: " + mail.getUuid());
        }
        PlayerInv inventoryBefore = PlayerInv.fromPlayerInventory(player.getInventory());
        double coinsBefore = profile.getCoins();
        double experienceBefore = profile.getExperience();
        double grindedCoinsBefore = profile.getGrindedCoins();
        int renownBefore = profile.getRenown();
        try {
            for (ItemStack content : attachments()) {
                if (content != null && content.getType() != Material.AIR
                        && !player.getInventory().addItem(content.clone()).isEmpty()) {
                    throw new IllegalStateException("Mail attachments do not fit in the inventory");
                }
            }
            profile.setRenown(profile.getRenown() + mail.getRenown());
            RewardService.grant(profile, RewardSource.MAIL, mail.getCoins(), mail.getExp());
        } catch (RuntimeException exception) {
            player.getInventory().setContents(inventoryBefore.getContents());
            player.getInventory().setArmorContents(inventoryBefore.getArmorContents());
            profile.setCoins(coinsBefore);
            profile.setExperience(experienceBefore);
            profile.setGrindedCoins(grindedCoinsBefore);
            profile.setRenown(renownBefore);
            profile.applyExperienceToPlayer(player);
            throw exception;
        }
    }
}

package ru.mirtime.punish;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.*;

import static ru.mirtime.punish.MirTimePunish.color;

public class ModPanel implements Listener {

    public static class ReportEntry {
        public final String sender;
        public final String target;
        public final String reason;
        public final String time;

        public ReportEntry(String sender, String target, String reason) {
            this.sender = sender;
            this.target = target;
            this.reason = reason;
            this.time = new SimpleDateFormat("HH:mm").format(new Date());
        }
    }

    private final MirTimePunish plugin;
    private final List<ReportEntry> reports = new ArrayList<>();
    private final Map<UUID, Location> specReturnLoc = new HashMap<>();
    private final Map<UUID, GameMode> specReturnMode = new HashMap<>();
    private final Map<Integer, ReportEntry> slotMap = new HashMap<>();

    public ModPanel(MirTimePunish plugin) {
        this.plugin = plugin;
    }

    public void createReport(Player sender, String targetName, String reason) {
        reports.add(new ReportEntry(sender.getName(), targetName, reason));
        sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &#00FF66Жалоба на игрока &f" + targetName + " &#00FF66отправлена модерации!"));

        TextComponent msg = new TextComponent(color("&#0055ff&l[!] &#00E5FFНовая жалоба: &f" + sender.getName() + " &7➔ &c" + targetName + " &8(&f" + reason + "&8) "));
        TextComponent btn = new TextComponent(color("&#00FF66&l[ОТКРЫТЬ ПАНЕЛЬ]"));
        btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reports"));

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("mirtime.punish.reports")) {
                staff.spigot().sendMessage(msg, btn);
                staff.playSound(staff.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
            }
        }
    }

    public void toggleSpec(Player mod, Player target) {
        if (specReturnLoc.containsKey(mod.getUniqueId()) && target == null) {
            mod.teleport(specReturnLoc.remove(mod.getUniqueId()));
            mod.setGameMode(specReturnMode.getOrDefault(mod.getUniqueId(), GameMode.SURVIVAL));
            specReturnMode.remove(mod.getUniqueId());
            mod.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fВы вышли из режима слежки и вернулись назад."));
            return;
        }
        if (target == null) {
            mod.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cУкажите игрока в сети: &#00FF66/spec <ник>"));
            return;
        }
        if (!specReturnLoc.containsKey(mod.getUniqueId())) {
            specReturnLoc.put(mod.getUniqueId(), mod.getLocation());
            specReturnMode.put(mod.getUniqueId(), mod.getGameMode());
        }
        mod.setGameMode(GameMode.SPECTATOR);
        mod.teleport(target.getLocation());
        mod.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fВы следите за &#00FF66" + target.getName() + "&f. Чтобы вернуться: &#00E5FF/spec"));
    }

    public void openReportsMenu(Player mod) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&#0055ff&l⚡ Панель модерации &8(&fЖалоб: &#00FF66" + reports.size() + "&8)"));
        ItemStack blue = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        var bMeta = blue.getItemMeta();
        if (bMeta != null) { bMeta.setDisplayName(color("&#0055ff▪")); blue.setItemMeta(bMeta); }
        for (int s : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,50,51,52,53}) inv.setItem(s, blue);

        ItemStack exitSpec = new ItemStack(Material.ENDER_EYE);
        var eMeta = exitSpec.getItemMeta();
        if (eMeta != null) {
            eMeta.setDisplayName(color("&#00E5FF&l[👁] Выйти из слежки (/spec)"));
            exitSpec.setItemMeta(eMeta);
        }
        inv.setItem(49, exitSpec);

        slotMap.clear();
        int[] slots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};
        for (int i = 0; i < Math.min(reports.size(), slots.length); i++) {
            ReportEntry r = reports.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(r.target));
                meta.setDisplayName(color("&c&lНарушитель: &f" + r.target + " &8[" + r.time + "]"));
                meta.setLore(List.of(
                        color("&#0055ff● &fОтправил: &#00E5FF" + r.sender),
                        color("&#0055ff● &fПричина: &e" + r.reason),
                        "",
                        color("&#00FF66[ЛКМ] &fТелепорт и скрытая слежка (/spec)"),
                        color("&#00E5FF[ПКМ] &fВызвать на проверку читов (/check)"),
                        color("&c[ШИФТ+ПКМ] &fЗакрыть и удалить жалобу")
                ));
                head.setItemMeta(meta);
            }
            inv.setItem(slots[i], head);
            slotMap.put(slots[i], r);
        }
        mod.openInventory(inv);
    }

    @EventHandler
    public void onPanelClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getView().getTitle().contains("Панель модерации")) return;
        e.setCancelled(true);

        if (e.getRawSlot() == 49) {
            p.closeInventory();
            toggleSpec(p, null);
            return;
        }

        ReportEntry r = slotMap.get(e.getRawSlot());
        if (r == null) return;

        ClickType click = e.getClick();
        if (click == ClickType.SHIFT_RIGHT) {
            reports.remove(r);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            openReportsMenu(p);
        } else if (click == ClickType.LEFT) {
            p.closeInventory();
            toggleSpec(p, Bukkit.getPlayerExact(r.target));
        } else if (click == ClickType.RIGHT) {
            p.closeInventory();
            Bukkit.dispatchCommand(p, "check " + r.target);
        }
    }
}

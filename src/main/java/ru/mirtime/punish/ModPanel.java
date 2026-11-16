/*
 *    __  __ _        _____ _                
 *   |  \/  (_)_ __  |_   _(_)_ __ ___   ___ 
 *   | |\/| | | '__|   | | | | '_ ` _ \ / _ \
 *   | |  | | | |      | | | | | | | | |  __/
 *   |_|  |_|_|_|      |_| |_|_| |_| |_|\___|
 *                                           
 *   Project: Mir Time | Author: Ars3nBar
 */
package ru.mirtime.punish;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
    private final List<ReportEntry> playerReports = new ArrayList<>();
    private final List<ReportEntry> acReports = new ArrayList<>();
    private final Map<UUID, Boolean> viewingAcTab = new HashMap<>();
    private final Map<UUID, Location> specReturnLoc = new HashMap<>();
    private final Map<UUID, GameMode> specReturnMode = new HashMap<>();
    private final Map<Integer, ReportEntry> slotMap = new HashMap<>();

    public ModPanel(MirTimePunish plugin) {
        this.plugin = plugin;
    }

    public void createReport(Player sender, String targetName, String reason) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cИгрок с таким ником сейчас не в сети."));
            return;
        }
        playerReports.add(new ReportEntry(sender.getName(), target.getName(), reason));
        sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &#00FF66Жалоба на игрока &f" + target.getName() + " &#00FF66отправлена модерации!"));

        TextComponent msg = new TextComponent(color("&#0055ff&l[!] &#00E5FFНовая жалоба: &f" + sender.getName() + " &7➔ &c" + target.getName() + " &8(&f" + reason + "&8) "));
        TextComponent btn = new TextComponent(color("&#00FF66&l[ОТКРЫТЬ ПАНЕЛЬ]"));
        btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reports"));

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("mirtime.punish.reports")) {
                staff.spigot().sendMessage(msg, btn);
                staff.playSound(staff.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
            }
        }
    }

    public void createAcReport(String targetName, String detectInfo) {
        acReports.removeIf(r -> r.target.equalsIgnoreCase(targetName) && r.reason.equalsIgnoreCase(detectInfo));
        acReports.add(new ReportEntry("MT-AC", targetName, detectInfo));
        if (acReports.size() > 28) acReports.remove(0);

        TextComponent msg = new TextComponent(color("&#0055ff&l[MT-AC] &c" + targetName + " &8» &#00E5FF" + detectInfo + " "));
        TextComponent btn = new TextComponent(color("&#00FF66&l[ПРОВЕРИТЬ]"));
        btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reports"));

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("mirtime.punish.reports")) {
                staff.spigot().sendMessage(msg, btn);
                staff.playSound(staff.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
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
        boolean isAc = viewingAcTab.getOrDefault(mod.getUniqueId(), false);
        String title = isAc
                ? "&#0055ff&l⚡ Репорты: &#00FF66Античит [MT-AC]"
                : "&#0055ff&l⚡ Репорты: &#00E5FFИгроки";
        Inventory inv = Bukkit.createInventory(null, 54, color(title));

        ItemStack blue = item(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff▪", null);
        ItemStack cyan = item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&#00E5FF▫", null);
        for (int s : new int[]{0,1,7,8,9,17,18,26,27,35,36,44,45,46,52,53}) inv.setItem(s, blue);
        for (int s : new int[]{2,6,47,48,50,51}) inv.setItem(s, cyan);

        inv.setItem(3, item(Material.PLAYER_HEAD,
                (!isAc ? "&#00FF66&l[✔] " : "&7") + "&#00E5FF&lЖалобы от игроков &8(&f" + playerReports.size() + "&8)",
                List.of(" &7Нажмите, чтобы открыть жалобы игроков")));

        inv.setItem(5, item(Material.SHIELD,
                (isAc ? "&#00FF66&l[✔] " : "&7") + "&#0055ff&lДетекты античита [MT-AC] &8(&f" + acReports.size() + "&8)",
                List.of(" &7Нажмите, чтобы открыть срабатывания античита")));

        inv.setItem(49, item(Material.ENDER_EYE, "&#00E5FF&l[👁] Выйти из слежки (/spec)", null));

        slotMap.clear();
        List<ReportEntry> activeList = isAc ? acReports : playerReports;
        int[] slots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};
        for (int i = 0; i < Math.min(activeList.size(), slots.length); i++) {
            ReportEntry r = activeList.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(r.target));
                meta.setDisplayName(color((isAc ? "&#0055ff&l[MT-AC] &c&l" : "&c&lНарушитель: &f") + r.target + " &8[" + r.time + "]"));
                meta.setLore(List.of(
                        color("&#0055ff● &fИсточник: &#00E5FF" + r.sender),
                        color("&#0055ff● &fПричина: &e" + r.reason),
                        "",
                        color("&#00FF66[ЛКМ] &fТелепорт и скрытая слежка (/spec)"),
                        color("&#00E5FF[ПКМ] &fВызвать на проверку читов (/check)"),
                        color("&c[ШИФТ+ПКМ] &fЗакрыть и удалить запись")
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
        if (!e.getView().getTitle().contains("Репорты:")) return;
        e.setCancelled(true);

        int slot = e.getRawSlot();
        if (slot == 3) {
            viewingAcTab.put(p.getUniqueId(), false);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            openReportsMenu(p);
            return;
        }
        if (slot == 5) {
            viewingAcTab.put(p.getUniqueId(), true);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            openReportsMenu(p);
            return;
        }
        if (slot == 49) {
            p.closeInventory();
            toggleSpec(p, null);
            return;
        }

        ReportEntry r = slotMap.get(slot);
        if (r == null) return;

        boolean isAc = viewingAcTab.getOrDefault(p.getUniqueId(), false);
        ClickType click = e.getClick();
        if (click == ClickType.SHIFT_RIGHT) {
            if (isAc) acReports.remove(r);
            else playerReports.remove(r);
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

    @EventHandler
    public void onModQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (specReturnLoc.containsKey(id)) {
            e.getPlayer().teleport(specReturnLoc.remove(id));
            e.getPlayer().setGameMode(specReturnMode.getOrDefault(id, GameMode.SURVIVAL));
            specReturnMode.remove(id);
        }
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore != null) {
                List<String> list = new ArrayList<>();
                for (String s : lore) list.add(color(s));
                meta.setLore(list);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            is.setItemMeta(meta);
        }
        return is;
    }
}

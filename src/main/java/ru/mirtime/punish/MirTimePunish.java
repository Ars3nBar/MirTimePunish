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

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
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

    private final MirTimePunish plugin;
    private final List<ReportEntry> playerReports = new ArrayList<>();
    private final List<ReportEntry> acReports = new ArrayList<>();
    private final Map<UUID, Boolean> viewingAcTab = new HashMap<>();
    private final Map<UUID, Location> specReturnLoc = new HashMap<>();
    private final Map<UUID, GameMode> specReturnMode = new HashMap<>();

    public ModPanel(MirTimePunish plugin) {
        this.plugin = plugin;
    }

    public static class ReportEntry {
        final String target;
        final String sender;
        final String reason;
        final String time;

        ReportEntry(String target, String sender, String reason) {
            this.target = target;
            this.sender = sender;
            this.reason = reason;
            this.time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        }
    }

    public void createReport(Player sender, String targetName, String reason) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cИгрок с таким ником сейчас не в сети."));
            return;
        }
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cНельзя отправить жалобу на самого себя!"));
            return;
        }
        playerReports.add(new ReportEntry(target.getName(), sender.getName(), reason));
        sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &#00FF66Жалоба на игрока &f" + target.getName() + " &#00FF66отправлена модерации!"));

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("mirtime.punish.reports")) {
                staff.playSound(staff.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
                staff.sendMessage(color("&#0055ff&l[РЕПОРТ] &#00E5FF" + sender.getName() + " &7➔ &c" + target.getName() + " &8(&f" + reason + "&8) &7[/reports]"));
            }
        }
    }

    public void createAcReport(String targetName, String detectInfo) {
        acReports.removeIf(r -> r.target.equalsIgnoreCase(targetName) && r.reason.equalsIgnoreCase(detectInfo));
        acReports.add(new ReportEntry(targetName, "MT-AC", detectInfo));
        if (acReports.size() > 28) acReports.remove(0);

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("mirtime.punish.reports")) {
                staff.playSound(staff.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
                staff.sendMessage(color("&#0055ff&l[MT-AC] &c" + targetName + " &8» &#00E5FF" + detectInfo + " &7[/reports]"));
            }
        }
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
        List<ReportEntry> list = isAc ? acReports : playerReports;
        int[] grid = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};
        for (int i = 0; i < Math.min(list.size(), grid.length); i++) {
            ReportEntry r = list.get(i);
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(r.target));
                meta.setDisplayName(color((isAc ? "&#0055ff&l[MT-AC] &c&l" : "&c&l[!] &f") + r.target));
                meta.setLore(List.of(
                        color(" &#0055ff● &fИсточник: &#00E5FF" + r.sender),
                        color(" &#0055ff● &fПричина: &c" + r.reason),
                        color(" &#0055ff● &fВремя: &7" + r.time),
                        "",
                        color(" &#00FF66ЛКМ &7➔ Начать слежку (/spec)"),
                        color(" &#00E5FFПКМ &7➔ Вызвать на проверку (/check)"),
                        color(" &cШифт + ПКМ &7➔ Закрыть и удалить")
                ));
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                skull.setItemMeta(meta);
            }
            inv.setItem(grid[i], skull);
        }

        inv.setItem(49, item(Material.BARRIER, "&c&l[Очистить текущую вкладку]", List.of("&7Удаляет все записи в открытом списке")));
        mod.openInventory(inv);
    }

    @EventHandler
    public void onReportClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player mod)) return;
        String title = e.getView().getTitle();
        if (!title.contains("Репорты:")) return;
        e.setCancelled(true);

        int slot = e.getRawSlot();
        boolean isAc = viewingAcTab.getOrDefault(mod.getUniqueId(), false);

        if (slot == 3) {
            viewingAcTab.put(mod.getUniqueId(), false);
            mod.playSound(mod.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            openReportsMenu(mod);
            return;
        }
        if (slot == 5) {
            viewingAcTab.put(mod.getUniqueId(), true);
            mod.playSound(mod.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            openReportsMenu(mod);
            return;
        }

        List<ReportEntry> list = isAc ? acReports : playerReports;
        if (slot == 49) {
            list.clear();
            mod.playSound(mod.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            openReportsMenu(mod);
            return;
        }

        int[] grid = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};
        for (int i = 0; i < grid.length; i++) {
            if (grid[i] == slot && i < list.size()) {
                ReportEntry r = list.get(i);
                if (e.getClick() == ClickType.SHIFT_RIGHT) {
                    list.remove(i);
                    openReportsMenu(mod);
                } else if (e.isLeftClick()) {
                    mod.closeInventory();
                    Player target = Bukkit.getPlayer(r.target);
                    if (target != null) toggleSpec(mod, target);
                    else mod.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cИгрок уже вышел с сервера."));
                } else if (e.isRightClick()) {
                    mod.closeInventory();
                    Bukkit.dispatchCommand(mod, "check " + r.target);
                }
                return;
            }
        }
    }

    public void toggleSpec(Player mod, Player target) {
        UUID id = mod.getUniqueId();
        if (specReturnLoc.containsKey(id) && target == null) {
            mod.teleport(specReturnLoc.remove(id));
            mod.setGameMode(specReturnMode.getOrDefault(id, GameMode.SURVIVAL));
            specReturnMode.remove(id);
            mod.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &#00FF66Вы вышли из режима слежки."));
            return;
        }
        if (target == null) {
            mod.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИспользование: &#00FF66/spec <ник> &7(или /spec для выхода)"));
            return;
        }
        if (!specReturnLoc.containsKey(id)) {
            specReturnLoc.put(id, mod.getLocation());
            specReturnMode.put(id, mod.getGameMode());
        }
        mod.setGameMode(GameMode.SPECTATOR);
        mod.teleport(target.getLocation());
        mod.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fВы следите за &#00FF66" + target.getName() + "&f. Чтобы вернуться: &#00E5FF/spec"));
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

package ru.mirtime.punish;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.*;

public class ModPanel implements Listener {

    private final MirTimePunish plugin;
    private final List<ReportEntry> reports = new ArrayList<>();
    private final Map<UUID, Location> specReturnLoc = new HashMap<>();
    private final Map<UUID, GameMode> specReturnMode = new HashMap<>();
    private final Map<UUID, Map<Integer, ReportEntry>> playerSlotMaps = new HashMap<>();

    private final Map<UUID, Boolean> viewingAntiCheatTab = new HashMap<>();
    private final Map<UUID, OsFilter> osFilters = new HashMap<>();

    public enum OsFilter {
        ALL("Все ОС", "&#00E5FFВсе ОС"),
        WINDOWS("Windows", "&#00E5FFWindows"),
        LINUX("Linux", "&#00FF66Linux"),
        OTHER("Другие", "&eДругие");

        public final String label;
        public final String colored;

        OsFilter(String label, String colored) {
            this.label = label;
            this.colored = colored;
        }

        public OsFilter next() {
            return switch (this) {
                case ALL -> WINDOWS;
                case WINDOWS -> LINUX;
                case LINUX -> OTHER;
                case OTHER -> ALL;
            };
        }
    }
    public static class PanelHolder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
        public void setInventory(Inventory inv) { this.inv = inv; }
    }

    public static class ReportEntry {
        public final String sender;
        public final String target;
        public final String reason;
        public final String time;
        public final boolean isAntiCheat;

        public ReportEntry(String sender, String target, String reason, boolean isAntiCheat) {
            this.sender = sender;
            this.target = target;
            this.reason = reason;
            this.time = new SimpleDateFormat("HH:mm").format(new Date());
            this.isAntiCheat = isAntiCheat;
        }
    }

    public ModPanel(MirTimePunish plugin) {
        this.plugin = plugin;
    }

    public boolean isSpec(UUID uuid) {
        return specReturnLoc.containsKey(uuid);
    }

    public void createReport(Player staff, String targetName, String reason) {
        ReportEntry entry = new ReportEntry(staff.getName(), targetName, reason, false);
        reports.add(entry);

        staff.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &#00FF66Жалоба на игрока &f" + targetName + " &#00FF66отправлена модерации!"));

        TextComponent msg = new TextComponent(MirTimePunish.color("&#0055ff&l[!] &#00E5FFНовая жалоба: &f" + targetName + " &7➔ &c" + reason + " &8(&f" + entry.time + "&8) "));
        TextComponent btn = new TextComponent(MirTimePunish.color("&#00FF66&l[ОТКРЫТЬ ПАНЕЛЬ]"));
        btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reports"));
        msg.addExtra(btn);

        for (Player mod : Bukkit.getOnlinePlayers()) {
            if (mod.hasPermission("mirtime.punish.reports")) {
                mod.spigot().sendMessage(msg);
                mod.playSound(mod.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            }
        }
    }

    public void createSystemReport(String source, String targetName, String reason) {
        for (ReportEntry existing : reports) {
            if (existing.isAntiCheat && existing.target.equalsIgnoreCase(targetName) && existing.reason.equalsIgnoreCase(reason)) {
                return;
            }
        }
        ReportEntry entry = new ReportEntry(source, targetName, reason, true);
        reports.add(entry);

        TextComponent msg = new TextComponent(MirTimePunish.color("&#FF2A55&l[MT-AC] &#00E5FFДетект: &f" + targetName + " &7➔ &c" + reason + " "));
        TextComponent btn = new TextComponent(MirTimePunish.color("&#00FF66&l[ПАНЕЛЬ /REPORTS]"));
        btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reports"));
        msg.addExtra(btn);

        for (Player mod : Bukkit.getOnlinePlayers()) {
            if (mod.hasPermission("mirtime.punish.reports")) {
                mod.spigot().sendMessage(msg);
                mod.playSound(mod.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.4f);
            }
        }
    }

    public void toggleSpec(Player mod, Player target) {
        UUID id = mod.getUniqueId();

        if (specReturnLoc.containsKey(id)) {
            Location ret = specReturnLoc.remove(id);
            if (ret != null) mod.teleport(ret);
            GameMode prevMode = specReturnMode.remove(id);
            mod.setGameMode(prevMode != null ? prevMode : GameMode.SURVIVAL);
            mod.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fВы вышли из режима слежки и вернулись назад."));
            return;
        }

        if (target == null) {
            mod.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cУкажите игрока в сети: &#00FF66/spec <ник>"));
            return;
        }

        specReturnLoc.put(id, mod.getLocation().clone());
        specReturnMode.put(id, mod.getGameMode());
        mod.setGameMode(GameMode.SPECTATOR);
        mod.teleport(target.getLocation());
        mod.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fВы следите за &#00FF66" + target.getName() + "&f. Чтобы вернуться: &#00E5FF/spec"));
    }
    public void openReportsMenu(Player mod) {
        boolean isAcTab = viewingAntiCheatTab.getOrDefault(mod.getUniqueId(), false);
        OsFilter currentFilter = osFilters.getOrDefault(mod.getUniqueId(), OsFilter.ALL);

        long playerReportsCount = reports.stream().filter(r -> !r.isAntiCheat).count();
        long acReportsCount = reports.stream().filter(r -> r.isAntiCheat).count();

        String tabTitle = isAcTab ? "&#FF2A55Античит MT-AC" : "&#00FF66Жалобы игроков";
        PanelHolder holder = new PanelHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, MirTimePunish.color("&#0055ff&l⚡ Репорты &8| " + tabTitle));
        holder.setInventory(inv);

        ItemStack blue = simpleItem(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff▪", null);
        int[] border = {0, 1, 5, 7, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53};
        for (int s : border) inv.setItem(s, blue);

        inv.setItem(2, simpleItem(
                !isAcTab ? Material.LIME_DYE : Material.GRAY_DYE,
                (!isAcTab ? "&#00FF66&l[✔] Жалобы от игроков" : "&7[ ] Жалобы от игроков") + " &8(&f" + playerReportsCount + "&8)",
                List.of("&7Нажмите, чтобы открыть жалобы,", "&7отправленные игроками через /report.")
        ));

        inv.setItem(3, simpleItem(
                isAcTab ? Material.REDSTONE : Material.GUNPOWDER,
                (isAcTab ? "&#FF2A55&l[✔] Детекты MT-AC" : "&7[ ] Детекты MT-AC") + " &8(&f" + acReportsCount + "&8)",
                List.of("&7Нажмите, чтобы открыть срабатывания", "&7самописного античита MT-AC v3.0.")
        ));

        inv.setItem(4, simpleItem(Material.ENDER_EYE, "&#00E5FF&l[👁] Выйти из слежки (/spec)", null));

        inv.setItem(6, simpleItem(Material.COMPASS, "&#0055ff&l[💻] Фильтр по ОС: " + currentFilter.colored, List.of(
                "&7Текущий фильтр: " + currentFilter.colored,
                "",
                "&#00FF66➥ Нажмите&f, чтобы переключить:",
                "&7Все ОС ➔ Windows ➔ Linux ➔ Другие"
        )));

        inv.setItem(8, simpleItem(Material.BARRIER, "&c&l[✖] Очистить текущую вкладку", List.of("&7Удалить все записи из открытой вкладки.")));

        Map<Integer, ReportEntry> slotMap = new HashMap<>();
        playerSlotMaps.put(mod.getUniqueId(), slotMap);

        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        List<ReportEntry> filtered = new ArrayList<>();
        for (ReportEntry r : reports) {
            if (r.isAntiCheat != isAcTab) continue;
            OsManager.OsType targetOs = plugin.getOsManager().getPlayerOs(r.target);
            boolean matchesOs = switch (currentFilter) {
                case ALL -> true;
                case WINDOWS -> targetOs == OsManager.OsType.WINDOWS;
                case LINUX -> targetOs == OsManager.OsType.LINUX;
                case OTHER -> targetOs == OsManager.OsType.OTHER || targetOs == OsManager.OsType.UNKNOWN;
            };
            if (matchesOs) filtered.add(r);
        }

        int limit = Math.min(filtered.size(), slots.length);
        for (int i = 0; i < limit; i++) {
            ReportEntry r = filtered.get(i);
            OsManager.OsType targetOs = plugin.getOsManager().getPlayerOs(r.target);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(r.target));
                String tag = r.isAntiCheat ? "&#FF2A55&l[MT-AC] " : "&c&l[ЖАЛОБА] ";
                meta.setDisplayName(MirTimePunish.color(tag + "&f" + r.target + " &8[" + r.time + "]"));
                meta.setLore(List.of(
                        MirTimePunish.color("&#0055ff● &fИсточник: &#00E5FF" + r.sender),
                        MirTimePunish.color("&#0055ff● &fПричина: &e" + r.reason),
                        MirTimePunish.color("&#0055ff● &fОС игрока: " + targetOs.coloredName),
                        "",
                        MirTimePunish.color("&#00FF66[ЛКМ] &fТелепорт и скрытая слежка (/spec)"),
                        MirTimePunish.color("&#00E5FF[ПКМ] &fВызвать на проверку читов (/check)"),
                        MirTimePunish.color("&c[ШИФТ+ПКМ] &fЗакрыть и удалить запись")
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
        if (!(e.getView().getTopInventory().getHolder() instanceof PanelHolder)) return;

        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        int slot = e.getRawSlot();
        UUID id = p.getUniqueId();
        boolean isAcTab = viewingAntiCheatTab.getOrDefault(id, false);

        if (slot == 2) {
            viewingAntiCheatTab.put(id, false);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            openReportsMenu(p);
            return;
        }
        if (slot == 3) {
            viewingAntiCheatTab.put(id, true);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            openReportsMenu(p);
            return;
        }
        if (slot == 4) {
            p.closeInventory();
            toggleSpec(p, null);
            return;
        }
        if (slot == 6) {
            OsFilter next = osFilters.getOrDefault(id, OsFilter.ALL).next();
            osFilters.put(id, next);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.3f);
            openReportsMenu(p);
            return;
        }
        if (slot == 8) {
            reports.removeIf(r -> r.isAntiCheat == isAcTab);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            openReportsMenu(p);
            return;
        }

        Map<Integer, ReportEntry> slotMap = playerSlotMaps.get(id);
        if (slotMap == null) return;
        ReportEntry r = slotMap.get(slot);
        if (r == null) return;

        ClickType click = e.getClick();
        if (click == ClickType.SHIFT_RIGHT) {
            reports.remove(r);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            openReportsMenu(p);
        } else if (click == ClickType.LEFT) {
            p.closeInventory();
            toggleSpec(p, Bukkit.getPlayerExact(r.target));
        } else if (click == ClickType.RIGHT) {
            p.closeInventory();
            Bukkit.dispatchCommand(p, "check " + r.target);
        }
    }

    private ItemStack simpleItem(Material mat, String name, List<String> lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MirTimePunish.color(name));
            if (lore != null) meta.setLore(lore.stream().map(MirTimePunish::color).toList());
            is.setItemMeta(meta);
        }
        return is;
    }
}

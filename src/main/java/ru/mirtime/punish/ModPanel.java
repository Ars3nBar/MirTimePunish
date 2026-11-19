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
    private final Map<Integer, ReportEntry> slotMap = new HashMap<>();

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

    public ModPanel(MirTimePunish plugin) {
        this.plugin = plugin;
    }

    public boolean isSpec(UUID uuid) {
        return specReturnLoc.containsKey(uuid);
    }

    public void createReport(Player staff, String targetName, String reason) {
        ReportEntry entry = new ReportEntry(staff.getName(), targetName, reason);
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

    public void toggleSpec(Player mod, Player target) {
        UUID id = mod.getUniqueId();

        if (specReturnLoc.containsKey(id)) {
            Location ret = specReturnLoc.remove(id);
            if (ret != null) {
                mod.teleport(ret);
            }
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
        Inventory inv = Bukkit.createInventory(null, 54, MirTimePunish.color("&#0055ff&l⚡ Панель модерации &8(&fЖалоб: &#00FF66" + reports.size() + "&8)"));

        ItemStack blue = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta bMeta = blue.getItemMeta();
        if (bMeta != null) {
            bMeta.setDisplayName(MirTimePunish.color("&#0055ff▪"));
            blue.setItemMeta(bMeta);
        }

        int[] border = {0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53};
        for (int s : border) {
            inv.setItem(s, blue);
        }

        ItemStack exitSpec = new ItemStack(Material.ENDER_EYE);
        ItemMeta eMeta = exitSpec.getItemMeta();
        if (eMeta != null) {
            eMeta.setDisplayName(MirTimePunish.color("&#00E5FF&l[👁] Выйти из слежки (/spec)"));
            exitSpec.setItemMeta(eMeta);
        }
        inv.setItem(4, exitSpec);

        slotMap.clear();
        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        int limit = Math.min(reports.size(), slots.length);
        for (int i = 0; i < limit; i++) {
            ReportEntry r = reports.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(r.target));
                meta.setDisplayName(MirTimePunish.color("&c&lНарушитель: &f" + r.target + " &8[" + r.time + "]"));
                meta.setLore(List.of(
                        MirTimePunish.color("&#0055ff● &fОтправил: &#00E5FF" + r.sender),
                        MirTimePunish.color("&#0055ff● &fПричина: &e" + r.reason),
                        "",
                        MirTimePunish.color("&#00FF66[ЛКМ] &fТелепорт и скрытая слежка (/spec)"),
                        MirTimePunish.color("&#00E5FF[ПКМ] &fВызвать на проверку читов (/check)"),
                        MirTimePunish.color("&c[ШИФТ+ПКМ] &fЗакрыть и удалить жалобу")
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
        int slot = e.getRawSlot();

        if (slot == 4) {
            p.closeInventory();
            toggleSpec(p, null);
            return;
        }

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
}

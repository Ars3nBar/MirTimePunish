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

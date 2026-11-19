package ru.mirtime.punish;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MirTimePunish extends JavaPlugin implements CommandExecutor, Listener {

    private final Map<UUID, String> punishTargets = new HashMap<>();
    private final Map<UUID, Integer> punishPages = new HashMap<>();
    private final Map<Integer, String> slotToTemplate = new HashMap<>();
    private File dbFile;
    private YamlConfiguration db;
    private ModPanel modPanel;
    private CheckArenaManager checkArena;

    public CheckArenaManager getCheckArena() { return checkArena; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dbFile = new File(getDataFolder(), "punishments.yml");
        if (!dbFile.exists()) {
            try { dbFile.createNewFile(); } catch (IOException ignored) {}
        }
        db = YamlConfiguration.loadConfiguration(dbFile);
        modPanel = new ModPanel(this);
        checkArena = new CheckArenaManager(this);
        TabHelper tabHelper = new TabHelper(this);

        for (String cmd : List.of("punish", "check", "report", "reports", "spec", "ban", "mute", "unban", "unmute")) {
            if (getCommand(cmd) != null) {
                getCommand(cmd).setExecutor(this);
                getCommand(cmd).setTabCompleter(tabHelper);
            }
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(modPanel, this);
        Bukkit.getPluginManager().registerEvents(checkArena, this);
        getLogger().info("MirTimePunish успешно запущен!");
    }

    private void saveDb() {
        try { db.save(dbFile); } catch (IOException ignored) {}
    }

    private String getPrefix(CommandSender sender) {
        if (sender instanceof Player p) {
            try {
                if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                    Method m = papi.getMethod("setPlaceholders", Player.class, String.class);
                    String res = (String) m.invoke(null, p, "%luckperms_prefix%");
                    if (res != null && !res.equals("%luckperms_prefix%")) return res;
                }
            } catch (Exception ignored) {}
        }
        return "&#00E5FF[Персонал] ";
    }
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String cName = cmd.getName().toLowerCase();

        if (cName.equals("report")) {
            if (!(sender instanceof Player p)) return true;
            if (args.length < 2) {
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИспользование: &#00FF66/report <ник> <причина>"));
                return true;
            }
            String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            modPanel.createReport(p, List.of(args).get(0), reason);
            return true;
        }

        if (cName.equals("reports")) {
            if (sender instanceof Player p) modPanel.openReportsMenu(p);
            return true;
        }

        if (cName.equals("spec")) {
            if (sender instanceof Player p) {
                Player t = args.length > 0 ? Bukkit.getPlayer(List.of(args).get(0)) : null;
                modPanel.toggleSpec(p, t);
            }
            return true;
        }

        if (cName.equals("punish")) {
            if (args.length > 0 && List.of(args).get(0).equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("mirtime.punish.reload")) {
                    sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cУ вас нет права mirtime.punish.reload!"));
                    return true;
                }
                reloadConfig();
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &#00FF66Конфиг шаблонов config.yml успешно перезагружен!"));
                return true;
            }
            if (!(sender instanceof Player p)) return true;
            if (args.length < 1) {
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИспользование: &#00FF66/punish <ник> &7или &#00E5FF/punish reload"));
                return true;
            }
            punishTargets.put(p.getUniqueId(), List.of(args).get(0));
            punishPages.put(p.getUniqueId(), 0);
            openPunishMenu(p, List.of(args).get(0));
            return true;
        }

        if (cName.equals("check")) {
            if (!(sender instanceof Player p)) return true;
            if (args.length < 1) {
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИспользование: &#00FF66/check <ник>"));
                return true;
            }
            Player target = Bukkit.getPlayer(List.of(args).get(0));
            if (target == null) {
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cИгрок не в сети."));
                return true;
            }
            if (target.hasPermission("mirtime.punish.bypass")) {
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cВы не можете вызвать этого сотрудника на проверку!"));
                return true;
            }
            checkArena.toggleCheck(p, target);
            return true;
        }

        if (cName.equals("ban") || cName.equals("mute")) {
            if (args.length < 2) {
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИспользование: &#00FF66/" + cName + " <ник> <статья> [срок]"));
                return true;
            }
            String target = List.of(args).get(0);
            String lastArg = List.of(args).get(args.length - 1);
            boolean hasDur = args.length >= 3 && (lastArg.equalsIgnoreCase("perm") || lastArg.matches("(?i)^\\d+[mhd]$"));
            String reason = hasDur
                    ? String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1))
                    : String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            long dur = hasDur ? parseDuration(lastArg) : findDurationByReason(reason);
            applyPunishment(sender, target, cName.equals("ban"), dur, reason);
            return true;
        }

        if (cName.equals("unban")) {
            if (args.length < 1) return true;
            String t = List.of(args).get(0);
            db.set("bans." + t.toLowerCase(), null);
            saveDb();
            Bukkit.broadcastMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИгрок &#00FF66" + t + " &fбыл разбанен " + getPrefix(sender) + "&#00FF66" + sender.getName()));
            return true;
        }

        if (cName.equals("unmute")) {
            if (args.length < 1) return true;
            String t = List.of(args).get(0);
            db.set("mutes." + t.toLowerCase(), null);
            saveDb();
            Bukkit.broadcastMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИгрок &#00FF66" + t + " &fбыл размучен " + getPrefix(sender) + "&#00FF66" + sender.getName()));
            return true;
        }
        return true;
    }

    public void applyPunishment(CommandSender sender, String targetName, boolean isBan, long durationMs, String reason) {
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null && online.hasPermission("mirtime.punish.bypass")) {
            sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cУ этого игрока иммунитет к наказаниям (mirtime.punish.bypass)!"));
            return;
        }
        String key = (isBan ? "bans." : "mutes.") + targetName.toLowerCase();
        long until = durationMs == -1L ? -1L : System.currentTimeMillis() + durationMs;
        db.set(key + ".until", until);
        db.set(key + ".reason", reason);
        db.set(key + ".admin", sender.getName());
        saveDb();

        String timeText = durationMs == -1L ? "Навсегда" : formatTime(durationMs);
        String badge = isBan ? "&c&l[БАН]" : "&#00E5FF&l[МУТ]";
        String act = isBan ? "&cзабанил" : "&#00E5FFзамутил";

        Bukkit.broadcastMessage(color("&#0055ff&l&m                                                  "));
        Bukkit.broadcastMessage(color(" &#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» " + badge + " " + getPrefix(sender) + "&#00FF66" + sender.getName() + " " + act + " &fигрока &#00FF66" + targetName));
        Bukkit.broadcastMessage(color(" &#0055ff● &fСтатья: &#00E5FF" + reason));
        Bukkit.broadcastMessage(color(" &#0055ff● &fСрок наказания: &#00FF66" + timeText));
        Bukkit.broadcastMessage(color("&#0055ff&l&m                                                  "));

        if (isBan && online != null) {
            if (checkArena.isSuspect(online.getUniqueId())) checkArena.endCheck(online.getUniqueId(), true);
            online.kickPlayer(buildBanScreen(reason, sender.getName(), until));
        }
        if (sender instanceof Player p) p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }
    private String buildBanScreen(String reason, String admin, long until) {
        String dateStr = until == -1L ? "Никогда (Перманентно)" : new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(until));
        String site = getConfig().getString("site-link", "don.mir-time.ru");
        return color("&#0055ff&l⚡ &#00E5FF&lСЕРВЕР MIR-TIME &#0055ff&l⚡\n\n&c&lВАШ АККАУНТ ЗАБЛОКИРОВАН!\n\n&fСтатья: &#00E5FF" + reason
                + "\n&fВыдал: &#00FF66" + admin + "\n&fДата разбана: &#00FF66" + dateStr + "\n\n&7Купить разбан или подать апелляцию:\n&#00E5FF&n" + site);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent e) {
        String key = "bans." + e.getPlayer().getName().toLowerCase();
        if (db.contains(key)) {
            long until = db.getLong(key + ".until");
            if (until == -1L || System.currentTimeMillis() < until) {
                e.disallow(PlayerLoginEvent.Result.KICK_BANNED, buildBanScreen(db.getString(key + ".reason", "Нарушение правил"), db.getString(key + ".admin", "Администрация"), until));
            } else {
                db.set(key, null);
                saveDb();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPaperChat(AsyncChatEvent e) {
        if (checkMuted(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().toLowerCase();
        if ((msg.startsWith("/msg ") || msg.startsWith("/tell ") || msg.startsWith("/w ") || msg.startsWith("/m ") || msg.startsWith("/r ")) && checkMuted(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    private boolean checkMuted(Player p) {
        String key = "mutes." + p.getName().toLowerCase();
        if (!db.contains(key)) return false;
        long until = db.getLong(key + ".until");
        if (until != -1L && System.currentTimeMillis() >= until) {
            db.set(key, null);
            saveDb();
            return false;
        }
        String rem = until == -1L ? "Навсегда" : formatTime(until - System.currentTimeMillis());
        p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cУ вас мут! &fСтатья: &#00E5FF" + db.getString(key + ".reason") + " &8(&fОсталось: &#00FF66" + rem + "&8)"));
        return true;
    }

    public void openPunishMenu(Player p, String target) {
        int page = punishPages.getOrDefault(p.getUniqueId(), 0);
        Inventory inv = Bukkit.createInventory(null, 54, color("&#0055ff&l⚡ Наказание: &#00FF66" + target + " &8[Стр. " + (page + 1) + "]"));
        ItemStack blue = item(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff▪", null);
        ItemStack cyan = item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&#00E5FF▫", null);
        for (int s : new int[]{0, 1, 7, 8, 9, 17, 36, 44, 45, 46, 52, 53}) inv.setItem(s, blue);
        for (int s : new int[]{2, 3, 4, 5, 6, 18, 26, 27, 35, 47, 48, 49, 50, 51}) inv.setItem(s, cyan);

        slotToTemplate.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("templates");
        if (sec != null) {
            List<String> keys = new ArrayList<>(sec.getKeys(false));
            int[] grid = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};
            int start = page * 28;
            for (int i = 0; i < 28 && (start + i) < keys.size(); i++) {
                String k = keys.get(start + i);
                Material mat = Material.matchMaterial(getConfig().getString("templates." + k + ".material", "PAPER"));
                if (mat == null) mat = Material.PAPER;
                inv.setItem(grid[i], item(mat, getConfig().getString("templates." + k + ".name", k), getConfig().getStringList("templates." + k + ".lore")));
                slotToTemplate.put(grid[i], k);
            }
            if (page > 0) inv.setItem(48, item(Material.ARROW, "&#00E5FF&l[◀ Предыдущая страница]", null));
            if (start + 28 < keys.size()) inv.setItem(50, item(Material.ARROW, "&#00FF66&l[Следующая страница ▶]", null));
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getView().getTitle().contains("Наказание:")) return;
        e.setCancelled(true);
        String target = punishTargets.get(p.getUniqueId());
        if (target == null) return;
        int slot = e.getRawSlot();
        int page = punishPages.getOrDefault(p.getUniqueId(), 0);
        if (slot == 48 && page > 0) { punishPages.put(p.getUniqueId(), page - 1); openPunishMenu(p, target); return; }
        if (slot == 50 && e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.ARROW) { punishPages.put(p.getUniqueId(), page + 1); openPunishMenu(p, target); return; }
        String k = slotToTemplate.get(slot);
        if (k == null) return;
        boolean isBan = getConfig().getString("templates." + k + ".type", "BAN").equalsIgnoreCase("BAN");
        long dur = parseDuration(getConfig().getString("templates." + k + ".duration", "1d"));
        String reason = getConfig().getString("templates." + k + ".reason", k);
        p.closeInventory();
        applyPunishment(p, target, isBan, dur, reason);
    }

    private long findDurationByReason(String reason) {
        ConfigurationSection sec = getConfig().getConfigurationSection("templates");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                if (getConfig().getString("templates." + k + ".reason", "").equalsIgnoreCase(reason)) {
                    return parseDuration(getConfig().getString("templates." + k + ".duration", "1d"));
                }
            }
        }
        return -1L;
    }

    public static long parseDuration(String input) {
        if (input == null || input.equalsIgnoreCase("perm") || input.equalsIgnoreCase("forever")) return -1L;
        try {
            char unit = Character.toLowerCase(input.charAt(input.length() - 1));
            long val = Long.parseLong(input.substring(0, input.length() - 1));
            return switch (unit) {
                case 'm' -> val * 60_000L;
                case 'h' -> val * 3_600_000L;
                case 'd' -> val * 86_400_000L;
                default -> -1L;
            };
        } catch (Exception e) { return -1L; }
    }

    public static String formatTime(long ms) {
        long days = ms / 86_400_000L;
        long hours = (ms % 86_400_000L) / 3_600_000L;
        long mins = (ms % 3_600_000L) / 60_000L;
        if (days > 0) return days + " дн. " + hours + " ч.";
        if (hours > 0) return hours + " ч. " + mins + " мин.";
        return Math.max(1, mins) + " мин.";
    }

    public static ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> list = new ArrayList<>();
                for (String s : lore) list.add(color(s));
                meta.setLore(list);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            is.setItemMeta(meta);
        }
        return is;
    }

    public static String color(String text) {
        if (text == null) return "";
        Matcher matcher = Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            StringBuilder rep = new StringBuilder("§x");
            for (char c : matcher.group(1).toCharArray()) rep.append('§').append(c);
            matcher.appendReplacement(buffer, rep.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}

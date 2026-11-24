package ru.mirtime.punish;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
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
    private CheckManager checkManager;
    private ChatFilter chatFilter;
    private OsManager osManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        dbFile = new File(getDataFolder(), "punishments.yml");
        if (!dbFile.exists()) {
            try { dbFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        db = YamlConfiguration.loadConfiguration(dbFile);

        this.modPanel = new ModPanel(this);
        this.checkManager = new CheckManager(this);
        this.chatFilter = new ChatFilter(this);
        this.osManager = new OsManager(this);

        TabHelper tabHelper = new TabHelper(this);
        List<String> commands = List.of(
                "punish", "check", "checktime", "report",
                "reports", "spec", "ban", "mute", "unban", "unmute", "acreport", "os"
        );
        for (String cmd : commands) {
            if (getCommand(cmd) != null) {
                getCommand(cmd).setExecutor(this);
                getCommand(cmd).setTabCompleter(tabHelper);
            }
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(modPanel, this);
        Bukkit.getPluginManager().registerEvents(checkManager, this);
        Bukkit.getPluginManager().registerEvents(chatFilter, this);
        Bukkit.getPluginManager().registerEvents(osManager, this);

        getLogger().info("MirTimePunish v2.1.0 (MT-AC вкладки + Фильтр по статьям + Опрос ОС) запущен!");
    }

    public ModPanel getModPanel() { return modPanel; }
    public CheckManager getCheckManager() { return checkManager; }
    public ChatFilter getChatFilter() { return chatFilter; }
    public OsManager getOsManager() { return osManager; }

    private void saveDb() {
        try { db.save(dbFile); } catch (IOException e) { e.printStackTrace(); }
    }

    private String getPrefix(CommandSender sender) {
        if (sender instanceof Player p) {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                try {
                    Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                    Method setPlaceholders = papi.getMethod("setPlaceholders", Player.class, String.class);
                    String prefix = (String) setPlaceholders.invoke(null, p, "%luckperms_prefix%");
                    if (prefix != null && !prefix.equals("%luckperms_prefix%") && !prefix.isEmpty()) return prefix;
                } catch (Exception ignored) {}
            }
            return "&#00E5FF[Персонал] ";
        }
        return "&4&l[Консоль] ";
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        String arg0 = args.length > 0 ? args[0] : "";
        String arg1 = args.length > 1 ? args[1] : "";

        if (name.equals("os")) {
            if (sender instanceof Player p) osManager.openOsMenu(p);
            return true;
        }

        if (name.equals("acreport")) {
            if (args.length >= 2) {
                String details = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                modPanel.createSystemReport("MT-AC v3.0", arg0, details);
            }
            return true;
        }

        if (name.equals("report")) {
            if (!(sender instanceof Player p)) return true;
            if (args.length < 2) {
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИспользование: &#00FF66/report <ник> <причина>"));
                return true;
            }
            String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            modPanel.createReport(p, arg0, reason);
            return true;
        }

        if (name.equals("reports")) {
            if (!(sender instanceof Player p)) return true;
            modPanel.openReportsMenu(p);
            return true;
        }

        if (name.equals("spec")) {
            if (!(sender instanceof Player p)) return true;
            Player target = args.length > 0 ? Bukkit.getPlayer(arg0) : null;
            modPanel.toggleSpec(p, target);
            return true;
        }

        if (name.equals("punish")) {
            if (args.length == 1 && arg0.equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("mirtime.punish.reload")) {
                    sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cУ вас нет права mirtime.punish.reload!"));
                    return true;
                }
                reloadConfig();
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &#00FF66Конфиг config.yml успешно перезагружен!"));
                return true;
            }
            if (!(sender instanceof Player p)) return true;
            if (args.length < 1) {
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИспользование: &#00FF66/punish <ник> &7или &#00E5FF/punish reload"));
                return true;
            }
            punishTargets.put(p.getUniqueId(), arg0);
            punishPages.put(p.getUniqueId(), 0);
            openPunishMenu(p, arg0);
            return true;
        }

        if (name.equals("check")) {
            if (!(sender instanceof Player p)) return true;
            if (args.length < 1) {
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИспользование: &#00FF66/check <ник>"));
                return true;
            }
            Player target = Bukkit.getPlayer(arg0);
            if (target == null) {
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cИгрок не в сети."));
                return true;
            }
            if (target.hasPermission("mirtime.punish.bypass")) {
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cВы не можете вызвать этого сотрудника на проверку!"));
                return true;
            }
            checkManager.toggleCheck(p, target);
            return true;
        }

        if (name.equals("checktime")) {
            if (!(sender instanceof Player p)) return true;
            if (args.length < 2) {
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИспользование: &#00FF66/checktime <ник> <минуты> &7(макс. 25 мин всего)"));
                return true;
            }
            try {
                checkManager.addCheckTime(p, arg0, Integer.parseInt(arg1));
            } catch (NumberFormatException ex) {
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cУкажите целое число минут!"));
            }
            return true;
        }

        if (name.equals("ban") || name.equals("mute")) {
            boolean isBan = name.equals("ban");
            if (args.length < 2) {
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИспользование: &#00FF66/" + name + " <ник> <статья> [срок]"));
                return true;
            }
            String lastArg = args[args.length - 1];
            boolean hasDuration = args.length >= 3 && (lastArg.equalsIgnoreCase("perm") || lastArg.matches("(?i)^\\d+[smhd]$"));

            String reason;
            long duration;
            if (hasDuration) {
                reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
                duration = parseDuration(lastArg);
            } else {
                reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                duration = findDurationByReason(reason);
            }
            applyPunishment(sender, arg0, isBan, duration, reason);
            return true;
        }

        if (name.equals("unban")) {
            if (args.length < 1) return true;
            db.set("bans." + arg0.toLowerCase(), null);
            saveDb();
            Bukkit.broadcastMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИгрок &#00FF66" + arg0 + " &fбыл разбанен " + getPrefix(sender) + "&#00E5FF" + sender.getName()));
            return true;
        }

        if (name.equals("unmute")) {
            if (args.length < 1) return true;
            db.set("mutes." + arg0.toLowerCase(), null);
            saveDb();
            Player onlineTarget = Bukkit.getPlayerExact(arg0);
            if (onlineTarget != null && chatFilter != null) chatFilter.resetWarnings(onlineTarget.getUniqueId());
            Bukkit.broadcastMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИгрок &#00FF66" + arg0 + " &fбыл размучен " + getPrefix(sender) + "&#00E5FF" + sender.getName()));
            return true;
        }
        return true;
    }
    public void applyPunishment(CommandSender sender, String targetName, boolean isBan, long durationMs, String reason) {
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget != null && onlineTarget.hasPermission("mirtime.punish.bypass")) {
            sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cУ этого игрока иммунитет к наказаниям (mirtime.punish.bypass)!"));
            return;
        }

        if (onlineTarget != null && checkManager != null && checkManager.isFrozen(onlineTarget.getUniqueId())) {
            checkManager.finishCheck(onlineTarget.getUniqueId(), false);
        }

        String adminDisplay = getPrefix(sender) + sender.getName();

        if (isBan && durationMs == 0L) {
            Bukkit.broadcastMessage(color("&#0055ff&l&m                                                  "));
            Bukkit.broadcastMessage(color(" &#00FF66&l[ПРЕДУПРЕЖДЕНИЕ] &#00E5FF&lMIR-TIME &8» " + adminDisplay + " &fвыдал предупреждение игроку &#00FF66" + targetName));
            Bukkit.broadcastMessage(color(" &#0055ff● &fСтатья: &#00E5FF" + reason));
            Bukkit.broadcastMessage(color("&#0055ff&l&m                                                  "));

            if (onlineTarget != null && onlineTarget.isOnline()) {
                onlineTarget.kickPlayer(color(
                        "&#0055ff&l⚡ &#00E5FF&lСЕРВЕР MIR-TIME &#0055ff&l⚡\n\n" +
                        "&#00FF66&lВЫ ПОЛУЧИЛИ ПРЕДУПРЕЖДЕНИЕ!\n\n" +
                        "&fСтатья: &#00E5FF" + reason + "\n" +
                        "&fВыдал: &#00FF66" + sender.getName() + "\n\n" +
                        "&7Ознакомьтесь с правилами: &#00E5FF&n" + getConfig().getString("site-link", "don.mir-time.ru") + "/rules"
                ));
            }
            return;
        }

        String key = (isBan ? "bans." : "mutes.") + targetName.toLowerCase();
        long until = (durationMs == -1L) ? -1L : System.currentTimeMillis() + durationMs;

        db.set(key + ".until", until);
        db.set(key + ".reason", reason);
        db.set(key + ".admin", sender.getName());
        saveDb();

        String timeText = (durationMs == -1L) ? "Навсегда" : formatTime(durationMs);
        String badge = isBan ? "&c&l[БАН]" : "&#00E5FF&l[МУТ]";
        String actionText = isBan ? "&cзабанил" : "&#00E5FFзамутил";

        Bukkit.broadcastMessage(color("&#0055ff&l&m                                                  "));
        Bukkit.broadcastMessage(color(" " + badge + " &#00E5FF&lMIR-TIME &8» " + adminDisplay + " " + actionText + " &fигрока &#00FF66" + targetName));
        Bukkit.broadcastMessage(color(" &#0055ff● &fСтатья: &#00E5FF" + reason));
        Bukkit.broadcastMessage(color(" &#0055ff● &fСрок наказания: &#00FF66" + timeText));
        Bukkit.broadcastMessage(color("&#0055ff&l&m                                                  "));

        if (onlineTarget != null && onlineTarget.isOnline()) {
            if (isBan) {
                onlineTarget.kickPlayer(buildBanScreen(reason, sender.getName(), until));
            } else {
                onlineTarget.playSound(onlineTarget.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }
    }

    private String buildBanScreen(String reason, String admin, long until) {
        String dateStr = (until == -1L) ? "Никогда (Перманентно)" : new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(until));
        String site = getConfig().getString("site-link", "don.mir-time.ru");
        return color(
                "&#0055ff&l⚡ &#00E5FF&lСЕРВЕР MIR-TIME &#0055ff&l⚡\n\n" +
                "&c&lВАШ АККАУНТ ЗАБЛОКИРОВАН!\n\n" +
                "&fСтатья: &#00E5FF" + reason + "\n" +
                "&fВыдал: &#00FF66" + admin + "\n" +
                "&fДата разбана: &#00FF66" + dateStr + "\n\n" +
                "&7Купить разбан или подать апелляцию:\n" +
                "&#00E5FF&n" + site
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent e) {
        Player p = e.getPlayer();
        String key = "bans." + p.getName().toLowerCase();
        if (!db.contains(key)) return;

        long until = db.getLong(key + ".until");
        if (until != -1L && System.currentTimeMillis() > until) {
            db.set(key, null);
            saveDb();
            return;
        }
        e.disallow(PlayerLoginEvent.Result.KICK_BANNED, buildBanScreen(
                db.getString(key + ".reason", "Нарушение правил"),
                db.getString(key + ".admin", "Администрация"),
                until
        ));
    }

    private boolean checkMuted(Player p) {
        String key = "mutes." + p.getName().toLowerCase();
        if (!db.contains(key)) return false;

        long until = db.getLong(key + ".until");
        if (until != -1L && System.currentTimeMillis() > until) {
            db.set(key, null);
            saveDb();
            return false;
        }
        String rem = (until == -1L) ? "Навсегда" : formatTime(until - System.currentTimeMillis());
        p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cУ вас мут! &fСтатья: &#00E5FF" + db.getString(key + ".reason") + " &8(&fОсталось: &#00FF66" + rem + "&8)"));
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPaperChat(AsyncChatEvent e) { if (checkMuted(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLegacyChat(AsyncPlayerChatEvent e) { if (checkMuted(e.getPlayer())) e.setCancelled(true); }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().toLowerCase();
        if (msg.startsWith("/msg ") || msg.startsWith("/tell ") || msg.startsWith("/w ") || msg.startsWith("/m ") || msg.startsWith("/r ")) {
            if (checkMuted(e.getPlayer())) e.setCancelled(true);
        }
    }
    public void openPunishMenu(Player mod, String targetName) {
        int page = punishPages.getOrDefault(mod.getUniqueId(), 0);
        Inventory inv = Bukkit.createInventory(null, 54, color("&#0055ff&l⚡ Наказание: &#00FF66" + targetName + " &8[Стр. " + (page + 1) + "]"));

        ItemStack blue = item(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff▪", null);
        ItemStack cyan = item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&#00E5FF▪", null);

        int[] borderBlue = {0, 2, 4, 6, 8, 9, 17, 18, 26, 27, 35, 36, 44, 46, 48, 50, 52};
        for (int s : borderBlue) inv.setItem(s, blue);
        int[] borderCyan = {1, 3, 5, 7, 45, 47, 49, 51, 53};
        for (int s : borderCyan) inv.setItem(s, cyan);

        slotToTemplate.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("templates");
        if (sec != null) {
            List<String> keys = new ArrayList<>(sec.getKeys(false));
            int[] grid = {
                    10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25,
                    28, 29, 30, 31, 32, 33, 34,
                    37, 38, 39, 40, 41, 42, 43
            };
            int perPage = grid.length;
            int start = page * perPage;

            for (int i = 0; i < perPage && (start + i) < keys.size(); i++) {
                String key = keys.get(start + i);
                int slot = grid[i];
                Material mat = Material.matchMaterial(sec.getString(key + ".material", "PAPER"));
                if (mat == null) mat = Material.PAPER;

                inv.setItem(slot, item(mat, sec.getString(key + ".name"), sec.getStringList(key + ".lore")));
                slotToTemplate.put(slot, key);
            }

            if (page > 0) inv.setItem(48, item(Material.ARROW, "&#00E5FF&l[◀ Предыдущая страница]", null));
            if (start + perPage < keys.size()) inv.setItem(50, item(Material.ARROW, "&#00FF66&l[Следующая страница ▶]", null));
        }
        mod.openInventory(inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player mod)) return;
        if (!e.getView().getTitle().contains("Наказание:")) return;

        e.setCancelled(true);
        String target = punishTargets.get(mod.getUniqueId());
        if (target == null) return;

        int slot = e.getRawSlot();
        int page = punishPages.getOrDefault(mod.getUniqueId(), 0);

        if (slot == 48 && page > 0) {
            punishPages.put(mod.getUniqueId(), page - 1);
            openPunishMenu(mod, target);
            return;
        }
        if (slot == 50 && e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.ARROW) {
            punishPages.put(mod.getUniqueId(), page + 1);
            openPunishMenu(mod, target);
            return;
        }

        String key = slotToTemplate.get(slot);
        if (key == null) return;

        String path = "templates." + key;
        String typeStr = getConfig().getString(path + ".type", "BAN");
        boolean isBan = typeStr.equalsIgnoreCase("BAN") || typeStr.equalsIgnoreCase("KICK");
        long dur = typeStr.equalsIgnoreCase("KICK") ? 0L : parseDuration(getConfig().getString(path + ".duration", "1d"));
        String reason = getConfig().getString(path + ".reason", "Нарушение правил");

        mod.closeInventory();
        applyPunishment(mod, target, isBan, dur, reason);
    }

    private long findDurationByReason(String reason) {
        ConfigurationSection sec = getConfig().getConfigurationSection("templates");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                String r = sec.getString(key + ".reason", "");
                if (r.equalsIgnoreCase(reason)) {
                    String type = sec.getString(key + ".type", "BAN");
                    if (type.equalsIgnoreCase("KICK")) return 0L;
                    return parseDuration(sec.getString(key + ".duration", "1d"));
                }
            }
        }
        return -1L;
    }

    public long parseDuration(String input) {
        if (input == null || input.equalsIgnoreCase("perm") || input.equalsIgnoreCase("forever")) return -1L;
        try {
            char unit = Character.toLowerCase(input.charAt(input.length() - 1));
            long val = Long.parseLong(input.substring(0, input.length() - 1));
            return switch (unit) {
                case 's' -> val * 1000L;
                case 'm' -> val * 60000L;
                case 'h' -> val * 3600000L;
                case 'd' -> val * 86400000L;
                default -> -1L;
            };
        } catch (Exception e) {
            return -1L;
        }
    }

    private String formatTime(long ms) {
        long days = ms / 86400000L;
        long hours = (ms % 86400000L) / 3600000L;
        long mins = Math.max(1L, (ms % 3600000L) / 60000L);
        if (days > 0) return days + " дн. " + hours + " ч.";
        if (hours > 0) return hours + " ч. " + mins + " мин.";
        return mins + " мин.";
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
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            is.setItemMeta(meta);
        }
        return is;
    }

    public static String color(String text) {
        if (text == null) return "";
        Pattern pattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder rep = new StringBuilder("§x");
            for (char c : hex.toCharArray()) rep.append('§').append(c);
            matcher.appendReplacement(buffer, rep.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}

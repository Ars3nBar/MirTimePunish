package ru.mirtime.punish;

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
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
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

    private File dbFile;
    private YamlConfiguration db;
    private ModPanel modPanel;
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Map<UUID, String> punishTargets = new HashMap<>();
    private final Map<Integer, String> slotToTemplate = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dbFile = new File(getDataFolder(), "punishments.yml");
        if (!dbFile.exists()) {
            try { dbFile.createNewFile(); } catch (IOException ignored) {}
        }
        db = YamlConfiguration.loadConfiguration(dbFile);
        modPanel = new ModPanel(this);

        TabHelper tabHelper = new TabHelper(this);
        for (String cmd : List.of("punish", "check", "report", "reports", "spec", "ban", "mute", "unban", "unmute")) {
            if (getCommand(cmd) != null) {
                getCommand(cmd).setExecutor(this);
                getCommand(cmd).setTabCompleter(tabHelper);
            }
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(modPanel, this);
        getLogger().info("MirTimePunish загружен!");
    }


    private void saveDb() {
        try { db.save(dbFile); } catch (IOException ignored) {}
    }

    private String getPrefix(CommandSender sender) {
        if (sender instanceof Player p) {
            try {
                if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                    Method setPlaceholders = papi.getMethod("setPlaceholders", Player.class, String.class);
                    String prefix = (String) setPlaceholders.invoke(null, p, "%luckperms_prefix%");
                    if (prefix != null && !prefix.equals("%luckperms_prefix%")) return prefix;
                }
            } catch (Exception ignored) {}
        }
        return "&#00E5FF[Персонал] ";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase();

        if (name.equals("report")) {
            if (!(sender instanceof Player p)) return true;
            if (args.length < 2) {
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИспользование: &#00FF66/report <ник> <причина>"));
                return true;
            }
            String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            modPanel.createReport(p, args[0], reason);
            return true;
        }

        if (name.equals("reports")) {
            if (sender instanceof Player p) modPanel.openReportsMenu(p);
            return true;
        }

        if (name.equals("spec")) {
            if (!(sender instanceof Player p)) return true;
            Player target = (args.length > 0) ? Bukkit.getPlayer(args[0]) : null;
            modPanel.toggleSpec(p, target);
            return true;
        }

        if (name.equals("punish")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("mirtime.punish.reload")) {
                    sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cУ вас нет права mirtime.punish.reload!"));
                    return true;
                }
                reloadConfig();
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &#00FF66Конфиг шаблонов config.yml успешно перезагружен!"));
                return true;
            }
            if (!(sender instanceof Player p)) return true;
            if (args.length == 0) {
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИспользование: &#00FF66/punish <ник> &7или &#00E5FF/punish reload"));
                return true;
            }
            punishTargets.put(p.getUniqueId(), args[0]);
            openPunishMenu(p, args[0]);
            return true;
        }

        if (name.equals("check")) {
            if (args.length == 0) {
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИспользование: &#00FF66/check <ник>"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cИгрок не в сети."));
                return true;
            }
            if (target.hasPermission("mirtime.punish.bypass")) {
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cВы не можете вызвать этого сотрудника на проверку!"));
                return true;
            }
            if (frozenPlayers.contains(target.getUniqueId())) {
                frozenPlayers.remove(target.getUniqueId());
                target.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &#00FF66Проверка успешно пройдена! Вы разморожены."));
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fВы отпустили игрока &#00FF66" + target.getName() + "&f."));
            } else {
                frozenPlayers.add(target.getUniqueId());
                target.sendTitle(color("&c&lПРОВЕРКА НА ЧИТЫ!"), color("&fСкиньте Discord/Telegram в чат за &#00E5FF5 минут"), 10, 140, 20);
                target.playSound(target.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                target.sendMessage(color("&#0055ff&l&m                                                "));
                target.sendMessage(color(" &c&l⚠ ВЫ ВЫЗВАНЫ НА ПРОВЕРКУ ЧИТОВ (Пункт 2.1 - 2.3)"));
                target.sendMessage(color(" &fНапишите свой &#00E5FFDiscord &fили &#00E5FFTelegram &fв чат."));
                target.sendMessage(color(" &cВыход с сервера = автоматический БАН на 30 дней!"));
                target.sendMessage(color("&#0055ff&l&m                                                "));
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИгрок &#00FF66" + target.getName() + " &fзаморожен на проверку."));
            }
            return true;
        }

        if (name.equals("ban") || name.equals("mute")) {
            if (args.length < 3) {
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИспользование: &#00FF66/" + name + " <ник> <время: 30m/5h/14d/perm> <причина>"));
                return true;
            }
            long duration = parseDuration(args[1]);
            String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            applyPunishment(sender, args[0], name.equals("ban"), duration, reason);
            return true;
        }

        if (name.equals("unban")) {
            if (args.length == 0) return true;
            db.set("bans." + args[0].toLowerCase(), null);
            saveDb();
            Bukkit.broadcastMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИгрок &#00FF66" + args[0] + " &fбыл разбанен " + getPrefix(sender) + "&#00E5FF" + sender.getName()));
            return true;
        }

        if (name.equals("unmute")) {
            if (args.length == 0) return true;
            db.set("mutes." + args[0].toLowerCase(), null);
            saveDb();
            Bukkit.broadcastMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИгрок &#00FF66" + args[0] + " &fбыл размучен " + getPrefix(sender) + "&#00E5FF" + sender.getName()));
            return true;
        }
        return true;
    }
    private void applyPunishment(CommandSender sender, String targetName, boolean isBan, long durationMs, String reason) {
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget != null && onlineTarget.hasPermission("mirtime.punish.bypass")) {
            sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cУ этого игрока иммунитет к наказаниям (mirtime.punish.bypass)!"));
            return;
        }

        String adminDisplay = getPrefix(sender) + "&#00FF66" + sender.getName();
        String key = (isBan ? "bans." : "mutes.") + targetName.toLowerCase();
        long until = (durationMs == -1) ? -1 : System.currentTimeMillis() + durationMs;

        db.set(key + ".until", until);
        db.set(key + ".reason", reason);
        db.set(key + ".admin", sender.getName());
        saveDb();

        String timeText = (durationMs == -1) ? "Навсегда" : formatTime(durationMs);
        String actionText = isBan ? "&cзабанил" : "&#00E5FFзамутил";

        Bukkit.broadcastMessage(color("&#0055ff&l&m                                                "));
        Bukkit.broadcastMessage(color(" &#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» " + adminDisplay + " " + actionText + " &fигрока &#00FF66" + targetName));
        Bukkit.broadcastMessage(color(" &#0055ff● &fПричина: &#00E5FF" + reason + " &8(&fСрок: &#00FF66" + timeText + "&8)"));
        Bukkit.broadcastMessage(color("&#0055ff&l&m                                                "));

        if (onlineTarget != null) {
            frozenPlayers.remove(onlineTarget.getUniqueId());
            if (isBan) onlineTarget.kickPlayer(buildBanScreen(reason, sender.getName(), until));
        }
    }

    private String buildBanScreen(String reason, String admin, long until) {
        String dateStr = (until == -1) ? "Никогда (Перманентно)" : new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(until));
        String site = getConfig().getString("site-link", "don.mir-time.ru");
        return color("&#0055ff&l⚡ &#00E5FF&lСЕРВЕР MIR-TIME &#0055ff&l⚡\n\n" +
                "&cВаш аккаунт заблокирован!\n\n" +
                "&fПричина: &#00E5FF" + reason + "\n" +
                "&fВыдал: &#00FF66" + admin + "\n" +
                "&fРазбан: &#00FF66" + dateStr + "\n\n" +
                "&7Купить разбан или подать апелляцию:\n" +
                "&#00E5FF&n" + site);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent e) {
        String key = "bans." + e.getPlayer().getName().toLowerCase();
        if (!db.contains(key)) return;
        long until = db.getLong(key + ".until");
        if (until != -1 && System.currentTimeMillis() > until) {
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (frozenPlayers.contains(p.getUniqueId())) return;
        String key = "mutes." + p.getName().toLowerCase();
        if (!db.contains(key)) return;
        long until = db.getLong(key + ".until");
        if (until != -1 && System.currentTimeMillis() > until) {
            db.set(key, null);
            saveDb();
            return;
        }
        e.setCancelled(true);
        String rem = (until == -1) ? "Навсегда" : formatTime(until - System.currentTimeMillis());
        p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cУ вас мут! &fПричина: &#00E5FF" + db.getString(key + ".reason") + " &8(&fОсталось: &#00FF66" + rem + "&8)"));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (frozenPlayers.contains(e.getPlayer().getUniqueId())) {
            if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getZ() != e.getTo().getZ()) {
                e.setTo(e.getFrom());
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (frozenPlayers.remove(p.getUniqueId())) {
            applyPunishment(Bukkit.getConsoleSender(), p.getName(), true, 30L * 86400000L, "Пункт 2.1 - Выход с проверки на читы");
        }
    }

    private void openPunishMenu(Player admin, String target) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&#0055ff&l⚡ Наказание: &#00FF66" + target));
        ItemStack blue = item(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff▪", null);
        for (int s : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,49,50,51,52,53}) inv.setItem(s, blue);

        slotToTemplate.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("templates");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                int slot = sec.getInt(key + ".slot", -1);
                if (slot < 0 || slot >= 54) continue;
                Material mat = Material.matchMaterial(sec.getString(key + ".material", "PAPER"));
                if (mat == null) mat = Material.PAPER;
                String name = sec.getString(key + ".name", key);
                List<String> lore = sec.getStringList(key + ".lore");
                inv.setItem(slot, item(mat, name, lore));
                slotToTemplate.put(slot, key);
            }
        }
        admin.openInventory(inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getView().getTitle().contains("Наказание:")) return;
        e.setCancelled(true);

        String target = punishTargets.get(p.getUniqueId());
        String key = slotToTemplate.get(e.getRawSlot());
        if (target == null || key == null) return;

        String path = "templates." + key;
        boolean isBan = getConfig().getString(path + ".type", "BAN").equalsIgnoreCase("BAN");
        long duration = parseDuration(getConfig().getString(path + ".duration", "1d"));
        String reason = getConfig().getString(path + ".reason", "Нарушение правил");

        applyPunishment(p, target, isBan, duration, reason);
        p.closeInventory();
    }

    private long parseDuration(String input) {
        if (input == null || input.equalsIgnoreCase("perm") || input.equalsIgnoreCase("forever")) return -1;
        try {
            char unit = Character.toLowerCase(input.charAt(input.length() - 1));
            long val = Long.parseLong(input.substring(0, input.length() - 1));
            return switch (unit) {
                case 'm' -> val * 60_000L;
                case 'h' -> val * 3_600_000L;
                case 'd' -> val * 86_400_000L;
                default -> 3_600_000L;
            };
        } catch (Exception e) {
            return 3_600_000L;
        }
    }

    private String formatTime(long ms) {
        long sec = ms / 1000;
        long days = sec / 86400;
        long hours = (sec % 86400) / 3600;
        long mins = (sec % 3600) / 60;
        if (days > 0) return days + " дн. " + hours + " ч.";
        if (hours > 0) return hours + " ч. " + mins + " мин.";
        return Math.max(1, mins) + " мин.";
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

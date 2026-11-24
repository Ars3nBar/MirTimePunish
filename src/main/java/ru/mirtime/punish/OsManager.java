package ru.mirtime.punish;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

public class OsManager implements Listener {

    private final MirTimePunish plugin;
    private final File file;
    private final YamlConfiguration data;

    // 30 минут в миллисекундах
    private static final long ASK_INTERVAL_MS = 30L * 60L * 1000L;

    public enum OsType {
        WINDOWS("Windows", "&#00E5FFWindows", Material.LIGHT_BLUE_STAINED_GLASS),
        LINUX("Linux", "&#00FF66Linux", Material.LIME_STAINED_GLASS),
        OTHER("Другие", "&eДругие (macOS / Телефон)", Material.YELLOW_STAINED_GLASS),
        UNKNOWN("Не указано", "&7Не указано", Material.GRAY_STAINED_GLASS);

        public final String plainName;
        public final String coloredName;
        public final Material icon;

        OsType(String plainName, String coloredName, Material icon) {
            this.plainName = plainName;
            this.coloredName = coloredName;
            this.icon = icon;
        }
    }

    public static class OsHolder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
        public void setInventory(Inventory inv) { this.inv = inv; }
    }

    public OsManager(MirTimePunish plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players_os.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try { data.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    public OsType getPlayerOs(String playerName) {
        String raw = data.getString("players." + playerName.toLowerCase() + ".os", "UNKNOWN");
        try {
            return OsType.valueOf(raw);
        } catch (Exception e) {
            return OsType.UNKNOWN;
        }
    }

    private String getIp(Player p) {
        InetSocketAddress addr = p.getAddress();
        return (addr != null && addr.getAddress() != null) ? addr.getAddress().getHostAddress() : "unknown";
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String key = "players." + p.getName().toLowerCase();
        String currentIp = getIp(p);
        String savedIp = data.getString(key + ".ip", "");
        long lastAsk = data.getLong(key + ".lastAsk", 0L);
        long now = System.currentTimeMillis();

        boolean ipChanged = !savedIp.equals(currentIp);
        boolean intervalPassed = (now - lastAsk) >= ASK_INTERVAL_MS;
        boolean notChosen = getPlayerOs(p.getName()) == OsType.UNKNOWN;

        if (ipChanged || intervalPassed || notChosen) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && !plugin.getCheckManager().isInCheck(p.getUniqueId())) {
                    openOsMenu(p);
                }
            }, 25L);
        }
    }

    public void openOsMenu(Player p) {
        OsHolder holder = new OsHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lС чего вы играете?"));
        holder.setInventory(inv);

        ItemStack border = makeItem(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff▪", null);
        for (int i = 0; i < 27; i++) inv.setItem(i, border);

        inv.setItem(11, makeItem(Material.LIGHT_BLUE_STAINED_GLASS, "&#00E5FF&l[Windows]", List.of(
                "&7Нажмите, если вы играете с ПК",
                "&7на операционной системе &fWindows&7."
        )));
        inv.setItem(13, makeItem(Material.LIME_STAINED_GLASS, "&#00FF66&l[Linux]", List.of(
                "&7Нажмите, если вы играете с ПК",
                "&7на &fLinux &7или &fSteam Deck&7."
        )));
        inv.setItem(15, makeItem(Material.YELLOW_STAINED_GLASS, "&e&l[Другие ОС]", List.of(
                "&7Нажмите, если вы играете с &fmacOS&7,",
                "&fAndroid (PojavLauncher)&7 или другой ОС."
        )));

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof OsHolder)) return;

        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        OsType chosen = switch (e.getRawSlot()) {
            case 11 -> OsType.WINDOWS;
            case 13 -> OsType.LINUX;
            case 15 -> OsType.OTHER;
            default -> null;
        };

        if (chosen == null) return;

        String key = "players." + p.getName().toLowerCase();
        data.set(key + ".os", chosen.name());
        data.set(key + ".ip", getIp(p));
        data.set(key + ".lastAsk", System.currentTimeMillis());
        save();

        p.closeInventory();
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
        p.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fСпасибо! Выбрана платформа: " + chosen.coloredName));
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MirTimePunish.color(name));
            if (lore != null) {
                meta.setLore(lore.stream().map(MirTimePunish::color).toList());
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            is.setItemMeta(meta);
        }
        return is;
    }
}

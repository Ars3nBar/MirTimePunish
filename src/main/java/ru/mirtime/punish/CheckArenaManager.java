package ru.mirtime.punish;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.ChunkGenerator;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CheckArenaManager implements Listener {

    private final MirTimePunish plugin;
    private World checkWorld;

    private final Map<UUID, UUID> suspectToMod = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> modToSuspect = new ConcurrentHashMap<>();
    private final Map<UUID, Location> savedLocations = new ConcurrentHashMap<>();

    public CheckArenaManager(MirTimePunish plugin) {
        this.plugin = plugin;
        setupCheckWorld();
    }

    private void setupCheckWorld() {
        checkWorld = Bukkit.getWorld("check_arena");
        if (checkWorld == null) {
            WorldCreator wc = new WorldCreator("check_arena");
            wc.environment(World.Environment.NORMAL);
            wc.type(WorldType.FLAT);
            wc.generateStructures(false);
            wc.generator(new ChunkGenerator() {});
            checkWorld = wc.createWorld();
        }
        if (checkWorld != null) {
            checkWorld.setDifficulty(Difficulty.PEACEFUL);
            checkWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            checkWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            checkWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            checkWorld.setTime(18000L);

            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    boolean border = (Math.abs(x) == 4 || Math.abs(z) == 4);
                    checkWorld.getBlockAt(x, 99, z).setType(border ? Material.BLUE_STAINED_GLASS : Material.BLACK_STAINED_GLASS);
                }
            }
        }
    }

    public boolean isInCheck(UUID uuid) {
        return suspectToMod.containsKey(uuid) || modToSuspect.containsKey(uuid);
    }

    public boolean isSuspect(UUID uuid) {
        return suspectToMod.containsKey(uuid);
    }

    public void toggleCheck(Player mod, Player target) {
        UUID tId = target.getUniqueId();
        UUID mId = mod.getUniqueId();

        if (suspectToMod.containsKey(tId)) {
            endCheck(tId, false);
            return;
        }

        if (modToSuspect.containsKey(mId)) {
            mod.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cВы уже проводите проверку другого игрока!"));
            return;
        }

        savedLocations.put(tId, target.getLocation().clone());
        savedLocations.put(mId, mod.getLocation().clone());

        suspectToMod.put(tId, mId);
        modToSuspect.put(mId, tId);

        if (checkWorld != null) {
            target.teleport(new Location(checkWorld, 0.5, 100.0, 2.5, 180f, 0f));
            mod.teleport(new Location(checkWorld, 0.5, 100.0, -2.5, 0f, 0f));
        }

        target.playSound(target.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
        target.sendTitle(MirTimePunish.color("&c&lПРОВЕРКА НА ЧИТЫ!"), MirTimePunish.color("&fСкиньте Discord/Telegram в чат за &#00E5FF5 минут"), 10, 100, 20);

        String line = MirTimePunish.color("&#0055ff&l&m                                                  ");
        target.sendMessage(line);
        target.sendMessage(MirTimePunish.color(" &c&l⚠ ВЫ ВЫЗВАНЫ НА ПРОВЕРКУ ЧИТОВ (Пункт 2.1 - 2.6)"));
        target.sendMessage(MirTimePunish.color(" &f1. Ваш обычный чат переключён в &bприватный диалог &fс модератором."));
        target.sendMessage(MirTimePunish.color(" &f2. Просто напишите ваш &#00E5FFDiscord &fили &#00E5FFTelegram &fв чат (без команд)."));
        target.sendMessage(MirTimePunish.color(" &cВыход с сервера = автоматический БАН на 14 дней!"));
        target.sendMessage(line);

        mod.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИгрок &#00FF66" + target.getName() + " &fвызван на проверку в мир &bcheck_arena&f. Чат изолирован!"));
    }

    public void endCheck(UUID suspectId, boolean leftServer) {
        UUID modId = suspectToMod.remove(suspectId);
        if (modId != null) modToSuspect.remove(modId);

        Player suspect = Bukkit.getPlayer(suspectId);
        Player mod = modId != null ? Bukkit.getPlayer(modId) : null;

        Location backSuspect = savedLocations.remove(suspectId);
        Location backMod = modId != null ? savedLocations.remove(modId) : null;

        if (mod != null && mod.isOnline()) {
            if (backMod != null) mod.teleport(backMod);
            if (!leftServer) {
                mod.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &aПроверка завершена. Вы возвращены на исходную точку."));
            }
        }

        if (!leftServer && suspect != null && suspect.isOnline()) {
            if (backSuspect != null) suspect.teleport(backSuspect);
            suspect.resetTitle();
            suspect.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &#00FF66Проверка успешно пройдена! Вы возвращены обратно."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent e) {
        Player sender = e.getPlayer();
        UUID sId = sender.getUniqueId();
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message());

        if (suspectToMod.containsKey(sId) || modToSuspect.containsKey(sId)) {
            e.setCancelled(true);
            boolean isMod = modToSuspect.containsKey(sId);
            UUID partnerId = isMod ? modToSuspect.get(sId) : suspectToMod.get(sId);
            Player partner = partnerId != null ? Bukkit.getPlayer(partnerId) : null;

            String formatted = isMod
                    ? MirTimePunish.color("&#0055ff[Проверка] &c&lМодератор " + sender.getName() + " &8» &f" + msg)
                    : MirTimePunish.color("&#0055ff[Проверка] &#00E5FF" + sender.getName() + " &8» &f" + msg);

            sender.sendMessage(formatted);
            if (partner != null && partner.isOnline()) partner.sendMessage(formatted);
            Bukkit.getConsoleSender().sendMessage(formatted);
            return;
        }

        e.viewers().removeIf(audience -> (audience instanceof Player p) && isInCheck(p.getUniqueId()));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (isSuspect(e.getPlayer().getUniqueId()) && e.hasChangedPosition()) {
            if (e.getTo().distanceSquared(e.getFrom()) > 0.01) e.setTo(e.getFrom());
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (isSuspect(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cКоманды заблокированы! Напишите ваш Discord/Telegram в обычный чат."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (suspectToMod.containsKey(id)) {
            endCheck(id, true);
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + p.getName() + " 14d Пункт 2.1 - Выход с проверки на читы")
            );
        } else if (modToSuspect.containsKey(id)) {
            UUID sId = modToSuspect.get(id);
            if (sId != null) endCheck(sId, false);
        }
    }

    @EventHandler public void onDrop(PlayerDropItemEvent e) { if (isSuspect(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p && isInCheck(p.getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onAttack(EntityDamageByEntityEvent e) { if (e.getDamager() instanceof Player p && isInCheck(p.getUniqueId())) e.setCancelled(true); }
}

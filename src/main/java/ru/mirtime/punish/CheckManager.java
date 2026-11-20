package ru.mirtime.punish;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class CheckManager implements Listener {

    private final MirTimePunish plugin;
    private World checkWorld;

    private final Map<UUID, CheckSession> activeChecks = new HashMap<>();
    private final Map<UUID, UUID> modToSuspect = new HashMap<>();
    private final Map<UUID, Long> offlineGraceEnd = new HashMap<>();

    public static class CheckSession {
        public final UUID suspectId;
        public final String suspectName;
        public final UUID modId;
        public final String modName;
        public final Location suspectReturnLoc;
        public final GameMode suspectReturnMode;
        public final Location modReturnLoc;
        public final GameMode modReturnMode;

        public final long startTimeMs;
        public long currentPhaseStartMs;
        public long expireTimeMs;
        public final long maxHardLimitMs;
        public final BossBar bossBar;

        public CheckSession(Player suspect, Player mod, Location susLoc, Location modLoc) {
            this.suspectId = suspect.getUniqueId();
            this.suspectName = suspect.getName();
            this.modId = mod.getUniqueId();
            this.modName = mod.getName();
            this.suspectReturnLoc = susLoc;
            this.suspectReturnMode = suspect.getGameMode();
            this.modReturnLoc = modLoc;
            this.modReturnMode = mod.getGameMode();

            this.startTimeMs = System.currentTimeMillis();
            this.currentPhaseStartMs = this.startTimeMs;
            this.expireTimeMs = this.startTimeMs + (5 * 60 * 1000L);
            this.maxHardLimitMs = this.startTimeMs + (25 * 60 * 1000L);

            this.bossBar = Bukkit.createBossBar(
                    MirTimePunish.color("&#0055ff&l⚡ ПРОВЕРКА НА ЧИТЫ &8| &fОсталось: &#00FF6605:00"),
                    BarColor.BLUE,
                    BarStyle.SEGMENTED_10
            );
            this.bossBar.addPlayer(suspect);
            this.bossBar.addPlayer(mod);
            this.bossBar.setVisible(true);
        }
    }

    public CheckManager(MirTimePunish plugin) {
        this.plugin = plugin;
        initCheckWorld();
        startTimerTask();
    }

    private void initCheckWorld() {
        String worldName = "mir_check";
        checkWorld = Bukkit.getWorld(worldName);
        if (checkWorld == null) {
            WorldCreator wc = new WorldCreator(worldName);
            wc.generator(new ChunkGenerator() {});
            wc.generateStructures(false);
            checkWorld = wc.createWorld();
        }
        if (checkWorld != null) {
            checkWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            checkWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            checkWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            checkWorld.setGameRule(GameRule.FALL_DAMAGE, false);
            checkWorld.setTime(6000L);

            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    Block floor = checkWorld.getBlockAt(x, 99, z);
                    floor.setType((Math.abs(x) == 4 || Math.abs(z) == 4) ? Material.BLUE_STAINED_GLASS : Material.SMOOTH_QUARTZ);
                    for (int y = 100; y <= 104; y++) {
                        Block wall = checkWorld.getBlockAt(x, y, z);
                        wall.setType((Math.abs(x) == 4 || Math.abs(z) == 4) ? Material.BARRIER : Material.AIR);
                    }
                    checkWorld.getBlockAt(x, 105, z).setType(Material.BARRIER);
                }
            }
        }
    }
    public boolean isFrozen(UUID uuid) { return activeChecks.containsKey(uuid); }
    public boolean isInCheck(UUID uuid) { return activeChecks.containsKey(uuid) || modToSuspect.containsKey(uuid); }

    public void toggleCheck(Player mod, Player target) {
        UUID targetId = target.getUniqueId();

        if (activeChecks.containsKey(targetId)) {
            finishCheck(targetId, true);
            mod.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fВы завершили проверку игрока &#00FF66" + target.getName() + "&f. Оба возвращены назад."));
            return;
        }

        if (modToSuspect.containsKey(mod.getUniqueId())) {
            mod.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cВы уже проводите проверку! Сначала завершите текущую."));
            return;
        }

        if (plugin.getModPanel().isSpec(mod.getUniqueId())) {
            plugin.getModPanel().toggleSpec(mod, null);
        }

        Location susOrigin = target.getLocation().clone();
        Location modOrigin = mod.getLocation().clone();

        CheckSession session = new CheckSession(target, mod, susOrigin, modOrigin);
        activeChecks.put(targetId, session);
        modToSuspect.put(mod.getUniqueId(), targetId);

        if (checkWorld != null) {
            target.teleport(new Location(checkWorld, 0.5, 100.0, 2.5, 180f, 0f));
            mod.teleport(new Location(checkWorld, 0.5, 100.0, -2.5, 0f, 0f));
        }

        target.setGameMode(GameMode.ADVENTURE);
        target.sendTitle(
                MirTimePunish.color("&c&lПРОВЕРКА НА ЧИТЫ!"),
                MirTimePunish.color("&fНапишите Discord/Telegram прямо в чат (5 мин)"),
                10, 120, 20
        );
        target.playSound(target.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);

        target.sendMessage(MirTimePunish.color("&#0055ff&l&m                                                  "));
        target.sendMessage(MirTimePunish.color(" &c&l⚠ ВЫ ВЫЗВАНЫ НА ПРОВЕРКУ ЧИТОВ (Раздел 2)"));
        target.sendMessage(MirTimePunish.color(" &fВы перенесены в комнату проверки. Общий чат отключён:"));
        target.sendMessage(MirTimePunish.color(" &fПросто напишите свой &#00E5FFDiscord &fили &#00E5FFTelegram &fв чат (без команд)."));
        target.sendMessage(MirTimePunish.color(" &7Макс. длительность проверки: 25 минут (п. 2.7)."));
        target.sendMessage(MirTimePunish.color(" &cВыход с сервера = Бан на 14 дней (п. 2.1)!"));
        target.sendMessage(MirTimePunish.color("&#0055ff&l&m                                                  "));

        mod.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fПроверка игрока &#00FF66" + target.getName() + " &fначата в отдельном мире."));
        mod.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &7Пишите в обычный чат для приватного общения с подозреваемым."));
        mod.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &7Добавить время (до 25 мин всего): &#00FF66/checktime " + target.getName() + " <минуты>"));
    }

    public void addCheckTime(Player mod, String targetName, int addMinutes) {
        Player target = Bukkit.getPlayerExact(targetName);
        UUID targetId = (target != null) ? target.getUniqueId() : null;

        if (targetId == null || !activeChecks.containsKey(targetId)) {
            mod.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cЭтот игрок сейчас не находится на проверке."));
            return;
        }
        if (addMinutes <= 0) {
            mod.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cУкажите положительное число минут."));
            return;
        }

        CheckSession session = activeChecks.get(targetId);
        long now = System.currentTimeMillis();
        long requestedExpire = session.expireTimeMs + (addMinutes * 60 * 1000L);

        if (requestedExpire > session.maxHardLimitMs) {
            long remainingAllowedMs = session.maxHardLimitMs - session.expireTimeMs;
            if (remainingAllowedMs <= 0) {
                mod.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cДостигнут лимит правил (п. 2.7): проверка не может идти дольше 25 минут!"));
                return;
            }
            session.currentPhaseStartMs = now;
            session.expireTimeMs = session.maxHardLimitMs;
            int actualAdded = (int) Math.ceil(remainingAllowedMs / 60000.0);
            mod.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &eДобавлено &#00FF66+" + actualAdded + " мин. &e(достигнут потолок 25 минут по п. 2.7)."));
            if (target.isOnline()) target.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fМодератор продлил время проверки (максимум 25 мин. по п. 2.7)."));
            return;
        }

        session.currentPhaseStartMs = now;
        session.expireTimeMs = requestedExpire;
        long totalMin = (session.expireTimeMs - session.startTimeMs) / 60000L;
        mod.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &#00FF66Добавлено +" + addMinutes + " мин. &fОбщий лимит проверки: &#00E5FF" + totalMin + "/25 мин."));
        if (target.isOnline()) target.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fМодератор добавил &#00FF66+" + addMinutes + " мин. &fк проверке."));
    }
    public void finishCheck(UUID suspectId, boolean clean) {
        CheckSession session = activeChecks.remove(suspectId);
        offlineGraceEnd.remove(suspectId);
        if (session == null) return;

        modToSuspect.remove(session.modId);
        session.bossBar.removeAll();
        session.bossBar.setVisible(false);

        Player suspect = Bukkit.getPlayer(suspectId);
        if (suspect != null && suspect.isOnline()) {
            suspect.teleport(session.suspectReturnLoc);
            suspect.setGameMode(session.suspectReturnMode);
            if (clean) suspect.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &#00FF66Проверка успешно пройдена! Вы возвращены на место."));
        }

        Player mod = Bukkit.getPlayer(session.modId);
        if (mod != null && mod.isOnline()) {
            mod.teleport(session.modReturnLoc);
            mod.setGameMode(session.modReturnMode);
        }
    }

    private void startTimerTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                Iterator<Map.Entry<UUID, Long>> graceIt = offlineGraceEnd.entrySet().iterator();
                while (graceIt.hasNext()) {
                    Map.Entry<UUID, Long> entry = graceIt.next();
                    UUID susId = entry.getKey();
                    long graceEnd = entry.getValue();
                    CheckSession session = activeChecks.get(susId);

                    if (now >= graceEnd) {
                        graceIt.remove();
                        if (session != null) {
                            String name = session.suspectName;
                            finishCheck(susId, false);
                            plugin.applyPunishment(Bukkit.getConsoleSender(), name, true, 14L * 86400000L, "Пункт 2.1 - Выход с проверки (Лив)");
                        }
                    } else if (session != null) {
                        long secLeft = Math.max(0, (graceEnd - now) / 1000L);
                        String timeStr = String.format("%02d:%02d", secLeft / 60, secLeft % 60);
                        session.bossBar.setColor(BarColor.RED);
                        session.bossBar.setProgress(Math.max(0.0, Math.min(1.0, (graceEnd - now) / 180000.0)));
                        session.bossBar.setTitle(MirTimePunish.color("&c&l⚠ ИГРОК ВЫШЕЛ! &8| &fОжидание перезахода: &c" + timeStr));
                    }
                }

                List<UUID> expired = new ArrayList<>();
                for (CheckSession session : activeChecks.values()) {
                    if (offlineGraceEnd.containsKey(session.suspectId)) continue;

                    long remMs = session.expireTimeMs - now;
                    if (remMs <= 0) {
                        expired.add(session.suspectId);
                        continue;
                    }

                    long totalPhaseMs = Math.max(1000L, session.expireTimeMs - session.currentPhaseStartMs);
                    double progress = Math.max(0.0, Math.min(1.0, (double) remMs / totalPhaseMs));
                    long secLeft = remMs / 1000L;
                    String timeStr = String.format("%02d:%02d", secLeft / 60, secLeft % 60);

                    long elapsedMin = (now - session.startTimeMs) / 60000L;
                    session.bossBar.setProgress(progress);
                    if (secLeft <= 60) {
                        session.bossBar.setColor(BarColor.RED);
                        session.bossBar.setTitle(MirTimePunish.color("&c&l⚡ ПРОВЕРКА НА ЧИТЫ &8| &fОсталось: &c" + timeStr + " &8(&7" + elapsedMin + "/25 мин&8)"));
                    } else {
                        session.bossBar.setColor(BarColor.BLUE);
                        session.bossBar.setTitle(MirTimePunish.color("&#0055ff&l⚡ ПРОВЕРКА НА ЧИТЫ &8| &fОсталось: &#00FF66" + timeStr + " &8(&7" + elapsedMin + "/25 мин&8)"));
                    }
                }

                for (UUID susId : expired) {
                    CheckSession session = activeChecks.get(susId);
                    if (session != null) {
                        Player mod = Bukkit.getPlayer(session.modId);
                        if (mod != null) mod.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &eВремя проверки игрока &#00FF66" + session.suspectName + " &eистекло. Проверка закрыта."));
                        finishCheck(susId, true);
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCheckChat(AsyncChatEvent e) {
        Player sender = e.getPlayer();
        UUID senderId = sender.getUniqueId();

        if (activeChecks.containsKey(senderId) || modToSuspect.containsKey(senderId)) {
            e.setCancelled(true);
            String plainMsg = PlainTextComponentSerializer.plainText().serialize(e.message());

            CheckSession session = activeChecks.containsKey(senderId) ? activeChecks.get(senderId) : activeChecks.get(modToSuspect.get(senderId));
            if (session == null) return;

            boolean isMod = senderId.equals(session.modId);
            String formatted = isMod
                    ? MirTimePunish.color("&#0055ff&l[ПРОВЕРКА] &#00E5FFМодератор " + sender.getName() + " &8» &f" + plainMsg)
                    : MirTimePunish.color("&#0055ff&l[ПРОВЕРКА] &#00FF66Проверяемый " + sender.getName() + " &8» &f" + plainMsg);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player sus = Bukkit.getPlayer(session.suspectId);
                Player mod = Bukkit.getPlayer(session.modId);
                if (sus != null && sus.isOnline()) sus.sendMessage(formatted);
                if (mod != null && mod.isOnline()) mod.sendMessage(formatted);
            });
            return;
        }
        e.viewers().removeIf(audience -> (audience instanceof Player p) && isInCheck(p.getUniqueId()));
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!activeChecks.containsKey(p.getUniqueId())) return;
        String msg = e.getMessage().toLowerCase();
        if (!msg.startsWith("/msg ") && !msg.startsWith("/m ") && !msg.startsWith("/r ")) {
            e.setCancelled(true);
            p.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cКоманды во время проверки отключены! Пишите прямо в чат."));
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!activeChecks.containsKey(e.getPlayer().getUniqueId())) return;
        if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getZ() != e.getTo().getZ()) e.setTo(e.getFrom());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        if (activeChecks.containsKey(id)) {
            CheckSession session = activeChecks.get(id);
            offlineGraceEnd.put(id, System.currentTimeMillis() + (3 * 60 * 1000L));
            Player mod = Bukkit.getPlayer(session.modId);
            if (mod != null && mod.isOnline()) mod.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &cИгрок &#00FF66" + p.getName() + " &cвышел с сервера! Ожидание перезахода 3 минуты (п. 2.1), после чего автобан на 14 дней."));
        }
        if (modToSuspect.containsKey(id)) finishCheck(modToSuspect.get(id), true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        if (activeChecks.containsKey(id) && offlineGraceEnd.containsKey(id)) {
            offlineGraceEnd.remove(id);
            CheckSession session = activeChecks.get(id);
            session.bossBar.addPlayer(p);
            if (checkWorld != null) p.teleport(new Location(checkWorld, 0.5, 100.0, 2.5, 180f, 0f));
            p.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &eВы перезашли вовремя. Проверка продолжается!"));
            Player mod = Bukkit.getPlayer(session.modId);
            if (mod != null && mod.isOnline()) mod.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00FF66Игрок " + p.getName() + " перезашёл на сервер! Проверка продолжается."));
        } else if (checkWorld != null && p.getWorld().equals(checkWorld) && !isInCheck(id)) {
            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }

    @EventHandler public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p && isInCheck(p.getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onBreak(BlockBreakEvent e) { if (checkWorld != null && e.getBlock().getWorld().equals(checkWorld)) e.setCancelled(true); }
    @EventHandler public void onPlace(BlockPlaceEvent e) { if (checkWorld != null && e.getBlock().getWorld().equals(checkWorld)) e.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { if (isFrozen(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onInvClick(InventoryClickEvent e) { if (e.getWhoClicked() instanceof Player p && isFrozen(p.getUniqueId())) e.setCancelled(true); }
}

package ru.mirtime.punish;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatFilter implements Listener {

    private final MirTimePunish plugin;
    private final Map<UUID, WarnData> warnings = new ConcurrentHashMap<>();

    private static class WarnData {
        int count;
        long lastWarnTime;

        WarnData(int count, long lastWarnTime) {
            this.count = count;
            this.lastWarnTime = lastWarnTime;
        }
    }

    public ChatFilter(MirTimePunish plugin) {
        this.plugin = plugin;
    }

    public void resetWarnings(UUID uuid) {
        warnings.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (shouldBypass(p)) return;

        String rawMessage = PlainTextComponentSerializer.plainText().serialize(e.message());
        String matchedWord = findMatchedWord(rawMessage);

        if (matchedWord != null) {
            e.setCancelled(true);
            handleViolation(p, rawMessage, matchedWord, false);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPrivateCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("chat-filter.check-private-commands", true)) return;

        Player p = e.getPlayer();
        if (shouldBypass(p)) return;

        String msg = e.getMessage();
        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/msg ") || lower.startsWith("/m ") || lower.startsWith("/tell ")
                || lower.startsWith("/w ") || lower.startsWith("/r ")) {
            int firstSpace = msg.indexOf(' ');
            if (firstSpace == -1 || firstSpace + 1 >= msg.length()) return;

            String content = msg.substring(firstSpace + 1);
            String matchedWord = findMatchedWord(content);
            if (matchedWord != null) {
                e.setCancelled(true);
                handleViolation(p, msg, matchedWord, true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Очищаем просроченные записи при выходе игрока
        UUID id = e.getPlayer().getUniqueId();
        WarnData data = warnings.get(id);
        if (data != null && isExpired(data.lastWarnTime)) {
            warnings.remove(id);
        }
    }

    private boolean shouldBypass(Player p) {
        if (!plugin.getConfig().getBoolean("chat-filter.enabled", true)) return true;
        if (p.hasPermission("mirtime.punish.filter.bypass") || p.hasPermission("mirtime.punish.bypass")) return true;
        return plugin.getCheckManager() != null && plugin.getCheckManager().isInCheck(p.getUniqueId());
    }

    private boolean isExpired(long lastTime) {
        long expireMinutes = plugin.getConfig().getLong("chat-filter.warn-expire-minutes", 10L);
        return expireMinutes > 0 && (System.currentTimeMillis() - lastTime) > (expireMinutes * 60_000L);
    }

    private void handleViolation(Player p, String originalMessage, String matchedWord, boolean isPrivateCmd) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();

        int maxWarns = plugin.getConfig().getInt("chat-filter.max-warnings", 3);
        WarnData data = warnings.get(uuid);

        int currentWarns;
        if (data == null || isExpired(data.lastWarnTime)) {
            currentWarns = 1;
        } else {
            currentWarns = data.count + 1;
        }

        if (currentWarns >= maxWarns) {
            warnings.remove(uuid);
            String durationStr = plugin.getConfig().getString("chat-filter.mute-duration", "15m");
            String reason = plugin.getConfig().getString("chat-filter.mute-reason", "Пункт 3.7 - Чрезмерная нецензурная лексика (Авто-фильтр)");
            long durationMs = plugin.parseDuration(durationStr);

            Bukkit.getScheduler().runTask(plugin, () -> {
                notifyStaff(p, originalMessage, matchedWord, maxWarns, maxWarns, true);
                p.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cВы набрали &#00FF66[" + maxWarns + "/" + maxWarns + "] &cпредупреждений фильтра и получили авто-мут!"));
                plugin.applyPunishment(Bukkit.getConsoleSender(), p.getName(), false, durationMs, reason);
            });
        } else {
            warnings.put(uuid, new WarnData(currentWarns, now));
            String chatType = isPrivateCmd ? "в личных сообщениях" : "в чате";
            Bukkit.getScheduler().runTask(plugin, () -> {
                notifyStaff(p, originalMessage, matchedWord, currentWarns, maxWarns, false);
                p.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cСообщение " + chatType + " заблокировано фильтром! Предупреждение: &#00FF66[" + currentWarns + "/" + maxWarns + "]"));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
            });
        }
    }

    private void notifyStaff(Player offender, String message, String matchedWord, int warns, int maxWarns, boolean muted) {
        if (!plugin.getConfig().getBoolean("chat-filter.notify-staff", true)) return;

        String status = muted ? "&c[АВТО-МУТ]" : "&e[" + warns + "/" + maxWarns + "]";
        String alert = MirTimePunish.color(
                "&#0055ff&l[Фильтр] " + status + " &f" + offender.getName()
                        + " &8(&7слово: &c" + matchedWord + "&8) &7» &f" + message
        );

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("mirtime.punish.reports")) {
                staff.sendMessage(alert);
            }
        }
    }

    private String findMatchedWord(String message) {
        List<String> badWords = plugin.getConfig().getStringList("chat-filter.words");
        if (badWords.isEmpty()) return null;

        List<String> whitelist = plugin.getConfig().getStringList("chat-filter.whitelist");
        boolean smart = plugin.getConfig().getBoolean("chat-filter.smart-normalize", true);

        String lower = message.toLowerCase(Locale.ROOT);
        for (String safe : whitelist) {
            if (!safe.isEmpty()) {
                lower = lower.replace(safe.toLowerCase(Locale.ROOT), " ");
            }
        }

        String normalized = smart ? normalizeText(lower) : lower;
        String compact = normalized.replaceAll("[^а-яёa-z0-9]", "");
        String collapsed = collapseRepeats(compact);

        for (String rawBad : badWords) {
            if (rawBad == null || rawBad.isBlank()) continue;

            String cleanBad = smart ? normalizeText(rawBad.toLowerCase(Locale.ROOT)) : rawBad.toLowerCase(Locale.ROOT);
            String compactBad = cleanBad.replaceAll("[^а-яёa-z0-9]", "");
            String collapsedBad = collapseRepeats(compactBad);

            if (normalized.contains(cleanBad)
                    || (!compactBad.isEmpty() && compact.contains(compactBad))
                    || (!collapsedBad.isEmpty() && collapsed.contains(collapsedBad))) {
                return rawBad;
            }
        }
        return null;
    }

    private String normalizeText(String text) {
        return text
                .replace('a', 'а')
                .replace('@', 'а')
                .replace('b', 'б')
                .replace('6', 'б')
                .replace('v', 'в')
                .replace('w', 'ш')
                .replace('g', 'г')
                .replace('d', 'д')
                .replace('e', 'е')
                .replace('ё', 'е')
                .replace('3', 'з')
                .replace('z', 'з')
                .replace('u', 'у')
                .replace('y', 'у')
                .replace('i', 'и')
                .replace('1', 'и')
                .replace('k', 'к')
                .replace('l', 'л')
                .replace('m', 'м')
                .replace('n', 'н')
                .replace('h', 'н')
                .replace('o', 'о')
                .replace('0', 'о')
                .replace('p', 'р')
                .replace('r', 'р')
                .replace('c', 'с')
                .replace('s', 'с')
                .replace('$', 'с')
                .replace('t', 'т')
                .replace('f', 'ф')
                .replace('x', 'х')
                .replace('4', 'ч');
    }

    private String collapseRepeats(String input) {
        if (input.length() < 2) return input;
        StringBuilder sb = new StringBuilder(input.length());
        char prev = 0;
        for (int i = 0; i < input.length(); i++) {
            char cur = input.charAt(i);
            if (cur != prev) {
                sb.append(cur);
                prev = cur;
            }
        }
        return sb.toString();
    }
}

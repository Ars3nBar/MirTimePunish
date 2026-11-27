package ru.mirtime.punish;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ChatFilter implements Listener {

    private final MirTimePunish plugin;
    private final Map<UUID, Integer> warnings = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCheckTime = new ConcurrentHashMap<>();

    private static final Pattern LINK_PATTERN = Pattern.compile(
            "(?i)(https?://|www\\.|[a-z0-9-]+\\.(ru|com|net|org|su|fun|gg|me|xyz|pro|tk|ml|ga|cf|gq)|\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d{1,5})?)"
    );

    private static final List<String> ART_3_1_WORDS = List.of(
            "мать ебал", "мамку ебал", "мать шлюха", "мать сдохла", "сын шлюхи", "сын бляди",
            "ебал твою мать", "ебал твою маму", "мать твоя", "отца ебал", "батю ебал"
    );

    private static final List<String> ART_3_2_WORDS = List.of(
            "mq", "мкью", "мамке привет", "маме привет", "твоей мамке", "твоей маме", "без мамный", "безмамный", "детдомовец"
    );

    private static final List<String> ART_3_3_WORDS = List.of(
            "сватну", "сватнуть", "деанон", "доксну", "сливаю адрес", "пробью по ip", "найду по айпи"
    );

    private static final List<String> ART_3_4_WORDS = List.of(
            "сдохни", "умри", "вскройся", "повесься", "чтоб ты сдох", "убейся"
    );

    private static final List<String> ART_3_5_WORDS = List.of(
            "зиг хайль", "хайль гитлер", "чурка", "хач", "ниггер", "хохол", "кацап"
    );

    private static final List<String> ART_3_7_WORDS = List.of(
            "бля", "блять", "сука", "хуй", "хуйня", "пизда", "пиздец", "ебать", "ебаный",
            "нахуй", "похуй", "долбоеб", "долбаеб", "уебок", "пидор", "пидорас", "гандон", "мудак", "шлюха"
    );

    public ChatFilter(MirTimePunish plugin) {
        this.plugin = plugin;
    }

    public void resetWarnings(UUID uuid) {
        warnings.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPaperChat(AsyncChatEvent e) {
        String plain = PlainTextComponentSerializer.plainText().serialize(e.message());
        if (checkAndPunish(e.getPlayer(), plain)) {
            e.setCancelled(true);
            e.viewers().clear();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLegacyChat(AsyncPlayerChatEvent e) {
        if (checkAndPunish(e.getPlayer(), e.getMessage())) {
            e.setCancelled(true);
            e.getRecipients().clear();
        }
    }

    private boolean checkAndPunish(Player p, String rawMessage) {
        if (p.hasPermission("mirtime.punish.filter.bypass")) return false;
        if (plugin.getCheckManager() != null && plugin.getCheckManager().isInCheck(p.getUniqueId())) return false;

        long now = System.currentTimeMillis();
        Long last = lastCheckTime.get(p.getUniqueId());
        if (last != null && (now - last) < 100L) {
            return true;
        }

        String lower = rawMessage.toLowerCase().trim();
        String normalized = lower.replaceAll("[^а-яa-z0-9\\s]", "");

        // 1. Реклама сторонних ресурсов (Пункт 3.8) — разрешаем официальные соцсети Mir-Time
        if (LINK_PATTERN.matcher(lower).find()) {
            boolean isOfficial = lower.contains("mir-time.ru")
                    || lower.contains("club241998844")
                    || lower.contains("t.me/mirtime_minecraft")
                    || lower.contains("discord.gg/4c2ag4cwqu");
            if (!isOfficial) {
                lastCheckTime.put(p.getUniqueId(), now);
                handleViolation(p, "Пункт 3.8 - Реклама сторонних ресурсов", "7d", true, "Бан на 7 дней");
                return true;
            }
        }

        if (containsAny(lower, normalized, ART_3_3_WORDS)) {
            lastCheckTime.put(p.getUniqueId(), now);
            handleViolation(p, "Пункт 3.3 - Угрозы расправой, деанон и слив данных", "14d", true, "Бан на 14 дней");
            return true;
        }

        if (containsAny(lower, normalized, ART_3_1_WORDS)) {
            lastCheckTime.put(p.getUniqueId(), now);
            handleViolation(p, "Пункт 3.1 - Прямое оскорбление родителей и родных", "7d", false, "Мут на 7 дней");
            return true;
        }

        if (containsAny(lower, normalized, ART_3_2_WORDS)) {
            lastCheckTime.put(p.getUniqueId(), now);
            handleViolation(p, "Пункт 3.2 - Упоминание и завуалированное оск. родных", "2d", false, "Мут на 2 дня");
            return true;
        }

        if (containsAny(lower, normalized, ART_3_4_WORDS)) {
            lastCheckTime.put(p.getUniqueId(), now);
            handleViolation(p, "Пункт 3.4 - Пожелания смерти и жёсткая травля", "12h", false, "Мут на 12 часов");
            return true;
        }

        if (containsAny(lower, normalized, ART_3_5_WORDS)) {
            lastCheckTime.put(p.getUniqueId(), now);
            handleViolation(p, "Пункт 3.5 - Разжигание межнациональной и политической розни", "12h", false, "Мут на 12 часов");
            return true;
        }

        if (containsAny(lower, normalized, ART_3_7_WORDS)) {
            lastCheckTime.put(p.getUniqueId(), now);
            handleViolation(p, "Пункт 3.7 - Чрезмерная нецензурная лексика в глобальном чате", "1h", false, "Мут на 1 час");
            return true;
        }

        if (rawMessage.length() >= 8) {
            long upper = rawMessage.chars().filter(Character::isUpperCase).count();
            long letters = rawMessage.chars().filter(Character::isLetter).count();
            if (letters >= 6 && ((double) upper / letters) >= 0.75) {
                lastCheckTime.put(p.getUniqueId(), now);
                handleViolation(p, "Пункт 3.6 - Спам, флуд и злоупотребление капсом", "1h", false, "Мут на 1 час");
                return true;
            }
        }

        return false;
    }

    private boolean containsAny(String lower, String normalized, List<String> list) {
        for (String w : list) {
            if (lower.contains(w) || normalized.contains(w)) return true;
        }
        return false;
    }

    private void handleViolation(Player p, String articleReason, String durationStr, boolean isBan, String punishLabel) {
        UUID id = p.getUniqueId();
        int current = warnings.getOrDefault(id, 0) + 1;

        if (current >= 3) {
            warnings.remove(id);
            long durMs = plugin.parseDuration(durationStr);
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.applyPunishment(Bukkit.getConsoleSender(), p.getName(), isBan, durMs, articleReason)
            );
        } else {
            warnings.put(id, current);
            int left = 3 - current;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) return;
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 0.9f);
                p.sendTitle(
                        MirTimePunish.color("&c&l⚠ ПРЕДУПРЕЖДЕНИЕ [" + current + "/3]"),
                        MirTimePunish.color("&fЕщё &c" + left + " &fпопытки — и будет &c" + punishLabel),
                        5, 50, 10
                );
                p.sendMessage(MirTimePunish.color("&#0055ff&l&m                                                  "));
                p.sendMessage(MirTimePunish.color(" &c&l[АВТО-ФИЛЬТР] &#00E5FF&lMIR-TIME &8» &cСообщение заблокировано! &8(&e" + current + "/3&8)"));
                p.sendMessage(MirTimePunish.color(" &#0055ff● &fСтатья: &#00E5FF" + articleReason));
                p.sendMessage(MirTimePunish.color(" &#0055ff● &fНаказание на 3/3: &c&l" + punishLabel));
                p.sendMessage(MirTimePunish.color("&#0055ff&l&m                                                  "));
            });
        }
    }
}

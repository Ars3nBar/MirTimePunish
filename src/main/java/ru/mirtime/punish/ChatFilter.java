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
import java.util.regex.Pattern;

public class ChatFilter implements Listener {

    private final MirTimePunish plugin;
    private final Map<UUID, Integer> warnings = new HashMap<>();

    // Паттерн ссылок и сторонних IP (Статья 3.8)
    private static final Pattern LINK_PATTERN = Pattern.compile(
            "(?i)(https?://|www\\.|[a-z0-9-]+\\.(ru|com|net|org|su|fun|gg|me|xyz|pro|tk|ml|ga|cf|gq)|\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d{1,5})?)"
    );

    // Статья 3.1: Прямое оскорбление родных (тяжёлое нарушение -> сразу 3/3 предупреждения = мут 7д)
    private static final List<String> ART_3_1_WORDS = List.of(
            "мать ебал", "мамку ебал", "мать шлюха", "мать сдохла", "сын шлюхи", "сын бляди",
            "ебал твою мать", "ебал твою маму", "мать твоя", "отца ебал", "батю ебал"
    );

    // Статья 3.2: Завуалированное упоминание / неуважение к родным (+2 предупреждения)
    private static final List<String> ART_3_2_WORDS = List.of(
            "mq", "мкью", "мамке привет", "маме привет", "твоей мамке", "твоей маме", "без мамный", "безмамный", "детдомовец"
    );

    // Статья 3.3: Деанон / угрозы слива личных данных (сразу 3/3 -> бан навсегда или строгий блок)
    private static final List<String> ART_3_3_WORDS = List.of(
            "сватну", "сватнуть", "деанон", "доксну", "сливаю адрес", "пробью по ip", "найду по айпи"
    );

    // Статья 3.4: Пожелания смерти (+2 предупреждения)
    private static final List<String> ART_3_4_WORDS = List.of(
            "сдохни", "умри", "вскройся", "повесься", "чтоб ты сдох", "убейся"
    );

    // Статья 3.5: Розжиг / нацизм (+2 предупреждения)
    private static final List<String> ART_3_5_WORDS = List.of(
            "зиг хайль", "хайль гитлер", "чурка", "хач", "ниггер", "хохол", "кацап"
    );

    // Статья 3.7: Чрезмерный мат и оскорбления в глобальном чате (+1 предупреждение, 3 пред. = мут 15м)
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

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPaperChat(AsyncChatEvent e) {
        String plain = PlainTextComponentSerializer.plainText().serialize(e.message());
        if (checkAndPunish(e.getPlayer(), plain)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onLegacyChat(AsyncPlayerChatEvent e) {
        if (checkAndPunish(e.getPlayer(), e.getMessage())) {
            e.setCancelled(true);
        }
    }

    private boolean checkAndPunish(Player p, String rawMessage) {
        if (p.hasPermission("mirtime.punish.filter.bypass")) return false;
        if (plugin.getCheckManager() != null && plugin.getCheckManager().isInCheck(p.getUniqueId())) return false;

        String lower = rawMessage.toLowerCase().trim();
        // Нормализация обходов вида "с.у.к.а" или "б л я"
        String normalized = lower.replaceAll("[^а-яa-z0-9\\s]", "");

        // 1. Проверка рекламы (Статья 3.8) — разрешаем только официальные домены проекта
        if (LINK_PATTERN.matcher(lower).find()) {
            boolean isOfficial = lower.contains("mir-time.ru") || lower.contains("://vk.com") || lower.contains("max.ru/se15118439");
            if (!isOfficial) {
                handleViolation(p, 3, "Пункт 3.8 - Реклама сторонних ресурсов", "12h", false);
                return true;
            }
        }

        // 2. Статья 3.3 (Деанон / угрозы) -> мгновенно 3/3 (Бан навсегда)
        if (containsAny(lower, normalized, ART_3_3_WORDS)) {
            handleViolation(p, 3, "Пункт 3.3 - Угрозы расправой, деанон и слив данных", "perm", true);
            return true;
        }

        // 3. Статья 3.1 (Прямое оск. родных) -> мгновенно 3/3 (Мут 7 дней)
        if (containsAny(lower, normalized, ART_3_1_WORDS)) {
            handleViolation(p, 3, "Пункт 3.1 - Прямое оскорбление родителей и родных", "7d", false);
            return true;
        }

        // 4. Статья 3.2 (Завуал. упом. родных) -> +2 предупреждения (Мут 2 дня)
        if (containsAny(lower, normalized, ART_3_2_WORDS)) {
            handleViolation(p, 2, "Пункт 3.2 - Упоминание и завуалированное оск. родных", "2d", false);
            return true;
        }

        // 5. Статья 3.4 (Пожелания смерти) -> +2 предупреждения (Мут 3 часа)
        if (containsAny(lower, normalized, ART_3_4_WORDS)) {
            handleViolation(p, 2, "Пункт 3.4 - Пожелания смерти и жёсткая травля", "3h", false);
            return true;
        }

        // 6. Статья 3.5 (Розжиг) -> +2 предупреждения (Мут 3 часа)
        if (containsAny(lower, normalized, ART_3_5_WORDS)) {
            handleViolation(p, 2, "Пункт 3.5 - Разжигание межнациональной и политической розни", "3h", false);
            return true;
        }

        // 7. Статья 3.7 (Мат и оскорбления) -> +1 предупреждение (на 3/3 Мут 15 минут)
        if (containsAny(lower, normalized, ART_3_7_WORDS)) {
            handleViolation(p, 1, "Пункт 3.7 - Чрезмерная нецензурная лексика в глобальном чате", "15m", false);
            return true;
        }

        // 8. Статья 3.6 (Злоупотребление КАПСОМ: длиннее 8 символов и >75% заглавных букв)
        if (rawMessage.length() >= 8) {
            long upper = rawMessage.chars().filter(Character::isUpperCase).count();
            long letters = rawMessage.chars().filter(Character::isLetter).count();
            if (letters >= 6 && ((double) upper / letters) >= 0.75) {
                handleViolation(p, 1, "Пункт 3.6 - Спам, флуд и злоупотребление капсом", "15m", false);
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

    private void handleViolation(Player p, int weight, String articleReason, String durationStr, boolean isBan) {
        UUID id = p.getUniqueId();
        int current = warnings.getOrDefault(id, 0) + weight;

        if (current >= 3) {
            warnings.remove(id);
            long durMs = plugin.parseDuration(durationStr);
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.applyPunishment(Bukkit.getConsoleSender(), p.getName(), isBan, durMs, articleReason)
            );
        } else {
            warnings.put(id, current);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
            p.sendMessage(MirTimePunish.color("&#0055ff&l⚡ &#00E5FF&lАВТО-ФИЛЬТР &8» &cСообщение заблокировано! &8(&eПредупреждение " + current + "/3&8)"));
            p.sendMessage(MirTimePunish.color(" &#0055ff● &fНарушение: &#00E5FF" + articleReason + " &7(на 3/3 — автоматическое наказание)"));
        }
    }
}

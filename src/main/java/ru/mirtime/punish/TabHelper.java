package ru.mirtime.punish;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class TabHelper implements TabCompleter {

    private final MirTimePunish plugin;

    public TabHelper(MirTimePunish plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String name = cmd.getName().toLowerCase();
        List<String> list = new ArrayList<>();

        // 1-й аргумент: ники игроков
        if (args.length == 1) {
            if (name.equals("punish")) list.add("reload");
            for (Player p : Bukkit.getOnlinePlayers()) {
                list.add(p.getName());
            }
            return filter(list, args);
        }

        // Для /ban и /mute: 2-й аргумент — только статьи нужного типа (BAN или MUTE)
        if (name.equals("ban") || name.equals("mute")) {
            String targetType = name.toUpperCase();
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("templates");
            if (sec == null) return list;

            if (args.length == 2) {
                for (String key : sec.getKeys(false)) {
                    if (sec.getString(key + ".type", "BAN").equalsIgnoreCase(targetType)) {
                        String reason = sec.getString(key + ".reason");
                        if (reason != null) list.add(reason);
                    }
                }
                return filter(list, args);
            }

            // После выбора статьи (3-й+ аргумент) автоматически подсказываем её срок из config.yml
            String typedReason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length - 1));
            for (String key : sec.getKeys(false)) {
                String reason = sec.getString(key + ".reason", "");
                if (reason.equalsIgnoreCase(typedReason)) {
                    String dur = sec.getString(key + ".duration", "1d");
                    list.add(dur);
                    return filter(list, args);
                }
            }
        }

        return list;
    }

    private List<String> filter(List<String> input, String[] args) {
        String last = args[args.length - 1].toLowerCase();
        List<String> res = new ArrayList<>();
        for (String s : input) {
            if (s.toLowerCase().startsWith(last)) res.add(s);
        }
        return res;
    }
}

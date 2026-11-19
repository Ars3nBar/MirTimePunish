package ru.mirtime.punish;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
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

        // Аргумент 1: онлайн игроки (и reload для /punish)
        if (args.length == 1) {
            if (name.equals("punish")) {
                list.add("reload");
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                list.add(p.getName());
            }
            return filter(list, args);
        }

        // Подсказки для /checktime <ник> <минуты>
        if (name.equals("checktime") && args.length == 2) {
            return filter(List.of("5", "10", "15", "20"), args);
        }

        // Подсказки статей и сроков для /ban и /mute
        if (name.equals("ban") || name.equals("mute")) {
            String targetType = name.toUpperCase();
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("templates");
            if (sec == null) return list;

            if (args.length == 2) {
                for (String key : sec.getKeys(false)) {
                    String type = sec.getString(key + ".type", "BAN");
                    if (type.equalsIgnoreCase(targetType) || (targetType.equals("BAN") && type.equalsIgnoreCase("KICK"))) {
                        String reason = sec.getString(key + ".reason");
                        if (reason != null) {
                            list.add(reason);
                        }
                    }
                }
                return filter(list, args);
            } else if (args.length >= 3) {
                String typedReason = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
                for (String key : sec.getKeys(false)) {
                    String type = sec.getString(key + ".type", "BAN");
                    if (type.equalsIgnoreCase(targetType) || (targetType.equals("BAN") && type.equalsIgnoreCase("KICK"))) {
                        String reason = sec.getString(key + ".reason", "");
                        if (reason.equalsIgnoreCase(typedReason)) {
                            list.add(sec.getString(key + ".duration", "1d"));
                            return filter(list, args);
                        }
                    }
                }
            }
        }

        return list;
    }

    private List<String> filter(List<String> list, String[] args) {
        String last = args[args.length - 1].toLowerCase();
        List<String> res = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(last)) {
                res.add(s);
            }
        }
        return res;
    }
}

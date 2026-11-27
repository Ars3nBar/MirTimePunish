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
        List<String> argsList = Arrays.asList(args);
        List<String> list = new ArrayList<>();

        if (argsList.size() == 1) {
            if (name.equals("punish")) {
                list.add("reload");
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                list.add(p.getName());
            }
            return filter(list, argsList);
        }

        if (name.equals("checktime") && argsList.size() == 2) {
            return filter(List.of("5", "10", "15", "20"), argsList);
        }

        // Подсказки для /tempban и /tempmute: 2-м аргументом подсказываем популярные сроки и короткие коды статей
        if (name.equals("tempban") || name.equals("tempmute")) {
            String targetType = name.contains("ban") ? "BAN" : "MUTE";
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("templates");

            if (argsList.size() == 2) {
                list.addAll(name.contains("ban")
                        ? List.of("3h", "6h", "12h", "1d", "3d", "5d", "7d", "10d", "14d", "30d")
                        : List.of("15m", "20m", "30m", "1h", "3h", "12h", "1d", "2d", "7d"));
                if (sec != null) {
                    for (String key : sec.getKeys(false)) {
                        String type = sec.getString(key + ".type", "BAN");
                        if (type.equalsIgnoreCase(targetType)) {
                            list.add(key.replace("p", "").replace("_", "."));
                        }
                    }
                }
                return filter(list, argsList);
            } else if (argsList.size() == 3 && sec != null) {
                for (String key : sec.getKeys(false)) {
                    String type = sec.getString(key + ".type", "BAN");
                    if (type.equalsIgnoreCase(targetType)) {
                        String reason = sec.getString(key + ".reason");
                        if (reason != null) list.add(reason);
                    }
                }
                return filter(list, argsList);
            }
        }

        // Подсказки для /ban и /mute: короткие номера статей (1.1, 3.1) и полные причины
        if (name.equals("ban") || name.equals("mute")) {
            String targetType = name.toUpperCase();
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("templates");
            if (sec == null) return list;

            if (argsList.size() == 2) {
                for (String key : sec.getKeys(false)) {
                    String type = sec.getString(key + ".type", "BAN");
                    if (type.equalsIgnoreCase(targetType) || (targetType.equals("BAN") && type.equalsIgnoreCase("KICK"))) {
                        list.add(key.replace("p", "").replace("_", "."));
                        String reason = sec.getString(key + ".reason");
                        if (reason != null) list.add(reason);
                    }
                }
                return filter(list, argsList);
            } else if (argsList.size() >= 3) {
                list.addAll(List.of("1h", "1d", "3d", "7d", "14d", "30d", "perm"));
                return filter(list, argsList);
            }
        }

        return list;
    }

    private List<String> filter(List<String> list, List<String> argsList) {
        String last = argsList.get(argsList.size() - 1).toLowerCase();
        List<String> res = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(last)) {
                res.add(s);
            }
        }
        return res;
    }
}

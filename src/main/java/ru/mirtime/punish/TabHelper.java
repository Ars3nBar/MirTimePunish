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

        // 1-й аргумент: ники онлайн-игроков (для /punish добавляем ещё reload)
        if (args.length == 1) {
            if (name.equals("punish")) list.add("reload");
            for (Player p : Bukkit.getOnlinePlayers()) {
                list.add(p.getName());
            }
            return filter(list, args);
        }

        // 2-й аргумент для /ban и /mute: подсказки времени
        if (args.length == 2 && (name.equals("ban") || name.equals("mute"))) {
            list.addAll(List.of("30m", "1h", "2h", "5h", "1d", "3d", "7d", "14d", "30d", "perm"));
            return filter(list, args);
        }

        // 3-й аргумент для /ban и /mute: причины и статьи из config.yml
        if (args.length == 3 && (name.equals("ban") || name.equals("mute"))) {
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("templates");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    String reason = sec.getString(key + ".reason");
                    if (reason != null) list.add(reason);
                }
            }
            return filter(list, args);
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

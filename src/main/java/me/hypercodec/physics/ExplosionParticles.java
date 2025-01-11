package me.hypercodec.physics;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;

import java.util.*;

public class ExplosionParticles implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if(label.equalsIgnoreCase("explosionparticles") || label.equalsIgnoreCase("ep")) {
            if(!sender.hasPermission("blockphysics.changegraphics")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command");
                return true;
            }

            if(!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Console cannot view explosion particles");
                return true;
            }

            if(args.length == 0) {
                if (((Player) sender).getPersistentDataContainer().has(BlockPhysics.explosionparticleskey, PersistentDataType.INTEGER)) {
                    ((Player) sender).getPersistentDataContainer().remove(BlockPhysics.explosionparticleskey);

                    sender.sendMessage("Realistic explosion particles toggled " + ChatColor.RED + "off");

                    return true;
                }

                ((Player) sender).getPersistentDataContainer().set(BlockPhysics.explosionparticleskey, PersistentDataType.INTEGER, 1);

                sender.sendMessage("Explosion particles toggled " + ChatColor.GREEN + "on");

                return true;
            }

            if(args[0].equalsIgnoreCase("enable")) {
                ((Player) sender).getPersistentDataContainer().set(BlockPhysics.explosionparticleskey, PersistentDataType.INTEGER, 1);

                sender.sendMessage("Realistic explosion particles " + ChatColor.GREEN + "enabled");

                return true;
            }

            if(args[0].equalsIgnoreCase("disable")) {
                ((Player) sender).getPersistentDataContainer().remove(BlockPhysics.explosionparticleskey);

                sender.sendMessage("Realistic explosion particles " + ChatColor.RED + "disabled");

                return true;
            }

            sender.sendMessage(ChatColor.RED + "Invalid argument(s)");

            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        final List<String> completions = new ArrayList<>();

        completions.add("enable");
        completions.add("disable");

        StringUtil.copyPartialMatches(args[0], new ArrayList<>(), completions);

        Collections.sort(completions);

        return completions;
    }
}

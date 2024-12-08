package com.roki.core;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FactionTabCompleter {
    @SuppressWarnings("deprecation")
    public static void setupTabCompletion(Command command) {
        // Clear existing parameters
        command.getCommandParameters().clear();

        // Define parameters for the faction command
        command.getCommandParameters().put("default", new CommandParameter[] {
            new CommandParameter("subcommand", new String[] {
                "join", "leave", "info", "money",
                "topmoney", "deposit", "topkills", "players",
                "sethome", "removehome" // Add sethome and removehome
            }),
            new CommandParameter("argument", true)
        });
    }

    public static List<String> getTabCompletions(RoyalKingdomsCore plugin, CommandSender sender, Command command, String alias, String[] args) {
        // Ensure the sender is a player
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        // Main faction command suggestions
        if (args.length == 1) {
            // List of all possible subcommands
            List<String> subcommands = Arrays.asList(
                "join", "leave", "info", "money",
                "topmoney", "deposit", "topkills", "players",
                "sethome", "removehome" // Add sethome and removehome
            );

            // Filter subcommands based on what the user has typed so far
            return subcommands.stream()
                .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        // Suggestions for second argument based on first argument
        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "info":
                case "players":
                    // Suggest available faction names for info and players commands
                    return plugin.getFactionNames().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());

                case "deposit":
                    // Suggest common deposit amounts
                    return Arrays.asList("10", "50", "100", "500", "1000")
                        .stream()
                        .filter(amount -> amount.startsWith(args[1]))
                        .collect(Collectors.toList());

                case "money":
                case "topmoney":
                    // Suggest available faction names for money-related commands
                    return plugin.getFactionNames().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());

                case "sethome":
                case "removehome":
                    // Only suggest this if the player has a home set already
                    return Arrays.asList("confirm") // Add confirmation if needed
                        .stream()
                        .filter(s -> s.startsWith(args[1]))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}
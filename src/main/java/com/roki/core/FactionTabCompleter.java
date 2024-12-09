package com.roki.core;

import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandEnum;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FactionTabCompleter {

    // Main faction subcommands
    private static final List<String> FACTION_SUBCOMMANDS = Arrays.asList(
        "join", "leave", "info", "money", "topmoney", 
        "deposit", "topkills", "players"
    );

    // Faction names for suggestions
    private static final List<String> FACTION_NAMES = Arrays.asList("phoenix", "griffin", "dragon");

    @SuppressWarnings("deprecation")
    public static void setupTabCompletion(Command command, RoyalKingdomsCore plugin) {
        command.getCommandParameters().clear();
        command.setUsage("/f <subcommand> [parameters]");

        // Define command parameters for tab completion
        command.getCommandParameters().put("FactionActions", new   CommandParameter[] {
            CommandParameter.newEnum("action", new CommandEnum("FactionAction", FACTION_SUBCOMMANDS)),
            CommandParameter.newEnum("value", new CommandEnum("FactionName",  FACTION_NAMES)),
        });
       
    }
}

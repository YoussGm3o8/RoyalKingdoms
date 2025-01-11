package com.roki.core;

import cn.nukkit.command.Command;
import cn.nukkit.command.data.CommandEnum;
import cn.nukkit.command.data.CommandParameter;
import com.roki.core.database.DatabaseManager;

import java.util.Arrays;
import java.util.List;

public class FactionTabCompleter {

    // Main faction subcommands
    private static final List<String> FACTION_SUBCOMMANDS = Arrays.asList(
        "join", "leave", "info", "money", "topmoney", 
        "deposit", "topkills", "players", "create", "invite"
    );    
        @SuppressWarnings("deprecation")
        public static void setupTabCompletion(Command command) {
            command.getCommandParameters().clear();
            command.setUsage("/f <subcommand> [parameters]");
    
            // Fetch faction names from the database
            DatabaseManager dbManager = RoyalKingdomsCore.getInstance().getDataModel().getDatabaseManager();
        List<String> factionNames = dbManager.getAllFactionNames();

        // Define command parameters for tab completion
        command.getCommandParameters().put("FactionActions", new CommandParameter[] {
            CommandParameter.newEnum("action", new CommandEnum("FactionAction", FACTION_SUBCOMMANDS)),
            CommandParameter.newEnum("value", new CommandEnum("FactionName", factionNames))
        });
    }
}

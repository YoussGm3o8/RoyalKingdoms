package com.roki.core;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.data.CommandEnum;
import cn.nukkit.command.data.CommandParameter;
import com.roki.core.database.DatabaseManager;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FactionTabCompleter {

    // Main faction subcommands
    private static final List<String> FACTION_SUBCOMMANDS = Arrays.asList(
        "join", "leave", "info", "money", "topmoney", "topkills", "players", "create", "disband", "home", "sethome", "gui"
    );
    private static final List<String> MONEY_SUGG = Arrays.asList( "1000", "5000", "10000", "50000", "100000" );

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

        // Define specific parameters for deposit command
        command.getCommandParameters().put("DepositActions", new CommandParameter[] {
            CommandParameter.newEnum("action", new CommandEnum("DepositAction", Arrays.asList("deposit"))),
            CommandParameter.newEnum("amount", new CommandEnum("Amount", MONEY_SUGG)),
        });

        // Define specific parameters for promote and demote commands
        command.getCommandParameters().put("PromoteDemoteActions", new CommandParameter[] {
            CommandParameter.newEnum("action", new CommandEnum("PromoteDemoteAction", Arrays.asList("promote", "demote"))),
            CommandParameter.newEnum("player", new CommandEnum("PlayerName", getFactionPlayers()))
        });

        // Define specific parameters for ally commands
        command.getCommandParameters().put("AllyActions", new CommandParameter[] {
            CommandParameter.newEnum("action", new CommandEnum("Ally", Arrays.asList("ally"))),
            CommandParameter.newEnum("faction", new CommandEnum("FactionName", factionNames)),
            CommandParameter.newEnum("action", new CommandEnum("AllyAction", Arrays.asList("request", "accept", "reject", "dissolve")))
        });

        // Define specific parameters for invite command
        command.getCommandParameters().put("InviteActions", new CommandParameter[] {
            CommandParameter.newEnum("action", new CommandEnum("InviteAction", Arrays.asList("invite"))),
            CommandParameter.newEnum("player", new CommandEnum("PlayerName", getOnlinePlayers()))
        });
    }

    private static List<String> getFactionPlayers() {
        DatabaseManager dbManager = RoyalKingdomsCore.getInstance().getDataModel().getDatabaseManager();
        return dbManager.loadAllFactions().values().stream()
            .flatMap(faction -> faction.getPlayers().stream())
            .collect(Collectors.toList());
    }

    private static List<String> getOnlinePlayers() {
        return RoyalKingdomsCore.getInstance().getServer().getOnlinePlayers().values().stream()
            .map(Player::getName)
            .collect(Collectors.toList());
    }
}

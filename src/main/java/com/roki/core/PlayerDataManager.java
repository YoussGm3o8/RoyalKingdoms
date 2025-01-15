package com.roki.core;

import com.roki.core.database.DatabaseManager;
import com.roki.core.database.PlayerDataModel;
import cn.nukkit.Player;

public class PlayerDataManager {
    private final DatabaseManager db;

    public PlayerDataManager(DatabaseManager db) {
        this.db = db;
    }

    public PlayerData getPlayerData(Player player) {
        PlayerDataModel dataModel = db.loadPlayerData(player.getUniqueId().toString());
        if (dataModel == null) {
            return new PlayerData(player.getUniqueId().toString(), player.getName(), null, "Member", null, null, 0);
        }
        return new PlayerData(
            dataModel.getUuid(),
            player.getName(),
            dataModel.getFaction(),
            dataModel.getRank(),
            dataModel.getHome(),
            dataModel.getLastLogin(),
            dataModel.getOnlineTime()
        );
    }

    public void savePlayerData(PlayerData playerData) {
        db.savePlayerData(
            playerData.getUuid(),
            playerData.getName(),
            playerData.getFaction(),
            playerData.getRank(),
            playerData.getHome(),
            playerData.getLastLogin(),
            playerData.getOnlineTime()
        );
    }
}


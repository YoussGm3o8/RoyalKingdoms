package com.roki.core.chunkProtection;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockBarrel;
import cn.nukkit.block.BlockChest;
import cn.nukkit.block.BlockHopper;
import cn.nukkit.block.BlockShulkerBox;
import cn.nukkit.entity.item.EntityPrimedTNT;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.block.BlockGrowEvent;
import cn.nukkit.event.block.BlockIgniteEvent;
import cn.nukkit.event.entity.EntityExplodeEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerTeleportEvent;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.element.ElementToggle;
import cn.nukkit.form.response.FormResponseCustom;
import cn.nukkit.form.response.FormResponseSimple;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.particle.DustParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.TextPacket;
import cn.nukkit.scheduler.NukkitRunnable;
import me.onebone.economyapi.EconomyAPI;

import com.roki.core.RoyalKingdomsCore;
import com.roki.core.database.DatabaseManager;
import com.roki.core.PlayerData;
import com.roki.core.Faction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FactionShieldManager implements Listener {
    private final RoyalKingdomsCore plugin;
    private final DatabaseManager db;
    private final Map<String, ProtectedChunkData> protectedChunks = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Boolean>> memberPermissions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Boolean>> allyPermissions = new ConcurrentHashMap<>();
    private final Set<Integer> processedResponses = new HashSet<>();

    public FactionShieldManager(RoyalKingdomsCore plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
        loadProtectedChunks();
        loadPermissions();
    }

    private void loadProtectedChunks() {
        List<ProtectedChunkData> loadedChunks = db.loadAllProtectedChunks();
        for (ProtectedChunkData chunk : loadedChunks) {
            String chunkKey = getChunkKey(chunk.getWorldName(), chunk.getChunkX(), chunk.getChunkZ());
            protectedChunks.put(chunkKey, chunk);
        }
        plugin.getLogger().info("Loaded " + loadedChunks.size() + " protected chunks.");
    }

    private void loadPermissions() {
        // Load member and ally permissions from the database
        // This is a placeholder, implement the actual loading logic
        // Example:
        // memberPermissions = db.loadMemberPermissions();
        // allyPermissions = db.loadAllyPermissions();
    }

    public void saveProtectedChunks() {
        db.saveAllProtectedChunks(new ArrayList<>(protectedChunks.values()));
        plugin.getLogger().info("Saved " + protectedChunks.size() + " protected chunks.");
    }

    public void savePermissions() {
        // Save member and ally permissions to the database
        // This is a placeholder, implement the actual saving logic
        // Example:
        // db.saveMemberPermissions(memberPermissions);
        // db.saveAllyPermissions(allyPermissions);
    }

    private void claimChunk(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        String factionName = playerData.getFaction();
        Faction faction = db.getFactionInfo(factionName);

        if (!"world".equals(player.getLevel().getName())) {
            player.sendMessage("§cYou can only claim land in the world 'world'.");
            return;
        }

        String chunkKey = getChunkKey(player.getLevel().getName(), player.getChunkX(), player.getChunkZ());
        if (protectedChunks.containsKey(chunkKey)) {
            player.sendMessage("§cThis chunk is already claimed.");
            return;
        }

        if (protectedChunks.values().stream().filter(chunk -> chunk.getFactionName().equals(factionName)).count() >= 12) {
            player.sendMessage("§cYour faction has reached the maximum number of claims (12).");
            return;
        }

        if (faction.getKills() < 10 || faction.getVaultBalance() < 128000) {
            player.sendMessage("§cYour faction needs at least 10 kills and $128,000 in the vault to claim land.");
            return;
        }

        if (!isNeighboringChunk(factionName, player.getLevel().getName(), player.getChunkX(), player.getChunkZ())) {
            player.sendMessage("§cYou can only claim neighboring chunks.");
            return;
        }

        ProtectedChunkData newChunk = new ProtectedChunkData(factionName, player.getLevel().getName(), player.getChunkX(), player.getChunkZ());
        newChunk.setShieldHealth(64);
        protectedChunks.put(chunkKey, newChunk);

        faction.setKills(faction.getKills() - 10);
        faction.setVaultBalance(faction.getVaultBalance() - 128000);
        db.saveFaction(faction);

        player.sendMessage("§aChunk claimed successfully!");
        saveProtectedChunks();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        String chunkKey = getChunkKey(event.getBlock().getLevel().getName(), event.getBlock().getChunkX(), event.getBlock().getChunkZ());
        ProtectedChunkData chunk = protectedChunks.get(chunkKey);
        String factionName = db.getPlayerFaction(player.getUniqueId().toString());

        if (chunk != null && (factionName == null || !factionName.equals(chunk.getFactionName()))) {
            event.setCancelled(true);
            player.sendTitle("§cFaction Shield", "§4Cannot Build Here!", 10, 70, 20);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String chunkKey = getChunkKey(event.getBlock().getLevel().getName(), event.getBlock().getChunkX(), event.getBlock().getChunkZ());
        ProtectedChunkData chunk = protectedChunks.get(chunkKey);
        String factionName = db.getPlayerFaction(player.getUniqueId().toString());

        if (chunk != null && (factionName == null || !factionName.equals(chunk.getFactionName()))) {
            event.setCancelled(true);
            player.sendTitle("§cFaction Shield", "§4Cannot Break Here!", 10, 70, 20);
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        String chunkKey = getChunkKey(event.getTo());
        ProtectedChunkData chunk = protectedChunks.get(chunkKey);
        String factionName = db.getPlayerFaction(player.getUniqueId().toString());

        if (chunk != null && (factionName == null || !factionName.equals(chunk.getFactionName()))) {
            event.setCancelled(true);
            player.sendTitle("§cFaction Shield", "§4Cannot Teleport Here!", 10, 70, 20);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        String chunkKey = getChunkKey(event.getTo());
        ProtectedChunkData chunk = protectedChunks.get(chunkKey);
        String factionName = db.getPlayerFaction(player.getUniqueId().toString());

        if (chunk != null && (factionName == null || !factionName.equals(chunk.getFactionName()))) {
            if (!getAllyPermission(player, "enter_chunk")) {
                player.sendMessage("§cYou cannot enter this chunk. It is protected by a faction shield.");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        List<ProtectedChunkData> affectedChunks = new ArrayList<>();
        Player igniter = null;

        if (event.getEntity() instanceof EntityPrimedTNT) {
            EntityPrimedTNT tnt = (EntityPrimedTNT) event.getEntity();
            if (tnt.getSource() instanceof Player) {
                igniter = (Player) tnt.getSource();
            }
        }

        for (Block block : event.getBlockList()) {
            String chunkKey = getChunkKey(block.getLevel().getName(), block.getChunkX(), block.getChunkZ());
            ProtectedChunkData chunk = protectedChunks.get(chunkKey);
            if (chunk != null) {
                affectedChunks.add(chunk);
            }
        }

        if (!affectedChunks.isEmpty()) {
            event.setCancelled(true);
            return;
        }
    }

    private boolean isNeighboringChunk(String factionName, String worldName, int chunkX, int chunkZ) {
        return protectedChunks.values().stream()
                .filter(chunk -> chunk.getFactionName().equals(factionName))
                .anyMatch(chunk -> Math.abs(chunk.getChunkX() - chunkX) <= 1 && Math.abs(chunk.getChunkZ() - chunkZ) <= 1 && chunk.getWorldName().equals(worldName));
    }

    private String getChunkKey(String worldName, int chunkX, int chunkZ) {
        return worldName + ":" + chunkX + ":" + chunkZ;
    }

    private String getChunkKey(Position pos) {
        if (pos == null || pos.level == null) {
            return null;
        }
        int chunkX = pos.getChunkX();
        int chunkZ = pos.getChunkZ();
        return pos.level.getName() + ":" + chunkX + ":" + chunkZ;
    }

    private String getChunkKey(PlayerMoveEvent event) {
        return getChunkKey(event.getTo());
    }

    public boolean handleShieldCommand(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        String factionName = playerData.getFaction();

        if (factionName == null) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }

        if (!playerData.isLeader()) {
            player.sendMessage("§cOnly the faction leader can claim land.");
            return true;
        }

        showShieldGui(player);
        return true;
    }

    private void showShieldGui(Player player) {
        FormWindowSimple shieldGui = new FormWindowSimple("Claim Land", "Select an action:");

        shieldGui.addButton(new ElementButton("Claim Chunk"));
        shieldGui.addButton(new ElementButton("View Claims"));
        shieldGui.addButton(new ElementButton("Manage Permissions"));
        shieldGui.addButton(new ElementButton("Back"));

        player.showFormWindow(shieldGui);
    }

    public void handleShieldGuiResponse(Player player, FormResponseSimple response) {
        if (response == null) {
            return;
        }
        String buttonText = response.getClickedButton().getText();

        switch (buttonText) {
            case "Claim Chunk":
                claimChunk(player);
                break;
            case "View Claims":
                viewClaims(player);
                break;
            case "Manage Permissions":
                showPermissionGui(player);
                break;
            case "Back":
                plugin.getCommandController().handleFactionGuiCommand(player);
                break;
            default:
                player.sendMessage("§cInvalid action.");
                break;
        }
    }

    private void viewClaims(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        String factionName = playerData.getFaction();
        List<ProtectedChunkData> claims = new ArrayList<>(protectedChunks.values());
        claims.removeIf(chunk -> !chunk.getFactionName().equals(factionName));

        if (claims.isEmpty()) {
            player.sendMessage("§cYour faction has no claimed chunks.");
            return;
        }

        player.sendMessage("§aYour faction's claimed chunks:");
        for (ProtectedChunkData chunk : claims) {
            player.sendMessage("§7- Chunk at (" + chunk.getChunkX() + ", " + chunk.getChunkZ() + ")");
        }
    }

    private void showPermissionGui(Player player) {
        FormWindowSimple permissionGui = new FormWindowSimple("Manage Permissions", "Select a group:");

        permissionGui.addButton(new ElementButton("Members"));
        permissionGui.addButton(new ElementButton("Allies"));
        permissionGui.addButton(new ElementButton("Back"));

        player.showFormWindow(permissionGui);
    }

    public void handlePermissionGuiResponse(Player player, FormResponseSimple response) {
        if (response == null) {
            return;
        }
        String buttonText = response.getClickedButton().getText();

        switch (buttonText) {
            case "Members":
                showMemberPermissionGui(player);
                break;
            case "Allies":
                showAllyPermissionGui(player);
                break;
            case "Back":
                showShieldGui(player);
                break;
            default:
                player.sendMessage("§cInvalid action.");
                break;
        }
    }

    private void showMemberPermissionGui(Player player) {
        FormWindowCustom memberPermissionGui = new FormWindowCustom("Member Permissions");

        memberPermissionGui.addElement(new ElementToggle("Break Blocks", getMemberPermission(player, "break_blocks")));
        memberPermissionGui.addElement(new ElementToggle("Place Blocks", getMemberPermission(player, "place_blocks")));
        memberPermissionGui.addElement(new ElementToggle("Open Chests", getMemberPermission(player, "open_chests")));
        memberPermissionGui.addElement(new ElementToggle("Open Doors", getMemberPermission(player, "open_doors")));

        player.showFormWindow(memberPermissionGui);
    }

    private void showAllyPermissionGui(Player player) {
        FormWindowCustom allyPermissionGui = new FormWindowCustom("Ally Permissions");

        allyPermissionGui.addElement(new ElementToggle("Break Blocks", getAllyPermission(player, "break_blocks")));
        allyPermissionGui.addElement(new ElementToggle("Place Blocks", getAllyPermission(player, "place_blocks")));
        allyPermissionGui.addElement(new ElementToggle("Open Chests", getAllyPermission(player, "open_chests")));
        allyPermissionGui.addElement(new ElementToggle("Open Doors", getAllyPermission(player, "open_doors")));
        allyPermissionGui.addElement(new ElementToggle("Enter Chunk", getAllyPermission(player, "enter_chunk")));

        player.showFormWindow(allyPermissionGui);
    }

    private boolean getMemberPermission(Player player, String permission) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        if (playerData.isOfficer() || playerData.isLeader()) {
            return true; // Officers can perform all actions
        }
        String factionName = playerData.getFaction();
        return memberPermissions.getOrDefault(factionName, new HashMap<>()).getOrDefault(permission, false);
    }

    private boolean getAllyPermission(Player player, String permission) {
        String factionName = db.getPlayerFaction(player.getUniqueId().toString());
        return allyPermissions.getOrDefault(factionName, new HashMap<>()).getOrDefault(permission, false);
    }

    public void handleMemberPermissionGuiResponse(Player player, FormResponseCustom response) {
        if (response == null) {
            return;
        }
        String factionName = db.getPlayerFaction(player.getUniqueId().toString());
        Map<String, Boolean> permissions = memberPermissions.computeIfAbsent(factionName, k -> new HashMap<>());

        permissions.put("break_blocks", response.getToggleResponse(0));
        permissions.put("place_blocks", response.getToggleResponse(1));
        permissions.put("open_chests", response.getToggleResponse(2));
        permissions.put("open_doors", response.getToggleResponse(3));

        player.sendMessage("§aMember permissions updated successfully!");
        savePermissions();
    }

    public void handleAllyPermissionGuiResponse(Player player, FormResponseCustom response) {
        if (response == null) {
            return;
        }
        String factionName = db.getPlayerFaction(player.getUniqueId().toString());
        Map<String, Boolean> permissions = allyPermissions.computeIfAbsent(factionName, k -> new HashMap<>());

        permissions.put("break_blocks", response.getToggleResponse(0));
        permissions.put("place_blocks", response.getToggleResponse(1));
        permissions.put("open_chests", response.getToggleResponse(2));
        permissions.put("open_doors", response.getToggleResponse(3));
        permissions.put("enter_chunk", response.getToggleResponse(4));

        player.sendMessage("§aAlly permissions updated successfully!");
        savePermissions();
    }
}
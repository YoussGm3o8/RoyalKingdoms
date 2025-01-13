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
    private final Map<String, List<ProtectedChunkData>> protectedChunks = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Boolean>> memberPermissions = new HashMap<>();
    private final Map<String, Map<String, Boolean>> allyPermissions = new HashMap<>();
    private final Set<Integer> processedResponses = new HashSet<>();

    public FactionShieldManager(RoyalKingdomsCore plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
        loadProtectedChunks();
    }

    private void loadProtectedChunks() {
        List<ProtectedChunkData> loadedChunks = db.loadAllProtectedChunks();
        for (ProtectedChunkData chunk : loadedChunks) {
            String chunkKey = chunk.getWorldName() + ":" + chunk.getChunkX() + ":" + chunk.getChunkZ();
            protectedChunks.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(chunk);
        }
        plugin.getLogger().info("Loaded " + loadedChunks.size() + " protected chunks.");
    }

    public void saveProtectedChunks() {
        db.saveAllProtectedChunks(new ArrayList<>(protectedChunks.values().stream().flatMap(List::stream).toList()));
        plugin.getLogger().info("Saved " + protectedChunks.size() + " protected chunks.");
    }

    private void claimChunk(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        String factionName = playerData.getFaction();
        Faction faction = db.getFactionInfo(factionName);

        if (!"world".equals(player.getLevel().getName())) {
            player.sendMessage("§cYou can only claim land in the world 'world'.");
            return;
        }

        ProtectedChunkData chunk = new ProtectedChunkData(db.getPlayerFaction(player.getUniqueId().toString()), player.getLevel().getName(), player.getChunkX(), player.getChunkZ());

        // Check if the chunk is already claimed
        for (List<ProtectedChunkData> protectedChunkList : protectedChunks.values()) {
            for (ProtectedChunkData protectedChunk : protectedChunkList) {
                if (protectedChunk.getChunk(player.getLevel()).equals(chunk)) {
                    player.sendMessage("§cThis chunk is already claimed by another faction.");
                    return;
                }
            }
        }

        if (protectedChunks.containsKey(factionName) && protectedChunks.get(factionName).size() >= 12) {
            player.sendMessage("§cYour faction has reached the maximum number of claims (12).");
            return;
        }

        if (faction.getKills() < 10 && faction.getVaultBalance() < 128000) {
            player.sendMessage("§cYour faction needs at least 10 kills to claim land and $128,000 in the vault.");
            return;
        }

        if (faction.getKills() < 10) {
            player.sendMessage("§cYour faction needs at least 10 kills to claim land.");
            return;
        }

        if (faction.getVaultBalance() < 128000) {
            player.sendMessage("§cYour faction needs at least $128,000 in the vault to claim land.");
            return;
        }

        if (!isNeighboringChunk(factionName, chunk)) {
            player.sendMessage("§cYou can only claim neighboring chunks.");
            return;
        }

        protectedChunks.computeIfAbsent(factionName, k -> new ArrayList<>()).add(new ProtectedChunkData(factionName, player.getLevel().getName(), player.getChunkX(), player.getChunkZ()));
        protectedChunks.get(factionName).get(protectedChunks.get(factionName).size() - 1).setShieldHealth(64);

        faction.setKills(faction.getKills() - 10);
        faction.setVaultBalance(faction.getVaultBalance() - 128000);
        db.saveFaction(faction);

        player.sendMessage("§aChunk claimed successfully!");
        saveProtectedChunks();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        String chunkKey = getChunkKey(event.getBlock().getLocation());
        ProtectedChunkData chunk = getProtectedChunk(chunkKey);
        String factionName = db.getPlayerFaction(player.getUniqueId().toString());

        if (chunk != null && (factionName == null || !factionName.equals(chunk.getFactionName()))) {
            event.setCancelled(true);
            player.sendTitle("§cFaction Shield", "§4Cannot Build Here!", 10, 70, 20);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String chunkKey = getChunkKey(event.getBlock().getLocation());
        ProtectedChunkData chunk = getProtectedChunk(chunkKey);
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
        ProtectedChunkData chunk = getProtectedChunk(chunkKey);
        String factionName = db.getPlayerFaction(player.getUniqueId().toString());

        if (chunk != null && (factionName == null || !factionName.equals(chunk.getFactionName()))) {
            event.setCancelled(true);
            player.sendTitle("§cFaction Shield", "§4Cannot Teleport Here!", 10, 70, 20);
        }
    }

    private boolean isNeighboringChunk(String factionName, ProtectedChunkData chunk) {
        List<ProtectedChunkData> claims = protectedChunks.get(factionName);
        if (claims == null || claims.isEmpty()) {
            return true;
        }

        for (ProtectedChunkData claimedChunk : claims) {
            if (Math.abs(claimedChunk.getChunkX() - chunk.getChunkX()) <= 1 && Math.abs(claimedChunk.getChunkZ() - chunk.getChunkZ()) <= 1) {
                return true;
            }
        }

        return false;
    }

    private void viewClaims(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        String factionName = playerData.getFaction();
        List<ProtectedChunkData> claims = protectedChunks.get(factionName);

        if (claims == null || claims.isEmpty()) {
            player.sendMessage("§cYour faction has no claimed chunks.");
            return;
        }

        player.sendMessage("§aYour faction's claimed chunks:");
        for (ProtectedChunkData chunk : claims) {
            player.sendMessage("§7- Chunk at (" + chunk.getChunkX() + ", " + chunk.getChunkZ() + ")");
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
            String chunkKey = getChunkKey(block.getLocation());
            ProtectedChunkData chunk = getProtectedChunk(chunkKey);
            if (chunk != null && (igniter == null || isAdjacentChunk(protectedChunks.get(chunk.getFactionName()), chunk))) {
                affectedChunks.add(chunk);
            }
        }

        event.getBlockList().removeIf(block -> {
            String chunkKey = getChunkKey(block.getLocation());
            return affectedChunks.contains(getProtectedChunk(chunkKey));
        });

        for (ProtectedChunkData chunk : affectedChunks) {
            int health = chunk.getShieldHealth();
            health--;

            if (health <= 0) {
                protectedChunks.get(chunk.getFactionName()).remove(chunk);

                String factionName = chunk.getFactionName();
                if (factionName != null) {
                    List<String> factionMembers = db.getFactionPlayers(factionName);
                    for (String memberName : factionMembers) {
                        Player member = plugin.getServer().getPlayer(memberName);
                        if (member != null) {
                            sendBigTextMessage(member, "§cA faction shield has been destroyed!");
                        }
                    }
                }
            } else {
                chunk.setShieldHealth(health);
                if (igniter != null) {
                    igniter.sendMessage("§cFaction shield health: " + health);
                }
                showShieldDamageEffect(chunk, event.getPosition());
            }
        }
        saveProtectedChunks();
    }

    private boolean isAdjacentChunk(List<ProtectedChunkData> claimedChunks, ProtectedChunkData chunk) {
        for (ProtectedChunkData claimedChunk : claimedChunks) {
            if (Math.abs(claimedChunk.getChunkX() - chunk.getChunkX()) <= 1 && Math.abs(claimedChunk.getChunkZ() - chunk.getChunkZ()) <= 1) {
                return true;
            }
        }
        return false;
    }

    private void sendBigTextMessage(Player player, String message) {
        TextPacket packet = new TextPacket();
        packet.type = TextPacket.TYPE_TRANSLATION;
        packet.message = "title";
        packet.parameters = new String[]{message};
        packet.isLocalized = false;
        player.dataPacket(packet);
    }

    private void showShieldDamageEffect(ProtectedChunkData chunk, Vector3 explosionPosition) {
        Level level = chunk.getLevel();
        int chunkX = chunk.getChunkX() << 4;
        int chunkZ = chunk.getChunkZ() << 4;

        // Show the shield walls for 1 minute
        new NukkitRunnable() {
            int counter = 0;

            @Override
            public void run() {
                if (counter >= 60) {
                    this.cancel();
                    return;
                }

                for (int x = chunkX; x < chunkX + 16; x++) {
                    for (int z = chunkZ; z < chunkZ + 16; z++) {
                        level.addParticle(new DustParticle(new Vector3(x, level.getHighestBlockAt(x, z), z), 0, 0, 255));
                    }
                }

                counter++;
            }
        }.runTaskTimer(plugin, 0, 20);

        // Show the explosion area in red for 3 seconds
        new NukkitRunnable() {
            @Override
            public void run() {
                for (int x = chunkX; x < chunkX + 16; x++) {
                    for (int z = chunkZ; z < chunkZ + 16; z++) {
                        if (explosionPosition.distance(new Vector3(x, level.getHighestBlockAt(x, z), z)) <= 4) {
                            level.addParticle(new DustParticle(new Vector3(x, level.getHighestBlockAt(x, z), z), 255, 0, 0));
                        }
                    }
                }
            }
        }.runTaskLater(plugin, 0);

        new NukkitRunnable() {
            @Override
            public void run() {
                for (int x = chunkX; x < chunkX + 16; x++) {
                    for (int z = chunkZ; z < chunkZ + 16; z++) {
                        if (explosionPosition.distance(new Vector3(x, level.getHighestBlockAt(x, z), z)) <= 4) {
                            level.addParticle(new DustParticle(new Vector3(x, level.getHighestBlockAt(x, z), z), 0, 0, 255));
                        }
                    }
                }
            }
        }.runTaskLater(plugin, 60);
    }

    public void handlePlayerMoveEvent(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        String chunkKey = getChunkKey(player.getPosition());
        ProtectedChunkData chunk = getProtectedChunk(chunkKey);
        String playerFaction = db.getPlayerFaction(player.getUniqueId().toString());

        if (chunk != null && (playerFaction == null || !playerFaction.equals(chunk.getFactionName()))) {
            if (!getAllyPermission(player, "enter_chunk")) {
                player.sendMessage("§cYou cannot enter this chunk. It is protected by a faction shield.");
                event.setCancelled(true);
            }
        }
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
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
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

    private void showPermissionGui(Player player) {
        FormWindowSimple permissionGui = new FormWindowSimple("Manage Permissions", "Select a group:");

        permissionGui.addButton(new ElementButton("Members"));
        permissionGui.addButton(new ElementButton("Allies"));
        permissionGui.addButton(new ElementButton("Back"));

        player.showFormWindow(permissionGui);
    }

    public void handlePermissionGuiResponse(Player player, FormResponseSimple response) {
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
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
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
        String factionName = db.getPlayerFaction(player.getUniqueId().toString());
        Map<String, Boolean> permissions = memberPermissions.computeIfAbsent(factionName, k -> new HashMap<>());

        permissions.put("break_blocks", response.getToggleResponse(0));
        permissions.put("place_blocks", response.getToggleResponse(1));
        permissions.put("open_chests", response.getToggleResponse(2));
        permissions.put("open_doors", response.getToggleResponse(3));

        player.sendMessage("§aMember permissions updated successfully!");
    }

    public void handleAllyPermissionGuiResponse(Player player, FormResponseCustom response) {
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
        String factionName = db.getPlayerFaction(player.getUniqueId().toString());
        Map<String, Boolean> permissions = allyPermissions.computeIfAbsent(factionName, k -> new HashMap<>());

        permissions.put("break_blocks", response.getToggleResponse(0));
        permissions.put("place_blocks", response.getToggleResponse(1));
        permissions.put("open_chests", response.getToggleResponse(2));
        permissions.put("open_doors", response.getToggleResponse(3));
        permissions.put("enter_chunk", response.getToggleResponse(4));

        player.sendMessage("§aAlly permissions updated successfully!");
    }

    public void handleExplosionEvent(EntityExplodeEvent event) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'handleExplosionEvent'");
    }

    private String getChunkKey(Position pos) {
        if (pos == null || pos.level == null) {
            return null;
        }
        int chunkX = pos.getChunkX();
        int chunkZ = pos.getChunkZ();
        return pos.level.getName() + ":" + chunkX + ":" + chunkZ;
    }

    private ProtectedChunkData getProtectedChunk(String chunkKey) {
        List<ProtectedChunkData> chunks = protectedChunks.get(chunkKey);
        if (chunks != null && !chunks.isEmpty()) {
            return chunks.get(0); // Assuming only one chunk per key
        }
        return null;
    }
}
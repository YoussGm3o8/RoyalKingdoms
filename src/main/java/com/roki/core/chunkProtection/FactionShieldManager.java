package com.roki.core.chunkProtection;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockBarrel;
import cn.nukkit.block.BlockChest;
import cn.nukkit.block.BlockHopper;
import cn.nukkit.block.BlockShulkerBox;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityArmorStand;
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
import cn.nukkit.form.element.ElementLabel;
import cn.nukkit.form.element.ElementToggle;
import cn.nukkit.form.element.ElementInput;
import cn.nukkit.form.response.FormResponseCustom;
import cn.nukkit.form.response.FormResponseSimple;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.particle.DustParticle;
import cn.nukkit.level.particle.FloatingTextParticle;
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
import java.util.stream.Collectors;

public class FactionShieldManager implements Listener {
    private final RoyalKingdomsCore plugin;
    private final DatabaseManager db;
    private final Map<String, ProtectedChunkData> protectedChunks = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Boolean>> memberPermissions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Boolean>> allyPermissions = new ConcurrentHashMap<>();
    private final Set<Integer> processedResponses = new HashSet<>();
    private final Map<String, NukkitRunnable> activeHealthAnimations = new ConcurrentHashMap<>();
    private final Map<String, Long> activeBeacons = new ConcurrentHashMap<>();
    private final Map<String, Integer> shieldReactorHealth = new ConcurrentHashMap<>();
    private final Map<String, Long> lastShieldDamageTime = new ConcurrentHashMap<>();
    private final int REACTOR_COOLDOWN = 600000; // 10 minutes in milliseconds
    private final int KILLS_TO_HEALTH = 250; // Health per kill
    private final int MONEY_TO_HEALTH = 250; // Health per 64000 money
    private final int MAX_REACTOR_HEALTH = 50000;
    private final double MONEY_PER_HEALTH = 256.0; // 64000/250 = 256 per health point

    public FactionShieldManager(RoyalKingdomsCore plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
        loadProtectedChunks();
        loadPermissions();
        loadShieldReactorData(); // Add this line
        startShieldHealingCheck();
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
        memberPermissions.putAll(db.loadMemberPermissions());
        allyPermissions.putAll(db.loadAllyPermissions());
        plugin.getLogger().info("Loaded member and ally permissions.");
    }

    private void loadShieldReactorData() {
        shieldReactorHealth.putAll(db.loadShieldReactorHealth());
        lastShieldDamageTime.putAll(db.loadShieldReactorDamageTimes());
        plugin.getLogger().info("Loaded shield reactor data for " + shieldReactorHealth.size() + " factions.");
    }

    public void saveProtectedChunks() {
        db.saveAllProtectedChunks(new ArrayList<>(protectedChunks.values()));
        plugin.getLogger().info("Saved " + protectedChunks.size() + " protected chunks.");
    }

    public void savePermissions() {
        db.saveMemberPermissions(memberPermissions);
        db.saveAllyPermissions(allyPermissions);
        plugin.getLogger().info("Saved member and ally permissions.");
    }

    public void saveShieldReactorData() {
        db.saveShieldReactorData(shieldReactorHealth, lastShieldDamageTime);
        plugin.getLogger().info("Saved shield reactor data.");
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

        long factionClaims = protectedChunks.values().stream().filter(chunk -> chunk.getFactionName().equals(factionName)).count();
        if (factionClaims >= 12) {
            player.sendMessage("§cYour faction has reached the maximum number of claims (12).");
            return;
        }

        if (faction.getKills() < 10 || faction.getVaultBalance() < 128000) {
            player.sendMessage("§cYour faction needs at least 10 kills and $128,000 in the vault to claim land.");
            return;
        }

        if (factionClaims > 0 && !isNeighboringChunk(factionName, player.getLevel().getName(), player.getChunkX(), player.getChunkZ())) {
            player.sendMessage("§cYou can only claim neighboring chunks.");
            return;
        }

        ProtectedChunkData newChunk = new ProtectedChunkData(factionName, player.getLevel().getName(), player.getChunkX(), player.getChunkZ());
        newChunk.setShieldHealth(5000);
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
        if (player == null || event.getTo() == null) {
            return;
        }

        String chunkKey = getChunkKey(event.getTo());
        if (chunkKey == null) {
            return;
        }

        ProtectedChunkData chunk = protectedChunks.get(chunkKey);
        if (chunk == null) {
            return;
        }

        String factionName = db.getPlayerFaction(player.getUniqueId().toString());
        if (factionName == null || !factionName.equals(chunk.getFactionName())) {
            if (factionName == null || !getAllyPermission(player, "enter_chunk")) {
                player.sendMessage("§cYou cannot teleport to this chunk. It is protected by a faction shield.");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player == null || event.getTo() == null) {
            return;
        }

        String chunkKey = getChunkKey(event.getTo());
        if (chunkKey == null) {
            return;
        }

        ProtectedChunkData chunk = protectedChunks.get(chunkKey);
        if (chunk == null) {
            return;
        }

        String factionName = db.getPlayerFaction(player.getUniqueId().toString());
        if (factionName == null || !factionName.equals(chunk.getFactionName())) {
            if (factionName == null || !getAllyPermission(player, "enter_chunk")) {
                player.sendMessage("§cYou cannot enter this chunk. It is protected by a faction shield.");
                showShieldImpact(event.getTo()); // Add impact effect where player hits shield
                showShieldBeacons(chunk); // Add corner beacons
                event.setCancelled(true);
            } else {
                notifyPlayerOfShield(player);
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof EntityPrimedTNT)) {
            return;
        }

        EntityPrimedTNT tnt = (EntityPrimedTNT) event.getEntity();
        Player igniter = tnt.getSource() instanceof Player ? (Player) tnt.getSource() : null;
        Position explosionPos = event.getPosition();
        
        // Get the chunk the TNT exploded in
        String explosionChunkKey = getChunkKey(explosionPos.level.getName(), explosionPos.getChunkX(), explosionPos.getChunkZ());
        ProtectedChunkData directHitChunk = protectedChunks.get(explosionChunkKey);
        
        // Set of chunks to check (including adjacent chunks for blast radius)
        Set<ProtectedChunkData> affectedChunks = new HashSet<>();
        
        // Check direct hit chunk
        if (directHitChunk != null) {
            affectedChunks.add(directHitChunk);
        }
        
        // Check nearby chunks (for blast radius)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue; // Skip center chunk (already checked)
                
                String nearbyChunkKey = getChunkKey(
                    explosionPos.level.getName(),
                    explosionPos.getChunkX() + dx,
                    explosionPos.getChunkZ() + dz
                );
                
                ProtectedChunkData nearbyChunk = protectedChunks.get(nearbyChunkKey);
                if (nearbyChunk != null) {
                    affectedChunks.add(nearbyChunk);
                }
            }
        }

        if (!affectedChunks.isEmpty()) {
            event.setCancelled(true);
            
            for (ProtectedChunkData chunk : affectedChunks) {
                // Calculate damage based on TNT position relative to shield
                int damage = calculateTNTDamage(explosionPos, chunk);
                // Use reactor to absorb damage
                damage = handleShieldDamage(chunk.getFactionName(), damage);
                
                if (damage > 0) {
                    int newHealth = chunk.getShieldHealth() - damage;
                    if (newHealth <= 0) {
                        protectedChunks.remove(getChunkKey(chunk.getWorldName(), chunk.getChunkX(), chunk.getChunkZ()));
                        saveProtectedChunks();
                        plugin.getLogger().info("Shield for chunk (" + chunk.getChunkX() + ", " + chunk.getChunkZ() + ") has been destroyed.");
                        
                        // Modified shield destruction announcement
                        if (igniter != null) {
                            // Send message to the destroyer
                            igniter.sendTitle(
                                "§4§lSHIELD DESTROYED!", 
                                "§cYou have broken through " + chunk.getFactionName() + "'s defenses!", 
                                20, 100, 20
                            );
                            
                            // Notify only faction members whose shield was destroyed
                            String destroyedFactionName = chunk.getFactionName();
                            for (Player p : plugin.getServer().getOnlinePlayers().values()) {
                                String playerFaction = db.getPlayerFaction(p.getUniqueId().toString());
                                if (destroyedFactionName.equals(playerFaction)) {
                                    p.sendTitle(
                                        "§4§lSHIELD DESTROYED!", 
                                        "§c" + igniter.getName() + " has broken through your defenses!", 
                                        20, 100, 20
                                    );
                                    p.sendMessage("§4§l[SHIELD DESTROYED] §r§cYour shield at (" + 
                                        chunk.getChunkX() * 16 + ", " + chunk.getChunkZ() * 16 + 
                                        ") has been destroyed by " + igniter.getName() + "!");
                                }
                            }
                        }
                    } else {
                        chunk.setShieldHealth(newHealth);
                        if (igniter != null) {
                            showShieldHealthHologram(igniter, chunk);
                            showShieldImpact(explosionPos);
                            showShieldBeacons(chunk);
                        }
                    }
                }
            }
            saveProtectedChunks();
        }
    }

    private int calculateTNTDamage(Position tntPos, ProtectedChunkData chunk) {
        // Calculate chunk boundaries
        int chunkStartX = chunk.getChunkX() * 16;
        int chunkEndX = chunkStartX + 15;
        int chunkStartZ = chunk.getChunkZ() * 16;
        int chunkEndZ = chunkStartZ + 15;
        
        // Check if TNT is inside the chunk
        if (tntPos.getFloorX() >= chunkStartX && tntPos.getFloorX() <= chunkEndX &&
            tntPos.getFloorZ() >= chunkStartZ && tntPos.getFloorZ() <= chunkEndZ) {
            return 64; // Direct hit inside shield
        }
        
        // Check if TNT is within 3 blocks of chunk boundaries
        double minDistance = Double.MAX_VALUE;
        
        // Check distance to chunk borders
        minDistance = Math.min(minDistance, Math.abs(tntPos.getX() - chunkStartX));
        minDistance = Math.min(minDistance, Math.abs(tntPos.getX() - chunkEndX));
        minDistance = Math.min(minDistance, Math.abs(tntPos.getZ() - chunkStartZ));
        minDistance = Math.min(minDistance, Math.abs(tntPos.getZ() - chunkEndZ));
        
        return minDistance <= 3 ? 32 : 0; // 32 damage if within 3 blocks, 0 if further
    }

    private void showShieldHealthHologram(Player player, ProtectedChunkData chunk) {
        int maxHealth = 5000;
        int currentHealth = chunk.getShieldHealth();
        String playerKey = player.getName();

        // If there's an existing animation, just update its target health
        NukkitRunnable existingAnimation = activeHealthAnimations.get(playerKey);
        if (existingAnimation != null) {
            if (existingAnimation instanceof ShieldHealthAnimation) {
                ((ShieldHealthAnimation) existingAnimation).updateTargetHealth(currentHealth);
                return;
            }
        }

        // Create new animation if none exists
        ShieldHealthAnimation animation = new ShieldHealthAnimation(player, currentHealth, maxHealth);
        activeHealthAnimations.put(playerKey, animation);
        animation.runTaskTimer(plugin, 0, 1);
    }

    private class ShieldHealthAnimation extends NukkitRunnable {
        private final Player player;
        private final int maxHealth;
        private int displayHealth;
        private int targetHealth;
        private int tick = 0;
        private final int animationDuration = 40;
        private int idleTime = 0;
        private final int maxIdleTime = 100; // 5 seconds (20 ticks per second)

        public ShieldHealthAnimation(Player player, int targetHealth, int maxHealth) {
            this.player = player;
            this.maxHealth = maxHealth;
            this.targetHealth = targetHealth;
            this.displayHealth = targetHealth + 50; // Start animation from slightly higher
        }

        public void updateTargetHealth(int newTargetHealth) {
            if (this.targetHealth != newTargetHealth) {
                this.displayHealth = this.targetHealth; // Start from current health
                this.targetHealth = newTargetHealth;
                this.tick = 0; // Reset animation
                this.idleTime = 0; // Reset idle time when receiving new damage
            }
        }

        @Override
        public void run() {
            if (!player.isOnline()) {
                activeHealthAnimations.remove(player.getName());
                this.cancel();
                return;
            }

            if (tick >= animationDuration) {
                idleTime++;
                if (idleTime >= maxIdleTime) {
                    // Clear the action bar and cancel the task after 5 seconds of no updates
                    player.sendActionBar("");
                    activeHealthAnimations.remove(player.getName());
                    this.cancel();
                    return;
                }
                // Keep showing the final health bar without animation
                showHealthBar(targetHealth);
                return;
            }

            // Reset idle time when there's an animation happening
            idleTime = 0;
            
            // Smooth transition effect
            int currentDisplay = (int) (displayHealth - (((float)(displayHealth - targetHealth) / animationDuration) * tick));
            showHealthBar(currentDisplay);
            tick++;
        }

        private void showHealthBar(int health) {
            int bars = 20;
            int filledBars = (int)((float)health / maxHealth * bars);
            StringBuilder healthBar = new StringBuilder();
            
            for(int i = 0; i < filledBars; i++) {
                healthBar.append("§c▊");
            }
            for(int i = filledBars; i < bars; i++) {
                healthBar.append("§7▊");
            }

            String message = String.format("§c§lShield Health: §f%,d §7/ §f%,d %s", 
                health, 
                maxHealth,
                healthBar.toString()
            );
            
            player.sendActionBar(message);
        }
    }

    private void showShieldImpact(Position pos) {
        Level level = pos.getLevel();
        Random rand = new Random();
        
        // Create a burst of particles at impact point
        for (int i = 0; i < 15; i++) {
            double offsetX = rand.nextDouble() * 2 - 1;
            double offsetY = rand.nextDouble() * 2 - 1;
            double offsetZ = rand.nextDouble() * 2 - 1;
            
            Vector3 particlePos = pos.add(offsetX, offsetY, offsetZ);
            DustParticle particle = new DustParticle(particlePos, 255, 0, 0); // Red particles
            level.addParticle(particle);
        }
    }

    private void showShieldBeacons(ProtectedChunkData chunk) {
        String chunkKey = getChunkKey(chunk.getWorldName(), chunk.getChunkX(), chunk.getChunkZ());
        
        // Don't create new beacons if they're already active
        if (activeBeacons.containsKey(chunkKey)) {
            return;
        }
        
        // Store the end time for the beacons
        activeBeacons.put(chunkKey, System.currentTimeMillis() + 60000); // 1 minute duration
        
        // Calculate chunk corners
        int baseX = chunk.getChunkX() * 16;
        int baseZ = chunk.getChunkZ() * 16;
        Level level = plugin.getServer().getLevelByName(chunk.getWorldName());
        
        // Create beacon effect task
        new NukkitRunnable() {
            @Override
            public void run() {
                if (System.currentTimeMillis() > activeBeacons.get(chunkKey)) {
                    activeBeacons.remove(chunkKey);
                    this.cancel();
                    return;
                }
                
                // Show beacon particles at each corner
                Position[] corners = {
                    new Position(baseX, 0, baseZ, level),
                    new Position(baseX + 15, 0, baseZ, level),
                    new Position(baseX, 0, baseZ + 15, level),
                    new Position(baseX + 15, 0, baseZ + 15, level)
                };
                
                for (Position corner : corners) {
                    // Get highest block at corner
                    int y = level.getHighestBlockAt(corner.getFloorX(), corner.getFloorZ());
                    // Create beam particles with larger spacing for better performance
                    for (int i = 0; i < 300; i += 2) { // 300 blocks high, particles every 2 blocks
                        Vector3 beamPos = new Vector3(corner.x, y + i, corner.z);
                        DustParticle particle = new DustParticle(beamPos, 255, 0, 0);
                        level.addParticle(particle);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20); // Update every second
    }

    private void notifyPlayerOfShield(Player player) {
        player.sendTitle("§cFaction Shield", "§4You just found a faction shield!", 10, 70, 20);
        player.sendMessage("§cTo break the shield, use TNT to damage it. The shield has 5000 health points.");
    }

    private boolean isNeighboringChunk(String factionName, String worldName, int chunkX, int chunkZ) {
        return protectedChunks.values().stream()
                .filter(chunk -> chunk.getFactionName().equals(factionName) && chunk.getWorldName().equals(worldName))
                .anyMatch(chunk -> Math.abs(chunk.getChunkX() - chunkX) <= 1 && Math.abs(chunk.getChunkZ() - chunkZ) <= 1);
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
        System.out.println("FactionName: " + factionName);

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
        shieldGui.addButton(new ElementButton("Shield Reactor")); // Add new button
        shieldGui.addButton(new ElementButton("Back"));

        player.showFormWindow(shieldGui);
    }

    private void showShieldReactorGui(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        String factionName = playerData.getFaction();
        Faction faction = db.getFactionInfo(factionName);
        
        int reactorHealth = shieldReactorHealth.getOrDefault(factionName, 0);
        Long lastDamage = lastShieldDamageTime.get(factionName);
        boolean onCooldown = lastDamage != null && 
            (System.currentTimeMillis() - lastDamage) < REACTOR_COOLDOWN;

        FormWindowCustom reactorGui = new FormWindowCustom("Shield Reactor");
        
        // Show current reactor health and status
        StringBuilder status = new StringBuilder();
        status.append(String.format("§aReactor Health: %d / %d", reactorHealth, MAX_REACTOR_HEALTH));
        
        if (reactorHealth >= MAX_REACTOR_HEALTH) {
            status.append("\n§6⚡ Reactor is at maximum capacity!");
        }
        
        if (onCooldown) {
            long remainingTime = (lastDamage + REACTOR_COOLDOWN - System.currentTimeMillis()) / 1000;
            status.append(String.format("\n§cCooldown: %d seconds", remainingTime));
        }
        
        reactorGui.addElement(new ElementLabel(status.toString())); // Element index 0

        // Only add input fields if not on cooldown and not at max health
        if (!onCooldown && reactorHealth < MAX_REACTOR_HEALTH) {
            reactorGui.addElement(new ElementLabel(String.format("\n§eKills: %d", faction.getKills()))); // Element index 1
            reactorGui.addElement(new ElementInput("Number of kills to spend (1 kill = 250 health)", "Enter number")); // Element index 2
            
            reactorGui.addElement(new ElementLabel(String.format("\n§eBalance: $%.0f", faction.getVaultBalance()))); // Element index 3
            reactorGui.addElement(new ElementInput("Money to spend ($256 = 1 health)", "Enter amount")); // Element index 4
        }

        player.showFormWindow(reactorGui);
    }

    public void handleShieldReactorGuiResponse(Player player, FormResponseCustom response) {
        if (response == null) return;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        String factionName = playerData.getFaction();
        Faction faction = db.getFactionInfo(factionName);

        Long lastDamage = lastShieldDamageTime.get(factionName);
        if (lastDamage != null && (System.currentTimeMillis() - lastDamage) < REACTOR_COOLDOWN) {
            player.sendMessage("§cShield Reactor is on cooldown!");
            return;
        }

        int currentHealth = shieldReactorHealth.getOrDefault(factionName, 0);
        if (currentHealth >= MAX_REACTOR_HEALTH) {
            player.sendMessage("§6Shield Reactor is already at maximum capacity!");
            return;
        }

        // Process kills input
        try {
            String killsInput = response.getInputResponse(2); // Changed from 1 to 2
            if (killsInput != null && !killsInput.isEmpty()) {
                int killsToSpend = Integer.parseInt(killsInput);
                if (killsToSpend > 0) {
                    if (faction.getKills() >= killsToSpend) {
                        int healthFromKills = killsToSpend * KILLS_TO_HEALTH;
                        int actualHealthAdded = Math.min(healthFromKills, MAX_REACTOR_HEALTH - currentHealth);
                        int actualKillsNeeded = (int) Math.ceil(actualHealthAdded / (double) KILLS_TO_HEALTH);
                        
                        faction.setKills(faction.getKills() - actualKillsNeeded);
                        addReactorHealth(factionName, actualHealthAdded);
                        
                        if (actualHealthAdded < healthFromKills) {
                            int refundedKills = killsToSpend - actualKillsNeeded;
                            faction.setKills(faction.getKills() + refundedKills);
                            player.sendMessage("§6" + refundedKills + " kills were refunded due to reaching maximum capacity!");
                        }
                        
                        player.sendMessage(String.format("§aSpent %d kills for %d reactor health!", actualKillsNeeded, actualHealthAdded));
                    } else {
                        player.sendMessage("§cNot enough kills!");
                    }
                }
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid number of kills entered!");
        }

        // Process money input
        try {
            String moneyInput = response.getInputResponse(4); // Changed from 3 to 4
            if (moneyInput != null && !moneyInput.isEmpty()) {
                double moneyToSpend = Double.parseDouble(moneyInput);
                if (moneyToSpend > 0) {
                    if (faction.getVaultBalance() >= moneyToSpend) {
                        int healthFromMoney = (int) (moneyToSpend / MONEY_PER_HEALTH);
                        int actualHealthAdded = Math.min(healthFromMoney, MAX_REACTOR_HEALTH - currentHealth);
                        double actualMoneyNeeded = actualHealthAdded * MONEY_PER_HEALTH;
                        
                        faction.setVaultBalance(faction.getVaultBalance() - actualMoneyNeeded);
                        addReactorHealth(factionName, actualHealthAdded);
                        
                        if (actualHealthAdded < healthFromMoney) {
                            double refundedMoney = moneyToSpend - actualMoneyNeeded;
                            faction.setVaultBalance(faction.getVaultBalance() + refundedMoney);
                            player.sendMessage(String.format("§6$%,.0f was refunded due to reaching maximum capacity!", refundedMoney));
                        }
                        
                        player.sendMessage(String.format("§aSpent $%,.0f for %d reactor health!", actualMoneyNeeded, actualHealthAdded));
                    } else {
                        player.sendMessage("§cNot enough money in faction vault!");
                    }
                }
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid amount of money entered!");
        }

        db.saveFaction(faction);
        showShieldReactorGui(player); // Refresh the GUI
    }

    private void addReactorHealth(String factionName, int health) {
        int currentHealth = shieldReactorHealth.getOrDefault(factionName, 0);
        int newHealth = Math.min(currentHealth + health, MAX_REACTOR_HEALTH);
        shieldReactorHealth.put(factionName, newHealth);
        saveShieldReactorData();
    }

    private int handleShieldDamage(String factionName, int damage) {
        int reactorHealth = shieldReactorHealth.getOrDefault(factionName, 0);
        
        if (reactorHealth > 0) {
            // Record damage time for cooldown
            lastShieldDamageTime.put(factionName, System.currentTimeMillis());
            
            // Absorb damage with reactor
            int remainingDamage = Math.max(0, damage - reactorHealth);
            shieldReactorHealth.put(factionName, Math.max(0, reactorHealth - damage));
            saveShieldReactorData(); // Add this line
            return remainingDamage;
        }
        
        return damage;
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
            case "Shield Reactor":
                showShieldReactorGui(player);
                break;
            case "Back":
                plugin.getCommandController().handleFactionGuiCommand(player);
                break;
            default:
                player.sendMessage("§cInvalid action.");
                break;
        }
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
        if (factionName == null) {
            return false;
        }
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

    // Add new runnable to check for shield healing
    private void startShieldHealingCheck() {
        new NukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<String, Long> entry : lastShieldDamageTime.entrySet()) {
                    String factionName = entry.getKey();
                    long lastDamage = entry.getValue();

                    // Check if cooldown has passed
                    if (System.currentTimeMillis() - lastDamage >= REACTOR_COOLDOWN) {
                        int reactorHealth = shieldReactorHealth.getOrDefault(factionName, 0);
                        if (reactorHealth > 0) {
                            healFactionShields(factionName);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20 * 60, 20 * 60); // Check every minute
    }

    private void healFactionShields(String factionName) {
        int reactorHealth = shieldReactorHealth.getOrDefault(factionName, 0);
        if (reactorHealth <= 0) return;

        List<ProtectedChunkData> factionChunks = protectedChunks.values().stream()
            .filter(chunk -> chunk.getFactionName().equals(factionName))
            .filter(chunk -> chunk.getShieldHealth() < 5000)
            .collect(Collectors.toList());

        for (ProtectedChunkData chunk : factionChunks) {
            int missing = 5000 - chunk.getShieldHealth();
            int heal = Math.min(missing, reactorHealth);
            
            chunk.setShieldHealth(chunk.getShieldHealth() + heal);
            reactorHealth -= heal;

            if (reactorHealth <= 0) break;
        }

        shieldReactorHealth.put(factionName, reactorHealth);
        saveShieldReactorData(); // Add this line
        saveProtectedChunks();
        showReactorHologram(factionName, reactorHealth); // Add this line
    }

    // Add this new method
    private void showReactorHologram(String factionName, int healAmount) {
        int reactorHealth = shieldReactorHealth.getOrDefault(factionName, 0);
        
        // Show hologram to all online faction members
        for (Player player : plugin.getServer().getOnlinePlayers().values()) {
            String playerFaction = db.getPlayerFaction(player.getUniqueId().toString());
            if (factionName.equals(playerFaction)) {
                Position playerPos = player.getPosition();
                Position holoPos = playerPos.add(0, 2, 0); // Above player's head
                
                // Create healing animation
                new NukkitRunnable() {
                    int tick = 0;
                    final int animationDuration = 40;

                    @Override
                    public void run() {
                        if (tick >= animationDuration || !player.isOnline()) {
                            this.cancel();
                            return;
                        }

                        String message = String.format("§aReactor Active\n§b%,d §7/ §b%,d\n§a+%d Health Restored", 
                            reactorHealth,
                            MAX_REACTOR_HEALTH,
                            healAmount
                        );
                        
                        player.sendTip(message);

                        tick++;
                    }
                }.runTaskTimer(plugin, 0, 1);
            }
        }
    }
}
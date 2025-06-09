package io.papermc.folia.compat.wrappers.entity;

import io.papermc.folia.compat.scheduler.RegionTaskDelegator;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.*;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.logging.Logger;

public class WrappedPlayer extends WrappedLivingEntity implements Player, io.papermc.folia.compat.wrappers.inventory.InventoryHolderWrapper {
    private static final Logger LOGGER = Logger.getLogger(WrappedPlayer.class.getName());

    // realPlayer is inherited from WrappedLivingEntity as realLivingEntity, and further cast here.
    // No, WrappedLivingEntity has `realLivingEntity`, and WrappedEntity has `realEntity`.
    // We need to ensure the correct one is used or cast.
    // For simplicity, let's ensure realPlayer is directly available.
    protected final Player realPlayer;

    public WrappedPlayer(Player realPlayer, RegionTaskDelegator taskDelegator) {
        super(realPlayer, taskDelegator); // This will set realLivingEntity and realEntity
        this.realPlayer = realPlayer;
    }

    /**
     * Provides the underlying real Player entity.
     * Important for internal operations that need to bypass the wrapper,
     * or for the delegator to schedule tasks against the actual entity.
     * @return The real Player instance.
     */
    public Player getRealEntity() {
        return realPlayer;
    }


    // Implementation of InventoryHolderWrapper
    @Override
    public InventoryHolder getRealHolder() {
        return this.realPlayer;
    }

    @Override
    public boolean isPlayerHolder() {
        return true;
    }

    @Override
    public WrappedPlayer getAsPlayerHolder() {
        return this;
    }

    @Override
    public Location getLocationForDelegation() {
        // For a player, the delegation should always be based on their entity context,
        // not a fixed location (unless it's for an inventory like a virtual chest they opened,
        // which is not this holder's direct concern).
        return null; // Or realPlayer.getLocation() if a location is strictly needed for some holder types
    }
    // End of InventoryHolderWrapper implementation


    @Override
    public String getName() {
        // Player name is effectively immutable and globally unique after login
        return realPlayer.getName();
    }

    @Override
    public String getDisplayName() {
        return realPlayer.getDisplayName();
    }

    @Override
    public void setDisplayName(String name) {
        // This might have implications for other players seeing the name change.
        // Could be delegated, but often handled by chat plugins that might do their own global sync.
        // For now, direct, but a candidate for future review.
        taskDelegator.scheduleForEntity(realEntity, () -> realPlayer.setDisplayName(name));
    }

    @Override
    public void kickPlayer(String message) {
        LOGGER.info("[COMPAT LAYER] WrappedPlayer.kickPlayer for " + getUniqueId() + ". Delegating (conceptually to player's current region or global).");
        // Kicking is a global-ish action but initiated by player.
        // Folia might have a specific API for this.
        // Scheduling on entity's current region to initiate seems reasonable for now.
        taskDelegator.scheduleForEntity(realEntity, () -> realPlayer.kickPlayer(message));
    }

    @Override
    public PlayerInventory getInventory() {
        return new io.papermc.folia.compat.wrappers.inventory.WrappedPlayerInventory(realPlayer.getInventory(), this.taskDelegator, this);
    }

    @Override
    public Inventory getEnderChest() {
        return new io.papermc.folia.compat.wrappers.inventory.WrappedInventory(realPlayer.getEnderChest(), this.taskDelegator, this);
    }

    @Override
    public InventoryView getOpenInventory() {
        // TODO: Return WrappedInventoryView. This is complex as it involves top and bottom.
        LOGGER.warning("WrappedPlayer.getOpenInventory - returning real view, NEEDS WRAPPING.");
        return realPlayer.getOpenInventory();
    }

    // Many Player methods - direct delegation, logging, or TODOs for now

    @Override public String getPlayerListName() { return realPlayer.getPlayerListName(); }
    @Override public void setPlayerListName(String name) { taskDelegator.scheduleForEntity(realPlayer, () -> realPlayer.setPlayerListName(name));}
    @Override public String getPlayerListHeader() { return realPlayer.getPlayerListHeader(); }
    @Override public String getPlayerListFooter() { return realPlayer.getPlayerListFooter(); }
    @Override public void setPlayerListHeader(String header) { taskDelegator.scheduleForEntity(realPlayer, () -> realPlayer.setPlayerListHeader(header));}
    @Override public void setPlayerListFooter(String footer) { taskDelegator.scheduleForEntity(realPlayer, () -> realPlayer.setPlayerListFooter(footer));}
    @Override public void setPlayerListHeaderFooter(String header, String footer) { taskDelegator.scheduleForEntity(realPlayer, () -> realPlayer.setPlayerListHeaderFooter(header, footer));}
    @Override public boolean isConversing() { return realPlayer.isConversing(); }
    @Override public void acceptConversationInput(String input) { realPlayer.acceptConversationInput(input); } // Likely needs context
    @Override public boolean beginConversation(Conversation conversation) { return realPlayer.beginConversation(conversation); } // Likely needs context
    @Override public void abandonConversation(Conversation conversation) { realPlayer.abandonConversation(conversation); }
    @Override public void abandonConversation(Conversation conversation, ConversationAbandonedEvent details) { realPlayer.abandonConversation(conversation, details); }
    @Override public void sendMessage(String message) { /* Needs context for chat formatting/events */ realPlayer.sendMessage(message); }
    @Override public void sendMessage(String... messages) { realPlayer.sendMessage(messages); }
    @Override public void sendRawMessage(String message) { realPlayer.sendRawMessage(message); }
    @Override public InetSocketAddress getAddress() { return realPlayer.getAddress(); }
    @Override public void sendData(Plugin source, String channel, byte[] message) { realPlayer.sendData(source, channel, message); } // Plugin messaging, likely global
    @Override public Set<String> getListeningPluginChannels() { return realPlayer.getListeningPluginChannels(); } // Snapshot
    @Override public void openInventory(Inventory inventory) { /* Needs delegation, inventory wrapping */ realPlayer.openInventory(inventory); }
    @Override public InventoryView openWorkbench(Location location, boolean force) { /* Needs delegation, wrapping */ return realPlayer.openWorkbench(location, force); }
    @Override public InventoryView openEnchanting(Location location, boolean force) { /* Needs delegation, wrapping */ return realPlayer.openEnchanting(location, force); }
    @Override public InventoryView openMerchant(Villager trader, boolean force) { /* Needs delegation, wrapping */ return realPlayer.openMerchant(trader, force); }
    @Override public InventoryView openMerchant(Merchant merchant, boolean force) { /* Needs delegation, wrapping */ return realPlayer.openMerchant(merchant, force); }
    @Override public void closeInventory() { taskDelegator.scheduleForEntity(realPlayer, realPlayer::closeInventory); }
    @Override public ItemStack getItemInHand() { /* Needs wrapping */ return realPlayer.getItemInHand(); }
    @Override public void setItemInHand(ItemStack item) { /* Needs delegation, wrapping */ realPlayer.setItemInHand(item); }
    @Override public ItemStack getItemOnCursor() { /* Needs wrapping */ return realPlayer.getItemOnCursor(); }
    @Override public void setItemOnCursor(ItemStack item) { /* Needs delegation, wrapping */ realPlayer.setItemOnCursor(item); }
    @Override public boolean hasCooldown(Material material) { return realPlayer.hasCooldown(material); }
    @Override public int getCooldown(Material material) { return realPlayer.getCooldown(material); }
    @Override public void setCooldown(Material material, int ticks) { taskDelegator.scheduleForEntity(realPlayer, () -> realPlayer.setCooldown(material, ticks)); }
    @Override public boolean isSleepingIgnored() { return realPlayer.isSleepingIgnored(); }
    @Override public void setSleepingIgnored(boolean isSleepingIgnored) { taskDelegator.scheduleForEntity(realPlayer, () -> realPlayer.setSleepingIgnored(isSleepingIgnored)); }
    @Override public Location getBedSpawnLocation() { return realPlayer.getBedSpawnLocation(); }
    @Override public void setBedSpawnLocation(Location location) { taskDelegator.scheduleForEntity(realPlayer, () -> realPlayer.setBedSpawnLocation(location)); }
    @Override public void setBedSpawnLocation(Location location, boolean force) { taskDelegator.scheduleForEntity(realPlayer, () -> realPlayer.setBedSpawnLocation(location, force)); }
    @Override public void playNote(Location loc, byte instrument, byte note) { /* Needs careful handling for audibility */ realPlayer.playNote(loc, instrument, note); }
    @Override public void playNote(Location loc, Instrument instrument, Note note) { realPlayer.playNote(loc, instrument, note); }
    @Override public void playSound(Location loc, Sound sound, float volume, float pitch) { realPlayer.playSound(loc, sound, volume, pitch); }
    @Override public void playSound(Location loc, String sound, float volume, float pitch) { realPlayer.playSound(loc, sound, volume, pitch); }
    @Override public void playSound(Location loc, Sound sound, SoundCategory category, float volume, float pitch) { realPlayer.playSound(loc, sound, category, volume, pitch); }
    @Override public void playSound(Location loc, String sound, SoundCategory category, float volume, float pitch) { realPlayer.playSound(loc, sound, category, volume, pitch); }
    @Override public void stopSound(Sound sound) { realPlayer.stopSound(sound); }
    @Override public void stopSound(String sound) { realPlayer.stopSound(sound); }
    @Override public void stopSound(Sound sound, SoundCategory category) { realPlayer.stopSound(sound, category); }
    @Override public void stopSound(String sound, SoundCategory category) { realPlayer.stopSound(sound, category); }
    @Override public void playEffect(Location loc, Effect effect, int data) { realPlayer.playEffect(loc, effect, data); }
    @Override public <T> void playEffect(Location loc, Effect effect, T data) { realPlayer.playEffect(loc, effect, data); }
    @Override public void sendBlockChange(Location loc, Material material, byte data) { realPlayer.sendBlockChange(loc, material, data); } // Client-side only, but needs context
    @Override public void sendBlockChange(Location loc, BlockData block) { realPlayer.sendBlockChange(loc, block); }
    @Override public boolean sendChunkChange(Location loc, int sx, int sy, int sz, byte[] data) { return realPlayer.sendChunkChange(loc, sx, sy, sz, data); }
    @Override public void sendSignChange(Location loc, String[] lines) throws IllegalArgumentException { realPlayer.sendSignChange(loc, lines); }
    @Override public void sendSignChange(Location loc, String[] lines, DyeColor dyeColor) throws IllegalArgumentException {}
    @Override public void sendSignChange(Location loc, String[] strings, DyeColor dyeColor, boolean b) throws IllegalArgumentException {}
    @Override public void sendMap(MapView map) { realPlayer.sendMap(map); }
    @Override public void updateInventory() { taskDelegator.scheduleForEntity(realPlayer, realPlayer::updateInventory); } // Client sync
    @Override public AdvancementProgress getAdvancementProgress(Advancement advancement) { return realPlayer.getAdvancementProgress(advancement); }
    @Override public int getClientViewDistance() { return realPlayer.getClientViewDistance(); }
    @Override public String getLocale() { return realPlayer.getLocale(); }
    @Override public void updateCommands() { realPlayer.updateCommands(); } // Global
    @Override public void openBook(ItemStack book) { taskDelegator.scheduleForEntity(realPlayer, () -> realPlayer.openBook(book)); }
    @Override public void openSign(Sign sign) {}
    @Override public Spigot spigot() { return realPlayer.spigot(); } // Needs deep wrapping
    @Override public boolean isOnline() { return realPlayer.isOnline(); } // Critical for validity checks
    @Override public boolean isBanned() { return realPlayer.isBanned(); } // Global state
    // @Override public boolean isWhitelisted() { return realPlayer.isWhitelisted(); } // Deprecated
    // @Override public void setWhitelisted(boolean value) { realPlayer.setWhitelisted(value); } // Deprecated
    @Override public Player getPlayer() { return this; } // Already a player
    @Override public long getFirstPlayed() { return realPlayer.getFirstPlayed(); }
    @Override public long getLastPlayed() { return realPlayer.getLastPlayed(); }
    @Override public boolean hasPlayedBefore() { return realPlayer.hasPlayedBefore(); }
    @Override public Location getCompassTarget() { return realPlayer.getCompassTarget(); }
    @Override public void setCompassTarget(Location loc) { taskDelegator.scheduleForEntity(realPlayer, () -> realPlayer.setCompassTarget(loc)); }
    @Override public Scoreboard getScoreboard() { /* Needs WrappedScoreboard */ return realPlayer.getScoreboard(); }
    @Override public void setScoreboard(Scoreboard scoreboard) throws IllegalArgumentException, IllegalStateException { /* Needs unwrap + global state management */ realPlayer.setScoreboard(scoreboard); }
    @Override public void sendResourcePack(String url) { realPlayer.sendResourcePack(url); }
    @Override public void sendResourcePack(String url, byte[] hash) { realPlayer.sendResourcePack(url, hash); }
    @Override public void sendResourcePack(String s, byte[] bytes, String s1) {}
    @Override public void sendResourcePack(String s, byte[] bytes, String s1, boolean b) {}
    @Override public PlayerResourcePackStatusEvent.Status getResourcePackStatus() { return realPlayer.getResourcePackStatus(); }
    @Override public String getResourcePackHash() { return realPlayer.getResourcePackHash(); }
    @Override public boolean hasResourcePack() { return realPlayer.hasResourcePack(); }
    @Override public GameMode getGameMode() { return realPlayer.getGameMode(); }
    @Override public void setGameMode(GameMode mode) { taskDelegator.scheduleForEntity(realPlayer, () -> realPlayer.setGameMode(mode)); }
    @Override public boolean isBlocking() { return realPlayer.isBlocking(); } // Duplicated from LivingEntity?
    @Override public int getExpToLevel() { return realPlayer.getExpToLevel(); }
    @Override public float getAttackCooldown() { return realPlayer.getAttackCooldown(); }
    @Override public boolean discoverRecipe(NamespacedKey recipe) { return realPlayer.discoverRecipe(recipe); } // Player data, needs sync
    @Override public int discoverRecipes(Collection<NamespacedKey> recipes) { return realPlayer.discoverRecipes(recipes); }
    @Override public boolean undiscoverRecipe(NamespacedKey recipe) { return realPlayer.undiscoverRecipe(recipe); }
    @Override public int undiscoverRecipes(Collection<NamespacedKey> recipes) { return realPlayer.undiscoverRecipes(recipes); }
    @Override public boolean hasDiscoveredRecipe(NamespacedKey recipe) { return realPlayer.hasDiscoveredRecipe(recipe);}
    @Override public Set<NamespacedKey> getDiscoveredRecipes() { return realPlayer.getDiscoveredRecipes(); } // Snapshot
    @Override public Entity getShoulderEntityLeft() { /* Needs wrap */ return realPlayer.getShoulderEntityLeft(); }
    @Override public void setShoulderEntityLeft(Entity entity) { /* Needs unwrap + deleg */ realPlayer.setShoulderEntityLeft(entity); }
    @Override public Entity getShoulderEntityRight() { /* Needs wrap */ return realPlayer.getShoulderEntityRight(); }
    @Override public void setShoulderEntityRight(Entity entity) { /* Needs unwrap + deleg */ realPlayer.setShoulderEntityRight(entity); }
    @Override public boolean dropItem(boolean dropAll) { /* Needs deleg */ return realPlayer.dropItem(dropAll); }
    @Override public float getExhaustion() { return realPlayer.getExhaustion(); }
    @Override public void setExhaustion(float value) { taskDelegator.scheduleForEntity(realPlayer, () -> realPlayer.setExhaustion(value)); }
    @Override public float getSaturation() { return realPlayer.getSaturation(); }
    @Override public void setSaturation(float value) { taskDelegator.scheduleForEntity(realPlayer, () -> realPlayer.setSaturation(value)); }
    @Override public int getFoodLevel() { return realPlayer.getFoodLevel(); }
    @Override public void setFoodLevel(int value) { taskDelegator.scheduleForEntity(realPlayer, () -> realPlayer.setFoodLevel(value)); }
    @Override public int getSaturatedRegenRate() { return 0; }
    @Override public void setSaturatedRegenRate(int i) { }
    @Override public int getUnsaturatedRegenRate() { return 0; }
    @Override public void setUnsaturatedRegenRate(int i) { }
    @Override public int getStarvationRate() { return 0; }
    @Override public void setStarvationRate(int i) { }
    @Override public Location getLastDeathLocation() { return realPlayer.getLastDeathLocation(); }
    @Override public void setLastDeathLocation(Location location) { taskDelegator.scheduleForEntity(realPlayer, () -> realPlayer.setLastDeathLocation(location)); }
    @Override public PlayerProfile getPlayerProfile() { return realPlayer.getPlayerProfile();}
    @Override public void setPlayerProfile(PlayerProfile playerProfile) {}
    @Override public Component name() {return realPlayer.name();}
    @Override public Component displayName() {return realPlayer.displayName();}
    @Override public void displayName(Component component) {taskDelegator.scheduleForEntity(realPlayer, () -> realPlayer.displayName(component));}
    @Override public Component playerListName() {return realPlayer.playerListName();}
    @Override public void playerListName(Component component) {taskDelegator.scheduleForEntity(realPlayer, () -> realPlayer.playerListName(component));}
    @Override public Component playerListHeader() {return realPlayer.playerListHeader();}
    @Override public void playerListHeader(Component component) {taskDelegator.scheduleForEntity(realPlayer, () -> realPlayer.playerListHeader(component));}
    @Override public Component playerListFooter() {return realPlayer.playerListFooter();}
    @Override public void playerListFooter(Component component) {taskDelegator.scheduleForEntity(realPlayer, () -> realPlayer.playerListFooter(component));}
    @Override public double getEyeHeight() { return realLivingEntity.getEyeHeight(); }
    @Override public double getEyeHeight(boolean ignorePose) { return realLivingEntity.getEyeHeight(ignorePose); }
    // Placeholder for missing methods from LivingEntity that Player might need if not directly inherited through our structure
    @Override public AttributeInstance getAttribute(Attribute attribute) { return realLivingEntity.getAttribute(attribute); }
    @Override public void sendExperienceChange(float v) {}
    @Override public void sendExperienceChange(float v, int i) {}
    @Override public float getExp() {return 0;}
    @Override public void setExp(float v) {}
    @Override public int getLevel() {return 0;}
    @Override public void setLevel(int i) {}
    @Override public int getTotalExperience() {return 0;}
    @Override public void setTotalExperience(int i) {}
    @Override public void giveExp(int i) {}
    @Override public void giveExpLevels(int i) {}
    @Override public boolean getAllowFlight() {return false;}
    @Override public void setAllowFlight(boolean b) {}
    @Override public void hidePlayer(Plugin plugin, Player player) {}
    @Override public void hidePlayer(Player player) {} // Deprecated
    @Override public void showPlayer(Plugin plugin, Player player) {}
    @Override public void showPlayer(Player player) {} // Deprecated
    @Override public boolean canSee(Player player) {return false;}
    @Override public boolean isFlying() {return false;}
    @Override public void setFlying(boolean b) {}
    @Override public void setFlySpeed(float v) throws IllegalArgumentException {}
    @Override public float getFlySpeed() {return 0;}
    @Override public void setWalkSpeed(float v) throws IllegalArgumentException {}
    @Override public float getWalkSpeed() {return 0;}
    @Override public void setTexturePack(String s) {} // Deprecated
    @Override public void setResourcePack(String s) {}
    @Override public void setResourcePack(String s, byte[] bytes) {}
    @Override public void chat(String s) {}
    @Override public boolean performCommand(String s) {return false;}

}

package at.gaderman.serverFest.management;

import at.gaderman.serverFest.ServerFest;
import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;

public class WorldManager {
    private static WorldManager instance;

    public static WorldManager getInstance(){
        if(instance == null) instance = new WorldManager();
        return instance;
    }

    private static final String HUB_SUFFIX = "_hub";

    private World hubWorld;

    private WorldManager(){}

    /**
     * Resolves or creates the hub world
     */
    public void setUpHub() {
        NamespacedKey hubKey = resolveHubWorldName();
        Bukkit.getLogger().info("[WorldManager] Hub world key resolved to: " + hubKey.getKey());

        hubWorld = loadOrCreateHubWorld(hubKey);

        if (hubWorld == null) {
            Bukkit.getLogger().severe("[WorldManager] Failed to load or create hub world '" + hubKey.getKey() + "'!");
        } else {
            Bukkit.getLogger().info("[WorldManager] Hub world ready: " + hubWorld.getName());
        }
    }

    /**
     * Returns the hub world. Will be {@code null} if {@link #setUpHub()} has
     * not been called or world creation failed.
     */
    public World getHubWorld() {
        return hubWorld;
    }

    /**
     * Derives the hub world name from the server's default world.
     * @return default level name + {@value HUB_SUFFIX}
     */
    private NamespacedKey resolveHubWorldName() {
        return new NamespacedKey(ServerFest.getPlugin(), "hub_world");
    }

    /**
     * Returns an existing loaded world with the given name, loads it from disk
     * if the folder exists, or creates a brand-new void world.
     */
    private World loadOrCreateHubWorld(NamespacedKey worldKey) {
        World existing = Bukkit.getWorld(worldKey);
        if (existing != null) {
            Bukkit.getLogger().info("[WorldManager] Hub world already loaded, reusing it.");
            return existing;
        }

        Bukkit.getLogger().info("[WorldManager] Hub world not loaded, generating/load new hub world...");
        World newHub = createWorldCreator(worldKey).createWorld();

        if(newHub == null){
            return null;
        }

        newHub.setSpawnLocation(0, 64, 0);
        newHub.setGameRule(GameRules.RESPAWN_RADIUS, 0);

        for (int x = -10; x <= 10; x++)
                for (int z = -10; z <= 10; z++){
                    new Location(newHub, x, newHub.getSpawnLocation().getY() - 1, z).getBlock().setType(Material.QUARTZ_BLOCK);
                }


        return newHub;
    }

    public World loadOrCreateFestWorld() {
        NamespacedKey worldKey = new NamespacedKey(ServerFest.getPlugin(), "fest_world");
        World existing = Bukkit.getWorld(worldKey);
        if(existing != null) {
            return existing;
        }

        long seed = Bukkit.getWorlds().getFirst().getSeed();
        return WorldCreator.ofKey(worldKey).seed(seed).createWorld();
    }

    /**
     * Builds a void world manager
     */
    private WorldCreator createWorldCreator(NamespacedKey worldKey) {
        return WorldCreator.ofKey(worldKey)
                .environment(World.Environment.NORMAL)
                .type(WorldType.NORMAL)
                .generator(new VoidChunkGenerator());
    }

    public WorldCreator createChallengeWorldCreator(NamespacedKey worldKey){
        return WorldCreator.ofKey(worldKey);
    }

    private static final class VoidChunkGenerator extends ChunkGenerator {}
}

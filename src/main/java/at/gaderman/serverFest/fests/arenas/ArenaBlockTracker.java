package at.gaderman.serverFest.fests.arenas;

import at.gaderman.serverFest.ServerFest;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ArenaBlockTracker {

    private static final int MAGIC = 0x53464254;
    private static final int VERSION = 1;

    private final ArenaInstance arenaInstance;
    private final Path file;

    private final Map<Long, Integer> changes = new HashMap<>();
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Integer> paletteLookup = new HashMap<>();

    private UUID worldUuid;
    private boolean dirty;

    public ArenaBlockTracker(ArenaInstance arenaInstance) {
        this.arenaInstance = arenaInstance;

        String arenaIdentifier = arenaInstance.gameType().toString().toLowerCase() + "-" + arenaInstance.id();

        Path arenaPath = ServerFest.getPlugin().getDataPath().resolve("arenas/" + arenaIdentifier);
        this.file = arenaPath.resolve("blocks.dat");

        worldUuid = arenaInstance.arenaLayout().origin().getWorld().getUID();
        load();
    }

    public void setBlock(Location location, Material material) {
        setBlock(location.getBlock(), material);
    }

    public void setBlock(Block block, Material material) {
        remember(block);
        block.setType(material);
    }

    public void setBlock(Location location, BlockData blockData) {
        setBlock(location.getBlock(), blockData);
    }

    public void setBlock(Block block, BlockData blockData) {
        remember(block);
        block.setBlockData(blockData);
    }

    private void remember(Block block) {
        long position = pack(block.getX(), block.getY(), block.getZ());

        if (changes.containsKey(position))
            return;

        String state = block.getBlockData().getAsString();

        int paletteId = paletteLookup.computeIfAbsent(state, value -> {
            int id = palette.size();
            palette.add(value);
            return id;
        });

        changes.put(position, paletteId);

        if (!dirty) {
            dirty = true;
            worldUuid = arenaInstance.arenaLayout().origin().getWorld().getUID();
            save();
        }
    }

    //region Packing

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (y & 0xFFF) << 26)
                | (z & 0x3FFFFFFL);
    }

    private static int unpackX(long value) {
        int x = (int) (value >> 38);
        if ((x & 0x2000000) != 0)
            x |= 0xFC000000;
        return x;
    }

    private static int unpackY(long value) {
        return (int) ((value >> 26) & 0xFFF);
    }

    private static int unpackZ(long value) {
        int z = (int) (value & 0x3FFFFFF);
        if ((z & 0x2000000) != 0)
            z |= 0xFC000000;
        return z;
    }

    //endregion

    public void save() {
        try {
            Files.createDirectories(file.getParent());

            Path temp = file.resolveSibling("blocks.dat.tmp");

            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(temp)))) {

                out.writeInt(MAGIC);
                out.writeInt(VERSION);

                out.writeLong(worldUuid.getMostSignificantBits());
                out.writeLong(worldUuid.getLeastSignificantBits());

                out.writeBoolean(dirty);

                out.writeInt(palette.size());

                for (String state : palette)
                    out.writeUTF(state);

                out.writeInt(changes.size());

                for (Map.Entry<Long, Integer> entry : changes.entrySet()) {
                    out.writeLong(entry.getKey());
                    out.writeInt(entry.getValue());
                }
            }

            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException exception) {
            throw new RuntimeException("Failed to save arena block tracker", exception);
        }
    }

    private void load() {
        if (!Files.exists(file))
            return;

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {

            int magic = in.readInt();

            if (magic != MAGIC)
                throw new IOException("Invalid ArenaBlockTracker file");

            int version = in.readInt();

            if (version != VERSION)
                throw new IOException("Unsupported ArenaBlockTracker version: " + version);

            worldUuid = new UUID(in.readLong(), in.readLong());
            dirty = in.readBoolean();

            int paletteSize = in.readInt();

            for (int i = 0; i < paletteSize; i++) {
                String state = in.readUTF();
                paletteLookup.put(state, i);
                palette.add(state);
            }

            int changeCount = in.readInt();

            for (int i = 0; i < changeCount; i++)
                changes.put(in.readLong(), in.readInt());

        } catch (IOException exception) {
            throw new RuntimeException("Failed to load arena block tracker", exception);
        }
    }

    public void clear() {
        changes.clear();
        palette.clear();
        paletteLookup.clear();
        dirty = false;
        worldUuid = null;

        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to delete arena block tracker", exception);
        }
    }

    public CompletableFuture<Void> restore() {
        World world = arenaInstance.arenaLayout().origin().getWorld();

        if (!dirty || !belongsTo(world))
            return CompletableFuture.completedFuture(null);

        Map<Long, List<Map.Entry<Long, Integer>>> chunks = new HashMap<>();

        changes.forEach((position, paletteId) -> {
            int x = unpackX(position);
            int z = unpackZ(position);

            int chunkX = x >> 4;
            int chunkZ = z >> 4;

            long chunkKey = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);

            chunks.computeIfAbsent(chunkKey, _ -> new ArrayList<>())
                    .add(Map.entry(position, paletteId));
        });

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Map.Entry<Long, List<Map.Entry<Long, Integer>>> entry : chunks.entrySet()) {
            int chunkX = (int) (entry.getKey() >> 32);
            int chunkZ = (int) (entry.getKey().longValue());

            CompletableFuture<Void> future = world.getChunkAtAsync(chunkX, chunkZ, false)
                    .thenAccept(chunk -> {
                        for (Map.Entry<Long, Integer> change : entry.getValue()) {
                            long position = change.getKey();

                            int x = unpackX(position);
                            int y = unpackY(position);
                            int z = unpackZ(position);

                            BlockData blockData = Bukkit.createBlockData(palette.get(change.getValue()));
                            world.getBlockAt(x, y, z).setBlockData(blockData, false);
                        }
                    });

            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenRun(this::clear);
    }

    public boolean belongsTo(World world) {
        return worldUuid != null && worldUuid.equals(world.getUID());
    }
}

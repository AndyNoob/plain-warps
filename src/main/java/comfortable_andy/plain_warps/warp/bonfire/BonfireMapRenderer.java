package comfortable_andy.plain_warps.warp.bonfire;

import comfortable_andy.plain_warps.PlainWarpsMain;
import comfortable_andy.plain_warps.util.astar.AStarPathFinder;
import net.minecraft.util.Mth;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.map.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BlockVector;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.*;

public class BonfireMapRenderer extends MapRenderer {

    public static final NamespacedKey GROUP = new NamespacedKey(PlainWarpsMain.getInstance(), "map_group");
    static Map<String, Collection<BlockVector>> COMPUTED_PATHS = new HashMap<>();

    public BonfireMapRenderer() {
        super(true);
    }

    int tick = 0;

    @Override
    public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
        String group = player.getPersistentDataContainer().get(GROUP, PersistentDataType.STRING);
        List<BonfireWarp> warps = PlainWarpsMain.getInstance().getWarps().stream()
                .filter(s -> s instanceof BonfireWarp warp
                        && Objects.equals(group, warp.group))
                .map(s -> (BonfireWarp) s)
                .toList();
        World world = player.getWorld();
        if (warps.isEmpty()) {
            canvas.drawText(10, 10, MinecraftFont.Font, "No warps under '" + group + "'");
            return;
        }
        Vector center = getCenter(warps);
        double maxDistance = warps.stream()
                .mapToDouble(w -> w.bonfirePosition.distance(center)).max().orElse(0) * 2.5;
        double pixelsPerBlock = 128d / maxDistance;
        Vector topLeft = center.clone()
                .subtract(new Vector(maxDistance / 2d, 0, maxDistance / 2d));

        if (tick++ % 10 != 0) return;
        tick = 0;
        if (pixelsPerBlock < 1) {
            double blocksPerPixel = Math.ceil(1 / pixelsPerBlock);
            pixelsPerBlock = 1 / blocksPerPixel;
        } else {
            pixelsPerBlock = Math.ceil(pixelsPerBlock);
        }

        double blocksPerPixel = 1 / pixelsPerBlock;
        {

            for (int x = 0; x < 128; x++) {
                for (int y = 0; y < 128; y++) {
                    if (pixelsPerBlock < 1) {
                        int maxY = Integer.MIN_VALUE;
                        Color color = null;
                        for (int i = 0; i < blocksPerPixel; i++) {
                            for (int j = 0; j < blocksPerPixel; j++) {
                                BlockVector vector = new BlockVector(
                                        ((x - 64) * blocksPerPixel + i) + center.getBlockX(),
                                        0,
                                        ((y - 64) * blocksPerPixel + i) + center.getBlockZ()
                                );
                                Block block = findBlock(group, world, vector);
                                if (block != null
                                        && !block.isEmpty()
                                        && block.getY() > maxY) color = getMapColor(block);
                            }
                        }
                        if (color == null) continue;
                        canvas.setPixelColor(x, y, color);
                    } else {
                        BlockVector vector = new BlockVector(
                                ((x - 63) * blocksPerPixel) + center.getBlockX(),
                                0,
                                ((y - 63) * blocksPerPixel) + center.getBlockZ()
                        );
                        Block block = findBlock(group, world, vector);
                        if (block != null && !block.isEmpty()) {
                            var color = getMapColor(block);
                            for (int i = 0; i < pixelsPerBlock; i++) {
                                for (int j = 0; j < pixelsPerBlock; j++) {
                                    canvas.setPixelColor(x + i, y + j, color);
                                }
                            }
                        }
                        if (pixelsPerBlock > 1) y += NumberConversions.ceil(pixelsPerBlock - 1);
                    }
                }
                if (pixelsPerBlock > 1) x += NumberConversions.ceil(pixelsPerBlock - 1);
            }
        }

        map.setCenterX(center.getBlockX());
        map.setCenterZ(center.getBlockZ());
        canvas.drawText(10, 10, MinecraftFont.Font, "dist " + maxDistance);
        canvas.drawText(10, 20, MinecraftFont.Font, "pixel/blk " + pixelsPerBlock);
        canvas.drawText(10, 30, MinecraftFont.Font, "blk/pixel " + (1 / pixelsPerBlock));
        {
            center.toLocation(world).getBlock().setType(Material.DIAMOND_BLOCK);
            topLeft.toLocation(world).getBlock().setType(Material.REDSTONE_BLOCK);
            new Vector(maxDistance, 0, 0).add(topLeft).toLocation(world).getBlock().setType(Material.EMERALD_BLOCK);
            new Vector(0, 0, maxDistance).add(topLeft).toLocation(world).getBlock().setType(Material.IRON_BLOCK);
            new Vector(maxDistance, 0, maxDistance).add(topLeft).toLocation(world).getBlock().setType(Material.AMETHYST_BLOCK);
        }

        updatePaths(warps, group, world);

        {
            MapCursorCollection cursors = new MapCursorCollection();
            for (BonfireWarp warp : warps) {
                Vector toWarp = warp.bonfirePosition.clone().subtract(center).add(new Vector(0.5, 0, 0.5));
                toWarp.multiply(pixelsPerBlock * 2);
                cursors.addCursor(new MapCursor(
                        (byte) Mth.clamp(toWarp.getX() + pixelsPerBlock, -128, 127),
                        (byte) Mth.clamp(toWarp.getZ() + pixelsPerBlock, -128, 127),
                        (byte) 8,
                        MapCursor.Type.PLAYER_OFF_LIMITS,
                        true,
                        warp.id()
                ));
            }
            Vector toPlayer = player.getLocation().toVector().subtract(center);
            toPlayer.multiply(pixelsPerBlock * 2);
            cursors.addCursor(new MapCursor(
                    (byte) Mth.clamp(toPlayer.getX() + pixelsPerBlock, -128, 127),
                    (byte) Mth.clamp(toPlayer.getZ() + pixelsPerBlock, -128, 127),
                    (byte) (Math.round((player.getYaw() / 360) * 16) & 15),
                    MapCursor.Type.PLAYER,
                    true
            ));
            canvas.setCursors(cursors);
        }
    }

    private static void updatePaths(List<BonfireWarp> warps, String group, World world) {
        if (warps.size() >= 2 && !COMPUTED_PATHS.containsKey(group)) {
            Set<BlockVector> locs = new HashSet<>();
            for (int i = 0; i < warps.size(); i++) {
                BonfireWarp warpA = warps.get(i);
                BonfireWarp warpB = warps.get((i + 1) % warps.size());
                List<BlockVector> path = new AStarPathFinder()
                        .findPath(
                                world,
                                groundVector(world, warpA.bonfirePosition.toBlockVector()),
                                groundVector(world, warpB.bonfirePosition.toBlockVector())
                        );
                if (path == null) continue;
                locs.addAll(path);
            }
            COMPUTED_PATHS.put(group, locs);
        } else if (COMPUTED_PATHS.containsKey(group)) {
            Collection<BlockVector> vectors = COMPUTED_PATHS.get(group);
            for (BlockVector vector : vectors) {
                world.spawnParticle(
                        Particle.FLAME,
                        vector.toLocation(world).add(0.5, 1, 0.5),
                        1,
                        0,
                        0,
                        0,
                        0,
                        null,
                        true
                );

            }
        }
    }

    private static @Nullable Block findBlock(String group, World world, BlockVector vector) {
        if (COMPUTED_PATHS.containsKey(group)) {
            BlockVector closest = COMPUTED_PATHS.get(group)
                    .stream()
                    .min(Comparator.comparing(b -> b
                            .distanceSquared(vector)))
                    .orElse(null);
            if (closest == null) return null;
            if (closest.clone().setY(0).distanceSquared(vector) > 9) return null;
            vector.setY(closest.getBlockY() - 1);
        } else vector.setY(world.getHighestBlockYAt(vector.getBlockX(), vector.getBlockZ()));
        Block block = vector.toLocation(world).getBlock();
        int i = 0;
        if (block.isEmpty()) {
            while (i++ < 5 && block.isEmpty()) {
                block = block.getRelative(0, -1, 0);
            }
        } else {
            Block temp;
            while (i++ < 5 && (temp = block.getRelative(0, 1, 0)).isSolid()) {
                block = temp;
            }
        }
        return block;
    }

    private static @NotNull Vector getCenter(List<BonfireWarp> warps) {
        Vector center;
        {
            Vector min = new Vector(Double.POSITIVE_INFINITY, 0, Double.POSITIVE_INFINITY);
            Vector max = new Vector(Double.NEGATIVE_INFINITY, 0, Double.NEGATIVE_INFINITY);
            for (BonfireWarp b : warps) {
                Vector p = b.bonfirePosition;
                min.setX(Math.min(min.getX(), p.getX()));
                min.setZ(Math.min(min.getZ(), p.getZ()));
                max.setX(Math.max(max.getX(), p.getX()));
                max.setZ(Math.max(max.getZ(), p.getZ()));
            }
            center = new Vector((min.getX() + max.getX()) * 0.5, 0, (min.getZ() + max.getZ()) * 0.5);
        }
        return center;
    }

    private static @NotNull Color getMapColor(Block block) {
        return new Color(block.getBlockData().getMapColor().asRGB());
    }

    static BlockVector groundVector(World world, BlockVector vec) {
        if (vec.toLocation(world).getBlock().getRelative(0, -1, 0).isSolid())
            return vec;
        return vec.clone().add(new Vector(0, -1, 0)).toBlockVector();
    }
}

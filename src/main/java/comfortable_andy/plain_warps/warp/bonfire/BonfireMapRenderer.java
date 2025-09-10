package comfortable_andy.plain_warps.warp.bonfire;

import comfortable_andy.plain_warps.PlainWarpsMain;
import comfortable_andy.plain_warps.util.astar.AStarPathFinder;
import net.minecraft.util.Mth;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.map.*;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BonfireMapRenderer extends MapRenderer {

    static Map<String, List<BlockVector>> samplePaths = new HashMap<>();

    private final String group;

    public BonfireMapRenderer() {
        super(true);
        group = null;
    }

    public BonfireMapRenderer(String group) {
        super(true);
        this.group = group;
    }

    @Override
    public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
        List<BonfireWarp> warps = PlainWarpsMain.getInstance().getWarps().stream()
                .filter(s -> s instanceof BonfireWarp warp
                        && Objects.equals(group, warp.group))
                .map(s -> (BonfireWarp) s)
                .toList();
        World world = player.getWorld();
        if (warps.isEmpty()) {
            canvas.drawText(10, 10, MinecraftFont.Font, "No warps found under group '" + group + "'");
            return;
        }
        Vector center = getCenter(warps);
        double maxDistance = warps.stream()
                .mapToDouble(w -> w.bonfirePosition.distance(center)).max().orElse(0) * 2.5;
        double pixelsPerBlock = 128d / maxDistance;
        Vector topLeft = center.clone()
                .subtract(new Vector(maxDistance / 2d, 0, maxDistance / 2d));

        {
            if (pixelsPerBlock < 1) {
                double blocksPerPixel = 1 / pixelsPerBlock;
                double remainder = blocksPerPixel - ((int) blocksPerPixel);
                pixelsPerBlock = (pixelsPerBlock - pixelsPerBlock * remainder);
            } else {
                pixelsPerBlock = Math.round(pixelsPerBlock);
            }

            double blocksPerPixel = 1 / pixelsPerBlock;

            for (int x = 0; x < 128; x++) {
                for (int y = 0; y < 128; y++) {
                    if (pixelsPerBlock < 1) {
                        double rgbAccum = 0;
                        for (int i = 0; i < blocksPerPixel; i++) {
                            for (int j = 0; j < blocksPerPixel; j++) {
                                Block block = world.getHighestBlockAt(
                                        (int) (x * blocksPerPixel + i + topLeft.getBlockX()),
                                        (int) (y * blocksPerPixel + i + topLeft.getBlockZ())
                                );
                                if (samplePaths.containsKey(group) && samplePaths.get(group).stream().noneMatch(b -> b.distanceSquared(block.getLocation().toVector()) < 9))
                                    continue;
                                rgbAccum += getMapColor(block).getRGB();
                            }
                        }
                        rgbAccum /= blocksPerPixel * blocksPerPixel;
                        canvas.setPixelColor(x, y, new Color((int) rgbAccum));
                    } else {
                        Block block = world.getHighestBlockAt(
                                (int) /*Math.round*/(x * blocksPerPixel + topLeft.getBlockX()),
                                (int) /*Math.round*/(y * blocksPerPixel + topLeft.getBlockZ())
                        );
                        if (samplePaths.containsKey(group) && samplePaths.get(group).stream().noneMatch(b -> b.distanceSquared(block.getLocation().toVector()) < 9))
                            continue;
                        var color = getMapColor(block);
                        if (!block.isEmpty()) {
                            for (int i = 0; i < pixelsPerBlock; i++) {
                                for (int j = 0; j < pixelsPerBlock; j++) {
                                    canvas.setPixelColor(x + i, y + j, color);
                                }
                            }
                        }
                        y += (int) (pixelsPerBlock - 1);
                    }
                }
                if (pixelsPerBlock > 1) x += (int) (pixelsPerBlock - 1);
            }
        }

        map.setCenterX(center.getBlockX());
        map.setCenterZ(center.getBlockZ());
        center.toLocation(world).getBlock().setType(Material.DIAMOND_BLOCK);
        topLeft.toLocation(world).getBlock().setType(Material.REDSTONE_BLOCK);
        new Vector(maxDistance, 0, 0).add(topLeft).toLocation(world).getBlock().setType(Material.EMERALD_BLOCK);
        new Vector(0, 0, maxDistance).add(topLeft).toLocation(world).getBlock().setType(Material.IRON_BLOCK);
        new Vector(maxDistance, 0, maxDistance).add(topLeft).toLocation(world).getBlock().setType(Material.AMETHYST_BLOCK);

        canvas.drawText(10, 10, MinecraftFont.Font, "dist " + maxDistance);
        canvas.drawText(10, 20, MinecraftFont.Font, "pixel/blk " + pixelsPerBlock);

        {
            if (warps.size() >= 2 && !samplePaths.containsKey(group)) {
                BonfireWarp warpA = warps.get(0);
                BonfireWarp warpB = warps.get(1);
                samplePaths.put(group, new AStarPathFinder()
                        .findPath(
                                world,
                                groundVector(world, warpA.bonfirePosition.toBlockVector()),
                                groundVector(world, warpB.bonfirePosition.toBlockVector())
                        )
                );
            } else if (samplePaths.containsKey(group)) {
                List<BlockVector> vectors = samplePaths.get(group);
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
                canvas.drawText(10, 30, MinecraftFont.Font,
                        "path size " + vectors.size()
                );
            }
        }

        {
            MapCursorCollection cursors = new MapCursorCollection();
            for (BonfireWarp warp : warps) {
                Vector toWarp = warp.bonfirePosition.clone().subtract(center).add(new Vector(1, 0, 1));
                toWarp.multiply(pixelsPerBlock * 2);
                cursors.addCursor(new MapCursor(
                        (byte) Mth.clamp(toWarp.getX(), -128, 127),
                        (byte) Mth.clamp(toWarp.getZ(), -128, 127),
                        (byte) 8,
                        MapCursor.Type.PLAYER_OFF_LIMITS,
                        true,
                        warp.id()
                ));
            }
            Vector toPlayer = player.getLocation().add(0.5, 0, 0.5).toVector().subtract(center);
            toPlayer.multiply(pixelsPerBlock * 2);
            cursors.addCursor(new MapCursor(
                    (byte) Mth.clamp(toPlayer.getX(), -128, 127),
                    (byte) Mth.clamp(toPlayer.getZ(), -128, 127),
                    (byte) (Math.round((player.getYaw() / 360) * 16) & 15),
                    MapCursor.Type.PLAYER,
                    true
            ));
            canvas.setCursors(cursors);
        }
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

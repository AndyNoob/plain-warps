package comfortable_andy.plain_warps.warp.bonfire;

import comfortable_andy.plain_warps.PlainWarpsMain;
import comfortable_andy.plain_warps.util.astar.AStarPathFinder;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
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
        Vector center = warps.stream()
                .map(b -> b.bonfirePosition)
                .reduce(new Vector(), Vector::add, Vector::add)
                .multiply(1d / warps.size());

        int maxDistance = warps.stream()
                .mapToInt(w -> Math.max(
                        Math.abs(w.bonfirePosition.getBlockX() - center.getBlockX()),
                        Math.abs(w.bonfirePosition.getBlockZ() - center.getBlockZ())
                )).max().orElse(0) + 10;
        map.setCenterX(center.getBlockX());
        map.setCenterZ(center.getBlockZ());

        double blockPerPixel = (maxDistance * 2d / 128);
        canvas.drawText(10, 10, MinecraftFont.Font, "dist " + maxDistance);
        canvas.drawText(10, 20, MinecraftFont.Font, "per pixel " + blockPerPixel);

        double scale = 1d / blockPerPixel * 2;
        canvas.drawText(10, 30, MinecraftFont.Font, "scale " + scale);

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
                    Vector offset = vector.clone().subtract(center);

                }
                canvas.drawText(10, 40, MinecraftFont.Font, "path size " + vectors.size());
            }
        }

        {
            MapCursorCollection cursors = new MapCursorCollection();
            for (BonfireWarp warp : warps) {
                Vector toWarp = warp.bonfirePosition.clone().subtract(center);
                toWarp.multiply(scale);
                cursors.addCursor(new MapCursor(
                        (byte) (toWarp.getBlockX()),
                        (byte) (toWarp.getBlockZ()),
                        (byte) 8,
                        MapCursor.Type.PLAYER_OFF_LIMITS,
                        true,
                        warp.id()
                ));
            }
            Vector toPlayer = player.getLocation().toVector().subtract(center);
            toPlayer.multiply(scale);
            cursors.addCursor(new MapCursor(
                    (byte) (toPlayer.getBlockX()),
                    (byte) (toPlayer.getBlockZ()),
                    (byte) (Math.round((player.getYaw() / 360) * 16) & 15),
                    MapCursor.Type.PLAYER,
                    true
            ));
            canvas.setCursors(cursors);
        }
    }

    static BlockVector groundVector(World world, BlockVector vec) {
        if (vec.toLocation(world).getBlock().getRelative(0, -1, 0).isSolid()) return vec;
        return vec.clone().add(new Vector(0, -1, 0)).toBlockVector();
    }
}

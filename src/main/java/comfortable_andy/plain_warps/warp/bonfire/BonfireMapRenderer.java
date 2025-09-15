package comfortable_andy.plain_warps.warp.bonfire;

import comfortable_andy.plain_warps.PlainWarpsMain;
import comfortable_andy.plain_warps.util.astar.AStarPathFinder;
import net.kyori.adventure.text.Component;
import net.minecraft.util.Mth;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.map.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;

public class BonfireMapRenderer extends MapRenderer {

    public static final NamespacedKey GROUP = new NamespacedKey(PlainWarpsMain.getInstance(), "map_group");
    static Map<PathKey, Collection<BlockVector>> COMPUTED_PATHS = new ConcurrentHashMap<>();
    static final Executor MAIN_THREAD = Bukkit.getScheduler()
            .getMainThreadExecutor(PlainWarpsMain.getInstance());

    public final ExecutorService computeService = Executors.newSingleThreadExecutor();
    private final Map<RenderKey, RenderState> states = new ConcurrentHashMap<>();
    private final World world;

    public BonfireMapRenderer(World world) {
        super(true);
        this.world = world;
    }

    @Override
    public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
        if (!player.getWorld().equals(world)) return;

        String group = player.getPersistentDataContainer().get(GROUP, PersistentDataType.STRING);
        List<BonfireWarp> warps = PlainWarpsMain.getInstance().getWarps().stream()
                .filter(s -> s instanceof BonfireWarp warp
                        && world.equals(s.getLocation().getWorld())
                        && Objects.equals(group, warp.group))
                .map(s -> (BonfireWarp) s)
                .toList();
        updatePaths(warps, group, world);
        if (warps.isEmpty()) return;

        RenderState state = states.computeIfAbsent(new RenderKey(group), k -> new RenderState(k.group, world));

        if (state.lastRendered != null) {
            paintBuffer(canvas, player, state, warps);
        }
        Vector center = getCenter(warps);
        RenderRequest req = new RenderRequest(center, getPixelsPerBlock(warps, center));
        if (req.pixelsPerBlock < 1) canvas.drawText(5, 5, MinecraftFont.Font, "Down-sampling");
        else canvas.drawText(5, 5, MinecraftFont.Font, "Up-scaling");
        if (state.queued) canvas.drawText(5, 15, MinecraftFont.Font, "Queuing");
        if (world.getFullTime() % 20 != 0 && req.equals(state.lastRendered)) {
            canvas.drawText(5, 15, MinecraftFont.Font, "Not rendering");
            return;
        }

        state.latest = req;
        if (state.queued) return;
        player.sendActionBar(Component.text("New render!"));
        state.queued = true;
        state.tail = state.tail.thenCompose(v -> drain(state))
                .whenComplete((v, e) -> state.queued = false);
    }

    private CompletableFuture<Void> drain(RenderState state) { // (gpt-5)
        // chain builds until s.latest is empty or equals lastRendered
        RenderRequest next = state.latest;
        if (next == null) return CompletableFuture.completedFuture(null);
        state.latest = null; // claim it

        return buildRendering(state, next).thenCompose(v -> drain(state));
    }

    private CompletableFuture<Void> buildRendering(RenderState state, RenderRequest latest) {
        return CompletableFuture
                .runAsync(() -> updateChunks(state, latest), MAIN_THREAD)
                .thenApplyAsync(a -> renderPixels(state, latest), computeService)
                .completeOnTimeout(null, 1500, TimeUnit.MILLISECONDS)
                .thenAcceptAsync(buf -> {
                    state.buffer = buf;
                    state.dirty = true;
                    state.lastRendered = latest;
                }, MAIN_THREAD);
    }

    private static void paintBuffer(@NotNull MapCanvas canvas, @NotNull Player player, RenderState state, List<BonfireWarp> warps) {
        if (state.dirty && state.buffer != null) {
            state.dirty = false;
            for (int x = 0; x < 128; x++) {
                for (int y = 0; y < 128; y++) {
                    int rgb = state.buffer[x + y * 128];
                    canvas.setPixelColor(x, y, new Color(rgb));
                }
            }
        }
        MapCursorCollection cursors = new MapCursorCollection();
        for (BonfireWarp warp : warps) {
            Vector toWarp = warp.bonfirePosition.clone().subtract(state.lastRendered.center)
                    .add(new Vector(0.5, 0, 0.5));
            toWarp.multiply(state.lastRendered.pixelsPerBlock * 2);
            cursors.addCursor(new MapCursor(
                    (byte) Mth.clamp(toWarp.getX() + state.lastRendered.pixelsPerBlock, -128, 127),
                    (byte) Mth.clamp(toWarp.getZ() + state.lastRendered.pixelsPerBlock, -128, 127),
                    (byte) 8,
                    MapCursor.Type.PLAYER_OFF_LIMITS,
                    true,
                    warp.id()
            ));
        }
        Vector toPlayer = player.getLocation().toVector().subtract(state.lastRendered.center);
        toPlayer.multiply(state.lastRendered.pixelsPerBlock * 2);
        cursors.addCursor(new MapCursor(
                (byte) Mth.clamp(toPlayer.getX() + state.lastRendered.pixelsPerBlock, -128, 127),
                (byte) Mth.clamp(toPlayer.getZ() + state.lastRendered.pixelsPerBlock, -128, 127),
                (byte) (Math.round((player.getYaw() / 360) * 16) & 15),
                MapCursor.Type.PLAYER,
                true
        ));
        canvas.setCursors(cursors);
    }

    private static double getPixelsPerBlock(List<BonfireWarp> warps, Vector center) {
        double maxDistance = warps.stream()
                .mapToDouble(w -> w.bonfirePosition.distance(center)).max().orElse(0) * 2.5;
        if (maxDistance <= 0) return 1;
        double pixelsPerBlock = 128d / maxDistance;
        if (pixelsPerBlock < 1) {
            double blocksPerPixel = Math.ceil(1 / pixelsPerBlock);
            pixelsPerBlock = 1 / blocksPerPixel;
        } else {
            pixelsPerBlock = Math.ceil(pixelsPerBlock);
        }
        return pixelsPerBlock;
    }

    private static int[] renderPixels(RenderState state, RenderRequest request) {
        double blocksPerPixel = 1 / request.pixelsPerBlock;
        boolean downSampling = request.pixelsPerBlock < 1;
        int roundedBpp = downSampling ? (int) Math.round(blocksPerPixel) : 1;
        int roundedPpb = downSampling ? 1 : (int) Math.round(request.pixelsPerBlock);
        double centerX = request.center.getX();
        double centerZ = request.center.getZ();
        int[] buffer = new int[128 * 128];
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                if (downSampling) {
                    int r = 0;
                    int g = 0;
                    int b = 0;
                    int n = 0;
                    for (int i = 0; i < roundedBpp; i++) {
                        for (int j = 0; j < roundedBpp; j++) {
                            int bx = (int) Math.floor(((x - 64) * blocksPerPixel + i) + centerX);
                            int bz = (int) Math.floor(((y - 64) * blocksPerPixel + j) + centerZ);
                            int argb = grabColor(state, bx, bz);
                            if (argb == 0) continue;

                            r += (argb >> 16) & 255;
                            g += (argb >> 8) & 255;
                            b += argb & 255;
                            n++;
                        }
                    }
                    if (n > 0) {
                        int c = 0xFF000000 | ((r/n) << 16) | ((g/n) << 8) | (b/n);
                        buffer[y * 128 + x] = c;
                    }
                } else {
                    int bx = (int) Math.floor(((x - 64) * blocksPerPixel) + centerX);
                    int bz = (int) Math.floor(((y - 64) * blocksPerPixel) + centerZ);
                    int rgb = grabColor(state, bx, bz);
                    if (rgb != 0) {
                        for (int i = 0; i < roundedPpb && /* don't go past the canvas edge (gpt-5)*/ x + i < 128; i++) {
                            for (int j = 0; j < roundedPpb && y + j < 128; j++) {
                                buffer[(y + j) * 128 + (x + i)] = rgb;
                            }
                        }
                    }
                    if (roundedPpb > 1) y += roundedPpb - 1;
                }
            }
            if (roundedPpb > 1) x += roundedPpb - 1;
        }
        return buffer;
    }

    void updateChunks(RenderState state, RenderRequest req) {
        PathKey pathKey = new PathKey(world.getUID(), state.group);
        Collection<BlockVector> vectors = COMPUTED_PATHS.getOrDefault(pathKey, List.of());
        if (vectors.isEmpty()) return;
        int width = (int) Math.ceil(128 / req.pixelsPerBlock);
        int minX = (int) (req.center.getX() - width / 2d);
        int minZ = (int) (req.center.getZ() - width / 2d);
        int maxX = minX + width;
        int maxZ = minZ + width;
        for (int x = (minX & ~15); x < maxX; x += 16) { // & ~15 to round down to nearest multiple of 16
            for (int z = (minZ & ~15); z < maxZ; z += 16) {
                int cx = (x >> 4);
                int cz = (z >> 4);
                int[] colors = updateChunk(state, world, cx, cz, vectors);
                if (colors == null) continue;
                state.putChunk(cx, cz, colors);
            }
        }
    }

    private static int @Nullable [] updateChunk(RenderState state, World world, int cx, int cz, Collection<BlockVector> vectors) {
        int chunkMinX = cx << 4;
        int chunkMinZ = cz << 4;
        int chunkMaxX = chunkMinX + 16;
        int chunkMaxZ = chunkMinZ + 16;
        List<BlockVector> list = new ArrayList<>();
        for (BlockVector w : vectors) {
            if (w.getX() >= chunkMinX && w.getX() < chunkMaxX
                    && w.getZ() >= chunkMinZ && w.getZ() < chunkMaxZ) {
                list.add(w);
            }
        }
        if (list.isEmpty()) return null;
        int[] chunkCol = new int[16 * 16];
        BlockVector cur = new BlockVector(0, 0, 0);
        for (int xx = 0; xx < 16; xx++) {
            cur.setX(chunkMinX + xx);
            for (int zz = 0; zz < 16; zz++) {
                cur.setZ(chunkMinZ + zz);
                for (BlockVector vector : list) {
                    double dx = vector.getX() - cur.getX();
                    double dz = vector.getZ() - cur.getZ();
                    if (dx * dx + dz * dz > 9) continue;
                    cur.setY(vector.getBlockY());
                    Block block = findBlock(state.group, world, cur);
                    if (block == null) continue;
                    chunkCol[(zz << 4) | xx] = getMapColor(block).getRGB();
                }
            }
        }
        return chunkCol;
    }

    static int grabColor(RenderState state, int x, int z) {
        int[] cols = state.grabChunk(x >> 4, z >> 4);
        if (cols == null) return 0;
        return cols[((z & 15) << 4) | (x & 15)];
    }

    private static void updatePaths(List<BonfireWarp> warps, String group, World world) {
        PathKey pathKey = new PathKey(world.getUID(), group);
        if (warps.size() >= 2 && !COMPUTED_PATHS.containsKey(pathKey)) {
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
            COMPUTED_PATHS.put(pathKey, locs);
        } else if (COMPUTED_PATHS.containsKey(pathKey)) {
            if ((world.getFullTime() % 7) != 0) return;
            Collection<BlockVector> vectors = COMPUTED_PATHS.get(pathKey);
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

    static @Nullable Block findBlock(String group, World world, BlockVector vector) {
        PathKey pathKey = new PathKey(world.getUID(), group);
        if (COMPUTED_PATHS.containsKey(pathKey)) {
            BlockVector closest = COMPUTED_PATHS.get(pathKey)
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

    static @NotNull Vector getCenter(List<BonfireWarp> warps) {
        Vector center;
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
        return center;
    }

    static @NotNull Color getMapColor(Block block) {
        return new Color(block.getBlockData().getMapColor().asRGB());
    }

    static BlockVector groundVector(World world, BlockVector vec) {
        if (vec.toLocation(world).getBlock().getRelative(0, -1, 0).isSolid())
            return vec;
        return vec.clone().add(new Vector(0, -1, 0)).toBlockVector();
    }

    record PathKey(UUID worldId, String group) {}

    record RenderKey(String group) {}

    static class RenderState {
        final String group;
        final World world;
        volatile boolean queued = false;
        volatile int[] buffer;
        volatile boolean dirty = false;
        volatile RenderRequest latest;
        volatile RenderRequest lastRendered;
        Map<Long, int[]> chunks = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, int[]> e) {
                return size() > 512;
            }
        }); // use lru (gpt-5)
        CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);

        RenderState(String group, World world) {
            this.group = group;
            this.world = world;
        }

        void putChunk(int cx, int cz, int[] colors) {
            long key = getKey(cx, cz);
            chunks.put(key, colors);
        }

        int[] grabChunk(int cx, int cz) {
            long key = getKey(cx, cz);
            return chunks.computeIfAbsent(key, k -> {
                var path = COMPUTED_PATHS.getOrDefault(
                        new PathKey(world.getUID(), group),
                        List.of()
                );
                try {
                    return CompletableFuture.supplyAsync(() -> updateChunk(
                            this,
                            world,
                            cx,
                            cz,
                            path
                    ), MAIN_THREAD).get(50, TimeUnit.MILLISECONDS);
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                } catch (TimeoutException e) {
                    return new int[16 * 16];
                }
            });
        }

        private static long getKey(long cx, int cz) {
            return (cx << 32) | (cz & 0xffffffffL);
        }
    }

    record RenderRequest(Vector center, double pixelsPerBlock) {
    }

}

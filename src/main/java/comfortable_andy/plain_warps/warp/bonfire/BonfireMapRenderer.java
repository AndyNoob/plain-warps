package comfortable_andy.plain_warps.warp.bonfire;

import comfortable_andy.plain_warps.PlainWarpsMain;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.map.*;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.Objects;

public class BonfireMapRenderer extends MapRenderer {

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
        if (warps.isEmpty()) {
            canvas.drawText(10, 10, MinecraftFont.Font, "No warps found under group '" + group + "'");
            return;
        }
        Vector center = warps.stream()
                .map(b -> b.bonfirePosition)
                .reduce(new Vector(), Vector::add, Vector::add)
                .multiply(1d / warps.size());
        player.getWorld().getBlockAt(center.toLocation(player.getWorld())).setType(Material.DIAMOND_BLOCK);
        int maxDistance = warps.stream()
                        .mapToInt(w -> Math.max(
                                Math.abs(w.bonfirePosition.getBlockX() - center.getBlockX()),
                                Math.abs(w.bonfirePosition.getBlockZ() - center.getBlockZ())
                        )).max().orElse(0) + 10;
        map.setCenterX(center.getBlockX());
        map.setCenterZ(center.getBlockZ());
        canvas.setPixelColor(128 / 2, 128 / 2, Color.BLACK);
        MapCursorCollection cursors = new MapCursorCollection();
        double blockPerPixel = (maxDistance * 2d / 128);
        canvas.drawText(10, 10, MinecraftFont.Font, "dist " + maxDistance);
        canvas.drawText(10, 20, MinecraftFont.Font, "per pixel " + blockPerPixel);
        double scale = 1d / blockPerPixel * 2;
        canvas.drawText(10, 30, MinecraftFont.Font, "scale " + scale);
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

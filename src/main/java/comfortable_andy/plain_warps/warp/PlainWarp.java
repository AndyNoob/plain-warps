package comfortable_andy.plain_warps.warp;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.joml.Vector2f;

import java.text.DecimalFormat;
import java.util.UUID;

@RequiredArgsConstructor
public class PlainWarp implements Warp {
    private final String id;
    private final UUID worldId;
    private final Vector pos;
    private final Vector2f rot;

    public PlainWarp(String id, Location location) {
        this(id, location.getWorld().getUID(), location.toVector(), new Vector2f(location.getPitch(), location.getYaw()));
    }

    public String getPerm() {
        return "plain_warps.warp." + id;
    }

    public Location getLocation() {
        return new Location(Bukkit.getWorld(worldId), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, rot.y, rot.x);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlainWarp warp)) return false;
        return id.equals(warp.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        World world = Bukkit.getWorld(worldId);
        return "warp named '" + id + '\'' +
                " in world '" + (world == null ? worldId + " (not loaded/doesn't exist)" : world.getName()) +
                "' at " + pos.toString().replace(",", ", ") +
                " [" + rot.toString(new DecimalFormat("#.##")).replace("(", "").replace(")", "") + "]";
    }

    public String id() {
        return id;
    }

}

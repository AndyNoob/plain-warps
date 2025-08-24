package comfortable_andy.plain_warps.warp;

import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.RotationResolver;
import io.papermc.paper.math.Rotation;
import lombok.AllArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.joml.Vector2f;

import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
public class PlainWarp implements Warp {

    @SuppressWarnings("UnstableApiUsage")
    public static final List<WarpProperty<?, ?, ?>> PROPERTIES = List.of(
            new WarpProperty<PlainWarp, Vector, FinePositionResolver>(
                    "pos",
                    (w, p) -> w.pos = p,
                    w -> w.pos,
                    ArgumentTypes.finePosition(),
                    (c, o) -> o.resolve(c).toVector()
            ),
            new WarpProperty<PlainWarp, Vector2f, RotationResolver>(
                    "rot",
                    (w, p) -> w.rot = p,
                    w -> w.rot,
                    ArgumentTypes.rotation(),
                    (c, o) -> {
                        Rotation resolved = o.resolve(c);
                        return new Vector2f(resolved.pitch(), resolved.yaw());
                    }
            ),
            new WarpProperty<PlainWarp, UUID, World>(
                    "world",
                    (w, p) -> w.worldId = p,
                    w -> w.worldId,
                    ArgumentTypes.world(),
                    (c, o) -> o.getUID()
            )
    );

    private final String id;
    private UUID worldId;
    private Vector pos;
    private Vector2f rot;

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

    @Override
    public List<WarpProperty<? extends Warp, ?, ?>> properties() {
        return PROPERTIES;
    }
}

package comfortable_andy.plain_warps.warp;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import comfortable_andy.plain_warps.PlainWarpsMain;
import lombok.Getter;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.bukkit.*;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.joml.Math;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class BonfireWarp extends PlainWarp {

    private static final Integer BLOCK_STATE_ID;

    static {
        try {
            Field stateId = Display.BlockDisplay.class.getDeclaredField("DATA_BLOCK_STATE_ID");
            stateId.trySetAccessible();
            BLOCK_STATE_ID = ((EntityDataAccessor<?>) stateId.get(null)).id();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
    private static final ProtocolManager MANAGER = ProtocolLibrary.getProtocolManager();

    public final Vector bonfirePosition;
    public final Vector swordDir;
    public final Vector swordOffset;
    public @Nullable UUID campfireId = null;
    public @Nullable UUID swordDisplayId = null;
    private @Getter transient ItemDisplay swordDisplay;
    private @Getter transient BlockDisplay campfireDisplay;

    public BonfireWarp(String id, Location location, Vector bonfirePosition) {
        super(id, location);
        this.bonfirePosition = bonfirePosition;
        this.swordDir = new Vector(0, 1, 0)
                .rotateAroundX(Math.toRadians(ThreadLocalRandom.current().nextDouble(15, 35)))
                .rotateAroundY(Math.toRadians(ThreadLocalRandom.current().nextDouble(360)));
        this.swordOffset = new Vector(
                ThreadLocalRandom.current().nextDouble(-0.15, 0.15),
                0.35,
                ThreadLocalRandom.current().nextDouble(-0.15, 0.15)
        );
    }

    @Override
    public boolean togglePersistentLockState(Player player) {
        boolean locked = super.togglePersistentLockState(player);
        showLockState(player, isLockedFor(player));
        return locked;
    }

    public void spawn() {
        World world = getLocation().getWorld();
        Location location = bonfirePosition.toLocation(world);
        if (campfireDisplay == null){
            if (campfireId != null) {
                Entity entity = Bukkit.getEntity(campfireId);
                if (entity instanceof BlockDisplay display) campfireDisplay = display;
            }
            if (campfireDisplay == null) {
                campfireDisplay = world.spawn(location, BlockDisplay.class, b -> setupBlockDisplay(world, b));
            }
        } else setupBlockDisplay(world, campfireDisplay);
        campfireId = campfireDisplay.getUniqueId();

        if (swordDisplay == null) {
            if (swordDisplayId != null) {
                Entity entity = Bukkit.getEntity(swordDisplayId);
                if (entity instanceof ItemDisplay display) swordDisplay = display;
            }
            if (swordDisplay == null) {
                swordDisplay = world.spawn(
                        location.clone().add(swordOffset),
                        ItemDisplay.class,
                        i -> setupItemDisplay(world, i)
                );
            }
        } else setupItemDisplay(world, swordDisplay);
        swordDisplayId = swordDisplay.getUniqueId();
    }

    public void remove() {
        if (swordDisplayId != null) {
            Entity entity = Bukkit.getEntity(swordDisplayId);
            if (entity != null) entity.remove();
        }
        if (campfireId != null) {
            Entity entity = Bukkit.getEntity(campfireId);
            if (entity != null) entity.remove();
        }
    }

    public void playAndShowUnlock(Player player) {
        if (swordDisplay == null)
            spawn();

        player.showEntity(PlainWarpsMain.getInstance(), swordDisplay);

        PacketContainer teleportSword = MANAGER.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
        teleportSword.getIntegers().write(0, swordDisplay.getEntityId());
        var pos = swordDisplay.getLocation().add(swordDir.clone().multiply(3));
        InternalStructure positionMoveRotation = teleportSword.getStructures().read(0);
        positionMoveRotation.getVectors()
                .write(0, pos.toVector())
                .write(1, new Vector());
        positionMoveRotation.getFloat()
                .write(0, pos.getYaw())
                .write(1, pos.getPitch());
        MANAGER.sendServerPacket(player, teleportSword);

        {
            PacketContainer setTpDuration = MANAGER.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            setTpDuration.getIntegers().write(0, swordDisplay.getEntityId());
            WrappedDataWatcher watcher = new WrappedDataWatcher(swordDisplay);
            watcher.setInteger(Display.DATA_POS_ROT_INTERPOLATION_DURATION_ID.id(), 18, true);
            setTpDuration.getDataValueCollectionModifier().write(0, watcher.toDataValueCollection());
            MANAGER.sendServerPacket(player, setTpDuration);
        }

        PlainWarpsMain.runLater(() -> {
            positionMoveRotation.getVectors().write(0,swordDisplay.getLocation().toVector());
            MANAGER.sendServerPacket(player, teleportSword);
        }, 1);

        PlainWarpsMain.runLater(() -> {
            boolean lit = true;
            updateCampfireLit(player, lit);
        }, 17);
//        drawLine(swordDisplay.getLocation(), swordDir);
    }

    private void updateCampfireLit(Player player, boolean lit) {
        PacketContainer setLitFire = MANAGER.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        setLitFire.getIntegers().write(0, campfireDisplay.getEntityId());
        WrappedDataWatcher watcher = new WrappedDataWatcher(campfireDisplay);
        Campfire campfireData = (Campfire) campfireDisplay.getBlock().clone();
        campfireData.setLit(lit);
        watcher.setBlockState(BLOCK_STATE_ID, WrappedBlockData.createData(campfireData), true);
        setLitFire.getDataValueCollectionModifier().write(0, watcher.toDataValueCollection());
        MANAGER.sendServerPacket(player, setLitFire);
    }

    @SuppressWarnings("unused")
    private void drawLine(Location start, Vector dir) {
        for (double i = 0; i < 10 /* 10 is the length of the line */; i += 0.5 /* 0.5 is the gap between each particle */) {
            Location cur = start.clone().add(dir.clone().multiply(i));
            // display particle at 'start' (display)
            start.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, cur, 1, 0, 0, 0, 0, null, true);
        }
    }

    public void showLockState(Player player, boolean toLock) {
        System.out.println(toLock);
        if (swordDisplay == null)
            spawn();
        if (toLock) {
            player.hideEntity(PlainWarpsMain.getInstance(), swordDisplay);
        } else {
            player.showEntity(PlainWarpsMain.getInstance(), swordDisplay);
        }
        updateCampfireLit(player, !toLock);
    }

    @Override
    public String toString() {
        return "bonfire " + super.toString();
    }

    private void setupBlockDisplay(World world, BlockDisplay b) {
        Campfire data = (Campfire) Material.SOUL_CAMPFIRE.createBlockData();
        data.setLit(false);
        b.setBlock(data);
        b.teleport(bonfirePosition.toLocation(world));
    }

    private void setupItemDisplay(World world, ItemDisplay i) {
        i.setItemStack(new ItemStack(Material.GOLDEN_SWORD));
        i.setTransformation(new Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f(1, 1, 1),
                new Quaternionf().rotateXYZ(
                        Math.toRadians(90),
                        Math.toRadians(0),
                        Math.toRadians(135)
                )
        ));
        i.teleport(bonfirePosition
                .toLocation(world)
                .add(0.5, 0, 0.5)
                .add(swordOffset)
                .setDirection(swordDir));
//        i.setVisibleByDefault(false);
    }

    @Contract("null -> null")
    public static @Nullable BonfireWarp findWarp(@Nullable BlockDisplay campfireDisplay) {
        if (campfireDisplay == null) return null;
        return (BonfireWarp) PlainWarpsMain.getInstance().getWarps()
                .stream()
                .filter(w -> w instanceof BonfireWarp warp
                        && Objects.equals(warp.campfireId, campfireDisplay.getUniqueId()))
                .findFirst()
                .orElse(null);
    }

}

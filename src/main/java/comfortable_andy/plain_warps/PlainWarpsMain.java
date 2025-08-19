package comfortable_andy.plain_warps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import com.google.gson.reflect.TypeToken;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import comfortable_andy.plain_warps.argument.WarpsArgumentType;
import comfortable_andy.plain_warps.listener.BonfireLoadListener;
import comfortable_andy.plain_warps.warp.BonfireWarp;
import comfortable_andy.plain_warps.warp.PlainWarp;
import comfortable_andy.plain_warps.warp.Warp;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.PaperCommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.FinePosition;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import lombok.Getter;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.Math;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("UnstableApiUsage")
public final class PlainWarpsMain extends JavaPlugin {

    private static PlainWarpsMain INST;
    private static final Gson GSON = new GsonBuilder()
            .setStrictness(Strictness.LENIENT)
            .disableHtmlEscaping()
            .registerTypeAdapterFactory(RuntimeTypeAdapterFactory
                    .of(Warp.class, "warpType")
                    .registerSubtype(BonfireWarp.class, "bonfire")
                    .registerSubtype(PlainWarp.class, "plain")
            )
            .create();
    private static final TypeToken<Set<Warp>> TOKEN = new TypeToken<>() {
    };
    @Getter
    private final Set<Warp> warps = new HashSet<>();
    private File warpsFile;

    @Override
    public void onEnable() {
        INST = this;
        warpsFile = new File(getDataFolder(), "warps.json");
        saveResource(
                getDataFolder().toPath().relativize(warpsFile.toPath()).toString(),
                false
        );
        loadWarps();
        getLifecycleManager().registerEventHandler(
                LifecycleEvents.COMMANDS,
                this::registerCommands
        );
        new BukkitRunnable() {
            @Override
            public void run() {
                saveWarps();
                for (Warp warp : warps) {
                    String perm = warp.getPerm();
                    if (getServer().getPluginManager().getPermission(perm) == null)
                        getServer().getPluginManager()
                                .addPermission(new Permission(perm, PermissionDefault.OP));
                }
            }
        }.runTaskTimer(this, 0, 20 * 15);
        getServer().getPluginManager().registerEvents(new BonfireLoadListener(), this);
    }

    private void registerCommands(@NotNull ReloadableRegistrarEvent<@NotNull Commands> event) {
        final Commands commands = event.registrar();
        final Command<CommandSourceStack> teleport = s -> {
            final Warp warp = s.getArgument("warp", Warp.class);
            final CommandSender sender = s.getSource().getSender();
            if (warp.isLockedFor(sender)) {
                throw new SimpleCommandExceptionType(Component.literal("You can't teleport there!")).create();
            }
            final Player target;
            if (!(sender instanceof Player player)) {
                target = s.getArgument("target", Player.class);
            } else target = player;
            target.teleport(warp.getLocation());
            sender.sendMessage("Done!");
            return Command.SINGLE_SUCCESS;
        };
        final var playerRoot = Commands
                .literal("warp")
                .requires(s -> s.getSender().hasPermission("plain_warps.warp.command"))
                .then(Commands
                        .argument("warp", new WarpsArgumentType(this))
                        .then(Commands
                                .argument("target", ArgumentTypes.player())
                                .executes(teleport))
                        .executes(teleport)
                );

        final Command<CommandSourceStack> addWarp = s -> {
            final CommandSender sender = s.getSource().getSender();
            FinePosition position = null;
            try {
                position = s.getArgument("pos", FinePositionResolver.class).resolve(s.getSource());
            } catch (Exception e) {
                if (sender instanceof Player player) {
                    position = player.getLocation();
                }
            }
            Vector2f rot = new Vector2f();
            try {
                Coordinates coordinates = s.getArgument("rot", Coordinates.class);
                Vec2 rotation = coordinates.getRotation(((PaperCommandSourceStack) s.getSource()).getHandle());
                rot.set(rotation.x, rotation.y);
            } catch (Exception e) {
                if (sender instanceof Player player) {
                    rot.set(player.getPitch(), player.getYaw());
                }
            }
            if (position == null)
                throw new SimpleCommandExceptionType(Component.literal("You need a position!")).create();
            final Location location = position.toLocation(s.getSource().getLocation().getWorld());
            location.setPitch(rot.x);
            location.setYaw(rot.y);
            BlockPosition bonfire = null;
            try {
                BlockPositionResolver positionResolver = s.getArgument("bonfire position", BlockPositionResolver.class);
                bonfire = positionResolver.resolve(s.getSource());
            } catch (Exception ignored) {
            }
            final PlainWarp warp = bonfire == null ? new PlainWarp(
                    s.getArgument("id", String.class),
                    location
            ) : new BonfireWarp(s.getArgument("id", String.class), location, bonfire.offset(0, 1, 0).toVector());
            if (warps.add(warp)) {
                sender.sendMessage("Added " + warp + "!");
                if (warp instanceof BonfireWarp bonfireWarp) bonfireWarp.spawn();
            } else
                throw new SimpleCommandExceptionType(Component.literal("A warp with id '" + warp.id() + "' already exists!")).create();
            return Command.SINGLE_SUCCESS;
        };
        var testSubCommand = Commands
                .literal("test")
                .requires(c -> c.getSender().isOp())
                .then(Commands
                        .argument("warp", new WarpsArgumentType(this))
                        .executes(c -> {
                            if (!(c.getSource().getSender() instanceof Player player)) {
                                throw new SimpleCommandExceptionType(Component.literal("You need to be a player.")).create();
                            }
                            if (!(c.getArgument("warp", Warp.class) instanceof BonfireWarp warp)) {
                                throw new SimpleCommandExceptionType(Component.literal("You need to specify a bonfire warp.")).create();
                            }
                            warp.playAndShowUnlock(player);
                            Bukkit.getScheduler().runTaskLater(
                                    this,
                                    () -> warp.showLockState(player, true),
                                    20 * 3
                            );
                            return 1;
                        })
                )
                .executes(c -> {
                    if (!(c.getSource().getSender() instanceof Player player)) {
                        throw new SimpleCommandExceptionType(Component.literal("You need to be a player.")).create();
                    }
                    Location location = player.getLocation()
                            .toBlockLocation()
                            .add(player.getFacing().getDirection().multiply(5))
                            .setRotation(0, 0);
                    BlockDisplay campfire = player.getWorld().spawn(location, BlockDisplay.class, b -> {
                        Campfire data = (Campfire) Material.SOUL_CAMPFIRE.createBlockData();
                        data.setLit(false);
                        b.setBlock(data);
                    });
                    Vector directionOfSword = new Vector(0, 1, 0)
                            .rotateAroundX(Math.toRadians(-15))
                            .rotateAroundY(Math.toRadians(15));
                    ItemDisplay sword = player.getWorld().spawn(location.clone().add(.5, 0.2, 0).add(directionOfSword), ItemDisplay.class, i -> {
                        i.setItemStack(new ItemStack(Material.GOLDEN_SWORD));
                        i.setTeleportDuration(18);
                        Bukkit.getScheduler().runTaskLater(this, () ->
                        {
                            i.teleport(i.getLocation().subtract(directionOfSword.clone().multiply(0.75)));
                            Bukkit.getScheduler().runTaskLater(this, () -> {
                                Campfire fireData = (Campfire) campfire.getBlock();
                                fireData.setLit(true);
                                campfire.setBlock(fireData);
                            }, i.getTeleportDuration());
                        }, 8);
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
                        i.teleport(i.getLocation().setDirection(directionOfSword));
                    });
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        campfire.remove();
                        sword.remove();
                    }, 20 * 5);
                    return 1;
                });

        var unlockExecute = (Command<CommandSourceStack>) s -> {
            Player target = null;
            try {
                target = s.getArgument("target", Player.class);
            } catch (Exception ignored) {
            }
            if (!(s.getSource().getSender() instanceof Player) && target == null) {
                throw new SimpleCommandExceptionType(Component.literal("Please specify a target")).create();
            }
            if (target == null) target = (Player) s.getSource().getSender();
            Warp warp = s.getArgument("warp", Warp.class);
            boolean toggledOn = warp.togglePersistentLockState(target);
            boolean lockedFor = warp.isLockedFor(target);
            String state = toggledOn ? "locked" : "unlocked";
            if (lockedFor != toggledOn) {
                s.getSource().getSender().sendMessage("Toggled warp '" + warp.id() + "', however, the target has permission override for the warp!");
            } else {
                if (target != s.getSource().getSender())
                    s.getSource()
                            .getSender()
                            .sendMessage("Warp '" + warp.id() + "' is now " + state + " for " + target.getName() + "!");
                target.sendMessage("Warp '" + warp.id() + "' is now " + state + "!");
            }
            return 1;
        };
        final var adminRoot = Commands
                .literal("warps")
                .requires(s -> s.getSender().hasPermission("plain_warps.warps.edit"))
                .then(Commands
                        .literal("add")
                        .then(Commands
                                .argument("id", StringArgumentType.string())
                                .executes(addWarp)
                                .then(Commands
                                        .argument("pos", ArgumentTypes.finePosition())
                                        .then(Commands
                                                .argument("rot", RotationArgument.rotation())
                                                .executes(addWarp)
                                                .then(Commands
                                                        .argument(
                                                                "bonfire position",
                                                                ArgumentTypes.blockPosition()
                                                        )
                                                        .executes(addWarp)
                                                )
                                        )
                                        .executes(addWarp)
                                )
                        )
                )
                .then(Commands.literal("remove")
                        .then(Commands
                                .argument("warp", new WarpsArgumentType(this))
                                .executes(s -> {
                                    Warp warp = s.getArgument("warp", Warp.class);
                                    if (warps.removeIf(w -> w.equals(warp))) {
                                        if (warp instanceof BonfireWarp bonfireWarp) bonfireWarp.remove();
                                        s.getSource().getSender().sendMessage("Removed " + warp + "!");
                                        return Command.SINGLE_SUCCESS;
                                    } else
                                        throw new SimpleCommandExceptionType(Component.literal("Could not remove warp.")).create();
                                })
                        )
                )
                .then(Commands.literal("toggle")
                        .then(Commands
                                .argument("warp", new WarpsArgumentType(this))
                                .executes(unlockExecute)
                                .then(Commands
                                        .argument("target", ArgumentTypes.player())
                                        .executes(unlockExecute)
                                )
                        )
                )
                .then(testSubCommand);
        commands.register(playerRoot.build());
        commands.register(adminRoot.build());
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::saveWarps, 20 * 5, 20 * 60);
    }

    @Override
    public void onDisable() {
        saveWarps();
    }

    public void loadWarps() {
        loadDataFile();
        warps.clear();
        try (FileReader reader = new FileReader(warpsFile)) {
            final Set<Warp> loaded = GSON.fromJson(reader, TOKEN);
            if (loaded != null) warps.addAll(loaded);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveWarps() {
        loadDataFile();
        try (FileWriter writer = new FileWriter(warpsFile)) {
            writer.write(GSON.toJson(warps, TOKEN.getType()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Warp getWarp(String id) {
        return warps.stream()
                .filter(w -> w.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    public void loadDataFile() {
        if (!warpsFile.exists()) {
            try {
                if (!(warpsFile.getParentFile().mkdirs() || warpsFile.createNewFile())) {
                    saveResource(
                            getDataFolder().toPath().relativize(warpsFile.toPath()).toString(),
                            false
                    );
                }
            } catch (Exception e) {
                throw new RuntimeException("Could not load/save warps\n" + GSON.toJson(warps), e);
            }
        }
    }

    public static void runLater(Runnable r, int ticks) {
        Bukkit.getScheduler().runTaskLater(getInstance(), r, ticks);
    }

    public static PlainWarpsMain getInstance() {
        return INST;
    }

}

package comfortable_andy.plain_warps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import comfortable_andy.plain_warps.argument.WarpsArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import lombok.Getter;
import net.minecraft.commands.arguments.coordinates.Vec2Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BlockVector;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2f;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public final class PlainWarpsMain extends JavaPlugin {

    private static final Gson GSON = new GsonBuilder().setLenient().disableHtmlEscaping().create();
    private static final TypeToken<Set<Warp>> TOKEN = new TypeToken<>() {
    };
    @Getter
    private final Set<Warp> warps = new HashSet<>();
    private File warpsFile;

    @Override
    public void onEnable() {
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
    }

    private void registerCommands(@NotNull ReloadableRegistrarEvent<Commands> event) {
        final Commands commands = event.registrar();
        final Command<CommandSourceStack> teleport = s -> {
            final Warp warp = s.getArgument("warp", Warp.class);
            final CommandSender sender = s.getSource().getSender();
            if (!sender.hasPermission("plain_warps.warps.*") && !sender.hasPermission(warp.getPerm())) {
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
                .requires(s -> s.getSender().hasPermission("plain_warps.warps.command"))
                .then(Commands
                        .argument("warp", new WarpsArgumentType(this))
                        .then(Commands
                                .argument("target", ArgumentTypes.player())
                                .executes(teleport))
                        .executes(teleport)
                );

        final Command<CommandSourceStack> addWarp = s -> {
            final CommandSender sender = s.getSource().getSender();
            BlockPosition position = null;
            try {
                position = s.getArgument("pos", BlockPositionResolver.class).resolve(s.getSource());
            } catch (Exception e) {
                if (sender instanceof Player player) {
                    position = player.getLocation().toBlock();
                }
            }
            Vector2f rot = new Vector2f();
            try {
                Vec2 vec2 = s.getArgument("rot", Vec2.class);
                rot.set(vec2.x, vec2.y);
            } catch (Exception e) {
                if (sender instanceof Player player) {
                    rot.set(player.getPitch(), player.getYaw());
                }
            }
            if (position == null)
                throw new SimpleCommandExceptionType(Component.literal("You need a position!")).create();
            final Warp warp = new Warp(
                    s.getArgument("id", String.class),
                    position.toLocation(s.getSource().getLocation().getWorld())
            );
            if (warps.add(warp))
                sender.sendMessage("Done! (" + warp + ")");
            else throw new SimpleCommandExceptionType(Component.literal("A warp with id '" + warp.id + "' already exists!")).create();
            return Command.SINGLE_SUCCESS;
        };
        final var adminRoot = Commands
                .literal("warps")
                .then(Commands
                        .literal("add")
                        .then(Commands
                                .argument("id", StringArgumentType.string())
                                .executes(addWarp)
                                .then(Commands
                                        .argument("pos", ArgumentTypes.blockPosition())
                                        .then(Commands
                                                .argument("rot", Vec2Argument.vec2())
                                                .executes(addWarp)
                                        )
                                        .executes(addWarp)
                                )
                        )
                )
                .then(Commands.literal("remove"));
        commands.register(playerRoot.build());
        commands.register(adminRoot.build());
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
            writer.write(GSON.toJson(warps));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Warp getWarp(String id) {
        return warps.stream().filter(w -> w.id.equals(id)).findFirst().orElse(null);
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
                throw new RuntimeException("Could not load/save warps\n" + warps, e);
            }
        }
    }

    public record Warp(String id, UUID worldId, BlockVector pos, Vector2f rot) {

        public Warp(String id, Location location) {
            this(id, location.getWorld().getUID(), location.toVector().toBlockVector(), new Vector2f(location.getPitch(), location.getYaw()));
        }

        public String getPerm() {
            return "plain_warps.warps." + id;
        }

        public Location getLocation() {
            return new Location(Bukkit.getWorld(worldId), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, rot.y, rot.x);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Warp warp = (Warp) o;

            return id.equals(warp.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return "id='" + id + '\'' +
                    ", world=" + Bukkit.getWorld(worldId) +
                    ", pos=" + pos +
                    ", rot=" + rot.toString(new DecimalFormat("#.##"));
        }
    }

}

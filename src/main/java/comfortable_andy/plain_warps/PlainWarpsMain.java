package comfortable_andy.plain_warps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import com.google.gson.reflect.TypeToken;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import comfortable_andy.plain_warps.argument.BonfireGroupArgumentType;
import comfortable_andy.plain_warps.argument.WarpsArgumentType;
import comfortable_andy.plain_warps.listener.BonfireListener;
import comfortable_andy.plain_warps.warp.PlainWarp;
import comfortable_andy.plain_warps.warp.Warp;
import comfortable_andy.plain_warps.warp.WarpProperty;
import comfortable_andy.plain_warps.warp.bonfire.BonfireMapRenderer;
import comfortable_andy.plain_warps.warp.bonfire.BonfireWarp;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.PaperCommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
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
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2f;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    private File mapIdFile;
    private Map<UUID, MapView> views;

    @Override
    public void onEnable() {
        INST = this;
        warpsFile = new File(getDataFolder(), "warps.json");
        mapIdFile = new File(getDataFolder(), "dont_touch_me");
        loadWarps();
        loadMapIds();
        saveResource(
                getDataFolder().toPath().relativize(warpsFile.toPath()).toString(),
                false
        );
        getLifecycleManager().registerEventHandler(
                LifecycleEvents.COMMANDS,
                this::registerCommands
        );
        new BukkitRunnable() {
            @Override
            public void run() {
                saveWarps();
                saveMapIds();
                for (Warp warp : warps) {
                    String perm = warp.getPerm();
                    if (getServer().getPluginManager().getPermission(perm) == null)
                        getServer().getPluginManager()
                                .addPermission(new Permission(perm, PermissionDefault.OP));
                }
            }
        }.runTaskTimer(this, 0, 20 * 15);
        getServer().getPluginManager().registerEvents(new BonfireListener(), this);
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
                    ItemStack stack = makeBonfireMap(player);
                    player.getInventory().addItem(stack);
                    return 1;
                });

        var unlockExecute = (Command<CommandSourceStack>) s -> {
            Player target = null;
            try {
                target = s.getArgument("target", PlayerSelectorArgumentResolver.class).resolve(s.getSource()).getFirst();
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
        final var editExecute = (Command<CommandSourceStack>) s -> {
            Warp warp = s.getArgument("warp", Warp.class);
            String name = s.getArgument("property", String.class);
            var property = getWarpProperty(warp, name);
            boolean hasValue = s.getNodes().stream()
                    .anyMatch(n -> n.getNode().getName().equals("value"));
            if (!hasValue) {
                s.getSource().getSender()
                        .sendMessage("Property '" + name + "' current set to: "
                                + property.getGetter().apply(warp));
                return 1;
            }
            StringReader reader = new StringReader(
                    s.getArgument("value", String.class)
            );
            var value = property.getCommandArgConverter()
                    .apply(s.getSource(), property.commandArgType().parse(reader));
            if (reader.canRead()) {
                throw new SimpleCommandExceptionType(
                        Component.literal("Too many arguments at: '" + reader.getRemaining() + "'")
                ).create();
            }
            property.getSetter().accept(warp, value);
            s.getSource().getSender()
                    .sendMessage("Property '" + name + "' now set to: " + value);
            saveWarps();
            return 1;
        };
        final var editRoot = Commands
                .literal("edit")
                .then(Commands
                        .argument("warp", new WarpsArgumentType(this))
                        .then(Commands
                                .argument("property", StringArgumentType.string())
                                .suggests((c, s) -> {
                                    c.getArgument("warp", Warp.class)
                                            .properties()
                                            .forEach(p -> s.suggest(
                                                    StringArgumentType
                                                            .escapeIfRequired(p.name())
                                            ));
                                    return s.buildFuture();
                                })
                                .executes(editExecute)
                                .then(Commands
                                        .argument(
                                                "value",
                                                StringArgumentType.greedyString()
                                        )
                                        .suggests((c, s) -> {
                                            Warp warp = c.getArgument("warp", Warp.class);
                                            String name = c.getArgument("property", String.class);
                                            var property = getWarpProperty(warp, name);
                                            return property.commandArgType().listSuggestions(c, s);
                                        })
                                        .executes(editExecute)
                                )
                        )
                );
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
                .then(Commands.literal("viewGroup").then(Commands.argument("viewGroup", new BonfireGroupArgumentType())
                        .executes(c -> {
                            if (!(c.getSource().getSender() instanceof Player player)) {
                                c.getSource().getSender().sendMessage("You need to be a player.");
                                return 0;
                            }
                            String group = c.getArgument("viewGroup", String.class);
                            if (group == null) {
                                player.getPersistentDataContainer().remove(BonfireMapRenderer.GROUP);
                            } else {
                                player.getPersistentDataContainer().set(BonfireMapRenderer.GROUP, PersistentDataType.STRING, group);
                            }
                            return 1;
                        })))
                .then(editRoot)
                .then(testSubCommand);
        commands.register(playerRoot.build());
        commands.register(adminRoot.build());
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::saveWarps, 20 * 5, 20 * 60);
    }

    private @NotNull ItemStack makeBonfireMap(Player player) {
        ItemStack stack = new ItemStack(Material.FILLED_MAP);
        stack.editMeta(MapMeta.class, m -> {
            MapView view = views.computeIfAbsent(
                    player.getWorld().getUID(),
                    u -> Bukkit.createMap(player.getWorld())
            );
            view.setScale(MapView.Scale.CLOSEST);
            view.getRenderers().forEach(view::removeRenderer);
            view.addRenderer(new BonfireMapRenderer());
            m.setMapView(view);
        });
        return stack;
    }

    private static @NotNull WarpProperty<?, ?, ?> getWarpProperty(Warp warp, String name) throws CommandSyntaxException {
        var property = warp.properties()
                .stream()
                .filter(p -> p.name().equals(name))
                .findFirst().orElse(null);
        if (property == null)
            throw new SimpleCommandExceptionType(Component.literal("'" + name + "' does not exist.")).create();
        return property;
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

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void ensureMapIdFileExists() {
        if (!mapIdFile.exists()) {
            try {
                mapIdFile.getParentFile().mkdirs();
                if (!mapIdFile.createNewFile())
                    throw new IllegalStateException("could not create map id file");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void loadMapIds() {
        ensureMapIdFileExists();
        this.views = new ConcurrentHashMap<>();
        try (Scanner scanner = new Scanner(mapIdFile)) {
            while (scanner.hasNext()) {
                String rawId = scanner.nextLine();
                UUID id;
                try {
                    id = UUID.fromString(rawId);
                } catch (Exception e) {
                    getLogger().warning("Malformed world id '" + rawId + "', ignoring.");
                    continue;
                }
                World world = Bukkit.getWorld(id);
                if (!scanner.hasNext()) {
                    getLogger().warning("World '" + (world == null ? id : world.getName()) + "' has missing map id entry.");
                    break;
                }
                int mapId = Integer.parseInt(scanner.nextLine());
                MapView view = Bukkit.getMap(mapId);
                if (view == null) {
                    getLogger().warning("World '" + (world == null ? id : world.getName()) + "' has a map id that couldn't be found.");
                    continue;
                }
                view.getRenderers().forEach(view::removeRenderer);
                view.addRenderer(new BonfireMapRenderer());
                views.put(id, view);
            }
            getLogger().info("Loaded map ids for " + views.size() + " worlds.");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveMapIds() {
        ensureMapIdFileExists();
        try (PrintWriter writer = new PrintWriter(mapIdFile)) {
            for (Map.Entry<UUID, MapView> entry : views.entrySet()) {
                writer.println(entry.getKey().toString());
                writer.println(entry.getValue().getId());
            }
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
                if (!((!warpsFile.getParentFile().exists() && warpsFile.getParentFile().mkdirs()) || warpsFile.createNewFile())) {
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

package comfortable_andy.plain_warps.argument;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import comfortable_andy.plain_warps.PlainWarpsMain;
import comfortable_andy.plain_warps.warp.Warp;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.chat.Component;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
@RequiredArgsConstructor
public class WarpsArgumentType implements CustomArgumentType.Converted<Warp, String> {

    private final PlainWarpsMain main;
    private static final DynamicCommandExceptionType WARP_NOT_FOUND = new DynamicCommandExceptionType(t -> Component.literal("Could not find warp '" + t + "'!"));

    @Override
    public @NotNull Warp convert(@NotNull String nativeType) throws CommandSyntaxException {
        Warp warp = main.getWarp(nativeType);
        if (warp == null) throw WARP_NOT_FOUND.create(nativeType);
        return warp;
    }

    @Override
    public @NotNull ArgumentType<String> getNativeType() {
        return StringArgumentType.string();
    }

    @Override
    public @NotNull <S> CompletableFuture<Suggestions> listSuggestions(@NotNull CommandContext<S> context, @NotNull SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof CommandSourceStack s)) return builder.buildFuture();
        final boolean canSeeAll = s.getSender().hasPermission("plain_warps.warp.*");
        for (Warp warp : main.getWarps()) {
            if (!canSeeAll && warp.isLockedFor(s.getSender()))
                continue;
            final Location loc = warp.getLocation();
            builder.suggest(StringArgumentType.escapeIfRequired(warp.id()), Component.literal("(")
                    .append(loc.getWorld().getName())
                    .append(") ")
                    .append("" + loc.getX())
                    .append(" ")
                    .append("" + loc.getY())
                    .append(" ")
                    .append("" + loc.getZ())
            );
        }
        return builder.buildFuture();
    }
}

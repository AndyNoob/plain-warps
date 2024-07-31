package comfortable_andy.plain_warps.argument;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import comfortable_andy.plain_warps.PlainWarpsMain;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.chat.Component;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
@RequiredArgsConstructor
public class WarpsArgumentType implements CustomArgumentType.Converted<PlainWarpsMain.Warp, String> {

    private final PlainWarpsMain main;

    @Override
    public @NotNull PlainWarpsMain.Warp convert(@NotNull String nativeType) {
        return main.getWarp(nativeType);
    }

    @Override
    public @NotNull ArgumentType<String> getNativeType() {
        return StringArgumentType.string();
    }

    @Override
    public @NotNull <S> CompletableFuture<Suggestions> listSuggestions(@NotNull CommandContext<S> context, @NotNull SuggestionsBuilder builder) {
        final boolean canSeeAll = context.getSource() instanceof CommandSourceStack s && s.getSender().hasPermission("plain_warps.warps.*");
        for (PlainWarpsMain.Warp warp : main.getWarps()) {
            if (!canSeeAll
                    && context.getSource() instanceof CommandSourceStack s
                    && !s.getSender().hasPermission(warp.getPerm()))
                continue;
            final Location loc = warp.getLocation();
            builder.suggest(warp.id(), Component.literal("(")
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

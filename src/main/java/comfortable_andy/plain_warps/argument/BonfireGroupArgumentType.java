package comfortable_andy.plain_warps.argument;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import comfortable_andy.plain_warps.PlainWarpsMain;
import comfortable_andy.plain_warps.warp.bonfire.BonfireWarp;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public class BonfireGroupArgumentType implements CustomArgumentType.Converted<String, String> {
    @Override
    public @NotNull String convert(@NotNull String nativeType) {
        return nativeType;
    }

    @Override
    public @NotNull ArgumentType<String> getNativeType() {
        return StringArgumentType.string();
    }

    @Override
    public <S> @NotNull CompletableFuture<Suggestions> listSuggestions(@NotNull CommandContext<S> context, @NotNull SuggestionsBuilder builder) {
        PlainWarpsMain.getInstance().getWarps().stream()
                .filter(s -> s instanceof BonfireWarp warp && warp.group != null && warp.group.startsWith(builder.getRemainingLowerCase()))
                .map(s -> ((BonfireWarp) s).group)
                .forEach(builder::suggest);
        return builder.buildFuture();
    }
}

package comfortable_andy.plain_warps.warp;

import com.mojang.brigadier.arguments.ArgumentType;
import comfortable_andy.plain_warps.util.ThrowingBiFunction;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.function.BiConsumer;
import java.util.function.Function;

@SuppressWarnings("UnstableApiUsage")
public record WarpProperty<W extends Warp, Type, CommandType>(
        String name,
        BiConsumer<W, Type> setter,
        Function<W, Type> getter,
        ArgumentType<CommandType> commandArgType,
        ThrowingBiFunction<CommandSourceStack, CommandType, Type> commandArgConverter
) {

    @SuppressWarnings("unchecked")
    public <War, T> Function<War, T> getGetter() {
        return (Function<War, T>) getter;
    }

    @SuppressWarnings("unchecked")
    public <War, T> BiConsumer<War, T> getSetter() {
        return (BiConsumer<War, T>) setter;
    }

    @SuppressWarnings("unchecked")
    public <C, T> ThrowingBiFunction<CommandSourceStack, C, T> getCommandArgConverter() {
        return (ThrowingBiFunction<CommandSourceStack, C, T>) commandArgConverter;
    }
}

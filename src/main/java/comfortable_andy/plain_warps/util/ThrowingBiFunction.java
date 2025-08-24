package comfortable_andy.plain_warps.util;


import com.mojang.brigadier.exceptions.CommandSyntaxException;

@FunctionalInterface
public interface ThrowingBiFunction<T, U, R> {
    R apply(T t, U u) throws CommandSyntaxException;
}

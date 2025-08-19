package comfortable_andy.plain_warps.warp;

import comfortable_andy.plain_warps.PlainWarpsMain;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.ListPersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface Warp {

    NamespacedKey UNLOCK_KEY = new NamespacedKey(PlainWarpsMain.getInstance(), "unlocked_warps");

    String id();

    String getPerm();

    Location getLocation();

    default boolean isLockedFor(CommandSender sender) {
        return !sender.hasPermission("plain_warps.warp.*")
                && !sender.hasPermission(getPerm())
                && (!(sender instanceof Player player) || !player.getPersistentDataContainer().getOrDefault(
                        UNLOCK_KEY,
                        ListPersistentDataType.LIST.strings(),
                        Collections.emptyList()
                ).contains(id()));
    }

    /**
     * @return true if now locked, false otherwise
     */
    default boolean togglePersistentLockState(Player player) {
        List<String> list = new ArrayList<>(player.getPersistentDataContainer().getOrDefault(
                UNLOCK_KEY,
                ListPersistentDataType.LIST.strings(),
                Collections.emptyList()
        ));
        if (list.contains(id())) {
            list.remove(id());
        } else list.add(id());
        player.getPersistentDataContainer().set(
                UNLOCK_KEY,
                ListPersistentDataType.LIST.strings(),
                list
        );
        return !list.contains(id());
    }

}

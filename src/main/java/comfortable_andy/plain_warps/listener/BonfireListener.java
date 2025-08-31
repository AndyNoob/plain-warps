package comfortable_andy.plain_warps.listener;

import comfortable_andy.plain_warps.PlainWarpsMain;
import comfortable_andy.plain_warps.warp.bonfire.BonfireWarp;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class BonfireListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrackEntity(PlayerTrackEntityEvent event) {
        if (!(event.getEntity() instanceof BlockDisplay display)) return;
        BonfireWarp warp = BonfireWarp.findWarp(display, b -> b.campfireId);
        if (warp == null) return;
        Player player = event.getPlayer();
        PlainWarpsMain.runLater(() -> warp.showLockState(player, warp.isLockedFor(player)), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;
        BonfireWarp warp = BonfireWarp.findWarp(interaction, b -> b.interactionId);
        if (warp == null) return;
        Player player = event.getPlayer();
        if (warp.isLockedFor(player)) {
            warp.playAndShowUnlock(player);
            player.sendMessage("You have unlocked '" + warp.id() + "'!");
        }
    }


}

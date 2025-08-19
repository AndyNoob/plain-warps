package comfortable_andy.plain_warps.listener;

import comfortable_andy.plain_warps.PlainWarpsMain;
import comfortable_andy.plain_warps.warp.BonfireWarp;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class BonfireLoadListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrackEntity(PlayerTrackEntityEvent event) {
        event.getPlayer().sendMessage("tracking " + event.getEntity());
        if (!(event.getEntity() instanceof BlockDisplay display)) return;
        BonfireWarp warp = BonfireWarp.findWarp(display);
        if (warp == null) return;
        Player player = event.getPlayer();
        PlainWarpsMain.runLater(() -> warp.showLockState(player, warp.isLockedFor(player)), 1);
    }

}

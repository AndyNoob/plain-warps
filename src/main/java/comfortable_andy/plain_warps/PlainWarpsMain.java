package comfortable_andy.plain_warps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BlockVector;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PlainWarpsMain extends JavaPlugin {

    private static final Gson GSON = new GsonBuilder().setLenient().disableHtmlEscaping().create();
    private static final TypeToken<Set<Warp>> TOKEN = new TypeToken<>() {
    };
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
        new BukkitRunnable() {
            @Override
            public void run() {
                saveWarps();
            }
        }.runTaskTimer(this, 0, 20 * 15);
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

    public record Warp(String id, UUID worldId, BlockVector pos) {

        public Warp(String id, Location location) {
            this(id, location.getWorld().getUID(), location.toVector().toBlockVector());
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

    }

}

package dev.woter.serverstats;

import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class StatsStorage {

    private final File file;
    public final Set<UUID> seenPlayers = new HashSet<>();

    public StatsStorage(JavaPlugin plugin) {
        File folder = plugin.getDataFolder();
        if (!folder.exists()) folder.mkdirs();
        this.file = new File(folder, "data.json");
    }

    public void load() {
        try {
            if (!file.exists()) return;

            JSONObject json = new JSONObject(Files.readString(file.toPath()));

            ServerStatsPlugin.totalJoins =
                json.optInt("total_joins", 0);
            ServerStatsPlugin.totalDeaths =
                json.optInt("total_deaths", 0);
            ServerStatsPlugin.totalRuntimeMs =
                json.optLong("total_runtime_ms", 0);
            ServerStatsPlugin.totalUniqueJoins =
                json.optLong("total_unique_joins", 0L);

            JSONArray arr = json.optJSONArray("seen_players");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    seenPlayers.add(UUID.fromString(arr.getString(i)));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void save() {
        try {
            JSONObject json = new JSONObject()
                .put("total_joins", ServerStatsPlugin.totalJoins)
                .put("total_deaths", ServerStatsPlugin.totalDeaths)
                .put("total_runtime_ms", ServerStatsPlugin.totalRuntimeMs)
                .put("total_unique_joins", ServerStatsPlugin.totalUniqueJoins);

            JSONArray arr = new JSONArray();
            for (UUID u : seenPlayers) arr.put(u.toString());
            json.put("seen_players", arr);

            try (FileWriter writer = new FileWriter(file, false)) {
                writer.write(json.toString(2));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

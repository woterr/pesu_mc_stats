package dev.woter.serverstats;

import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

public class StatsStorage {

    private final File file;

    public StatsStorage(JavaPlugin plugin) {
        File folder = plugin.getDataFolder();
        if (!folder.exists()) {
            folder.mkdirs();
        }
        this.file = new File(folder, "data.json");
    }

    public void load() {
        try {
            if (!file.exists()) {
                return;
            }

            String content = Files.readString(file.toPath());
            JSONObject json = new JSONObject(content);

            ServerStatsPlugin.totalJoins =
                    json.optInt("total_joins", 0);
            ServerStatsPlugin.totalDeaths =
                    json.optInt("total_deaths", 0);
            ServerStatsPlugin.totalRuntimeMs =
                    json.optLong("total_runtime_ms", 0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void save() {
        try {
            JSONObject json = new JSONObject();
            json.put("total_joins", ServerStatsPlugin.totalJoins);
            json.put("total_deaths", ServerStatsPlugin.totalDeaths);
            json.put("total_runtime_ms", ServerStatsPlugin.totalRuntimeMs);

            try (FileWriter writer = new FileWriter(file, false)) {
                writer.write(json.toString(2));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

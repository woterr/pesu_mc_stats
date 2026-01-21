package dev.woter.serverstats;

import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.*;

public class PlayerStatsStorage {

    private final File playersDir;

    public PlayerStatsStorage(JavaPlugin plugin) {
        File dataFolder = plugin.getDataFolder();
        playersDir = new File(dataFolder, "players");

        if (!playersDir.exists()) {
            playersDir.mkdirs();
        }
    }

    public PlayerStats load(UUID uuid) {
        try {
            File file = new File(playersDir, uuid + ".json");
            PlayerStats stats = new PlayerStats(uuid);

            if (!file.exists()) {
                return stats;
            }

            String content = Files.readString(file.toPath());
            JSONObject json = new JSONObject(content);

            stats.totalJoins = json.optInt("total_joins", 0);
            stats.totalDeaths = json.optInt("total_deaths", 0);

            stats.playerKills = json.optInt("player_kills", 0);
            stats.mobKills = json.optInt("mob_kills", 0);
            stats.messagesSent = json.optInt("messages_sent", 0);

            stats.totalPlaytimeMs = json.optLong("total_playtime_ms", 0);
            stats.firstJoinTs = json.optLong("first_join_ts", 0);
            stats.lastJoinTs = json.optLong("last_join_ts", 0);
            stats.lastSeenTs = json.optLong("last_seen_ts", 0);
            stats.lastKnownName = json.optString("last_known_name", "");

            JSONArray adv = json.optJSONArray("advancements");
            if (adv != null) {
                for (int i = 0; i < adv.length(); i++) {
                    stats.advancements.add(adv.getString(i));
                }
            }

            return stats;

        } catch (Exception e) {
            e.printStackTrace();
            return new PlayerStats(uuid);
        }
    }

    public void save(PlayerStats stats) {
        try {
            File file = new File(playersDir, stats.uuid + ".json");

            JSONObject json = new JSONObject();
            json.put("uuid", stats.uuid.toString());
            json.put("last_known_name", stats.lastKnownName);

            json.put("total_joins", stats.totalJoins);
            json.put("total_deaths", stats.totalDeaths);

            json.put("player_kills", stats.playerKills);
            json.put("mob_kills", stats.mobKills);
            json.put("messages_sent", stats.messagesSent);

            json.put("total_playtime_ms", stats.totalPlaytimeMs);
            json.put("first_join_ts", stats.firstJoinTs);
            json.put("last_join_ts", stats.lastJoinTs);
            json.put("last_seen_ts", stats.lastSeenTs);

            json.put("advancements", stats.advancements);

            try (FileWriter writer = new FileWriter(file, false)) {
                writer.write(json.toString(2));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<PlayerStats> getAll() {
        List<PlayerStats> list = new ArrayList<>();

        File[] files = playersDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return list;

        for (File file : files) {
            try {
                String content = Files.readString(file.toPath());
                JSONObject json = new JSONObject(content);
                UUID uuid = UUID.fromString(json.getString("uuid"));
                list.add(load(uuid));
            } catch (Exception ignored) {}
        }

        return list;
    }
}

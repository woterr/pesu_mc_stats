package dev.woter.serverstats.duels;

import org.bukkit.Bukkit;
import org.bson.Document;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;

public final class DuelStatsAggregator {

    private DuelStatsAggregator() {}

    public static Document buildSnapshot(String uuid, String name) {
        try {
            File dir = new File(Bukkit.getPluginsFolder(), "Duels/users");
            File file = new File(dir, uuid + ".json");
            if (!file.exists()) return null;

            JSONObject root = new JSONObject(Files.readString(file.toPath()));

            int wins = root.optInt("wins", 0);
            int losses = root.optInt("losses", 0);

            Document doc = new Document()
                .append("uuid", uuid)
                .append("name", name)
                .append("wins", wins)
                .append("losses", losses)
                .append("total_matches", wins + losses)
                .append(
                    "rating",
                    root.optJSONObject("rating") != null
                        ? Document.parse(root.getJSONObject("rating").toString())
                        : new Document()
                );

            return doc;

        } catch (Exception e) {
            return null;
        }
    }
}

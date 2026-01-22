package dev.woter.serverstats.duels;

import org.bukkit.Bukkit;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public final class DuelStatsAggregator {

    private DuelStatsAggregator() {}

    public static Document buildSnapshot(String uuid, String name) {
        try {
            File dir = new File(Bukkit.getPluginsFolder(), "Duels/users");
            File file = new File(dir, uuid + ".json");
            if (!file.exists()) return null;

            JSONObject root = new JSONObject(Files.readString(file.toPath()));

            Document doc = new Document()
                .append("uuid", uuid)
                .append("name", name)
                .append("wins", root.optInt("wins", 0))
                .append("losses", root.optInt("losses", 0))
                .append(
                    "rating",
                    root.optJSONObject("rating") != null
                        ? Document.parse(root.getJSONObject("rating").toString())
                        : new Document()
                );

            JSONArray matches = root.optJSONArray("matches");
            if (matches == null) {
                doc.append("total_matches", 0);
                return doc;
            }

            Map<String, KitAgg> kits = new HashMap<>();
            long lastMatchTs = 0;

            for (int i = 0; i < matches.length(); i++) {
                JSONObject m = matches.getJSONObject(i);

                String kit = m.optString("kit", "Unknown");
                String winner = m.optString("winner");
                String loser = m.optString("loser");

                long duration = m.optLong("duration", 0);
                long time = m.optLong("time", 0);

                lastMatchTs = Math.max(lastMatchTs, time);

                KitAgg agg = kits.computeIfAbsent(kit, k -> new KitAgg());
                agg.played++;
                agg.totalDuration += duration;

                if (name.equalsIgnoreCase(winner)) {
                    agg.wins++;
                } else if (name.equalsIgnoreCase(loser)) {
                    agg.losses++;
                }
            }

            Document kitDoc = new Document();
            for (Map.Entry<String, KitAgg> e : kits.entrySet()) {
                KitAgg a = e.getValue();
                kitDoc.append(
                    e.getKey(),
                    new Document()
                        .append("played", a.played)
                        .append("wins", a.wins)
                        .append("losses", a.losses)
                        .append(
                            "avg_duration_ms",
                            a.played == 0 ? 0 : a.totalDuration / a.played
                        )
                );
            }

            doc.append("kits", kitDoc)
               .append("total_matches", matches.length())
               .append("last_match_ts", lastMatchTs);

            return doc;

        } catch (Exception e) {
            return null;
        }
    }

    private static final class KitAgg {
        int played = 0;
        int wins = 0;
        int losses = 0;
        long totalDuration = 0;
    }
}

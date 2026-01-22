package dev.woter.serverstats;

import dev.woter.serverstats.duels.DuelStatsReader;
import dev.woter.serverstats.mongo.MongoService;
import fi.iki.elonen.NanoHTTPD;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bson.Document;
import org.json.JSONObject;

import java.util.Date;
import java.util.UUID;

public class StatsHttpServer extends NanoHTTPD {

    public StatsHttpServer() {
        super(ServerStatsPlugin.HTTP_HOST, ServerStatsPlugin.HTTP_PORT);
    }

    private String hms(long ms) {
        long s = ms / 1000;
        return String.format(
            "%02dh %02dm %02ds",
            s / 3600,
            (s % 3600) / 60,
            s % 60
        );
    }

    @Override
    public Response serve(IHTTPSession session) {

        if (!ServerStatsPlugin.HTTP_TOKEN
                .equals(session.getHeaders().get("x-stats-token"))) {
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                "text/plain",
                "Unauthorized"
            );
        }

        if (!"/mc/stats".equals(session.getUri())) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/plain",
                "Not Found"
            );
        }

        MongoService mongo = ServerStatsPlugin.mongoService;

        String uuidParam = session.getParms().get("player");
        if (uuidParam != null) {

            PlayerStats stats;
            try {
                stats = ServerStatsPlugin.playerStorage
                        .load(UUID.fromString(uuidParam));
            } catch (Exception e) {
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "text/plain",
                    "Invalid UUID"
                );
            }

            boolean online = Bukkit.getPlayer(stats.uuid) != null;
            long now = System.currentTimeMillis();

            JSONObject json = new JSONObject()
                .put("uuid", stats.uuid.toString())
                .put("name", stats.lastKnownName)
                .put("online", online)
                .put("total_playtime_ms", stats.totalPlaytimeMs)
                .put("total_joins", stats.totalJoins)
                .put("total_deaths", stats.totalDeaths);

            JSONObject duelRaw = DuelStatsReader.read(stats.uuid.toString());
            Document duelSummaryDoc = null;

            if (duelRaw != null) {
                int wins = duelRaw.optInt("wins", 0);
                int losses = duelRaw.optInt("losses", 0);

                JSONObject rating = duelRaw.optJSONObject("rating");

                long lastMatchTs = 0;
                if (duelRaw.has("matches")) {
                    for (Object o : duelRaw.getJSONArray("matches")) {
                        JSONObject m = (JSONObject) o;
                        lastMatchTs = Math.max(
                            lastMatchTs,
                            m.optLong("time", 0)
                        );
                    }
                }

                json.put("duel", new JSONObject()
                    .put("wins", wins)
                    .put("losses", losses)
                    .put("rating", rating)
                    .put("total_matches", wins + losses)
                    .put("last_match_ts", lastMatchTs)
                );

                duelSummaryDoc = new Document()
                    .append("wins", wins)
                    .append("losses", losses)
                    .append("rating", rating != null ? rating.toMap() : null)
                    .append("total_matches", wins + losses)
                    .append("last_match_ts", lastMatchTs);
            }

            if (mongo != null) {
                Document snap = new Document()
                    .append("uuid", stats.uuid.toString())
                    .append("name", stats.lastKnownName)
                    .append("online", online)
                    .append("last_seen_ts", now)
                    .append("total_playtime_ms", stats.totalPlaytimeMs)
                    .append("total_joins", stats.totalJoins)
                    .append("total_deaths", stats.totalDeaths);

                if (duelSummaryDoc != null) {
                    snap.append("duel", duelSummaryDoc);
                }

                mongo.upsertPlayerSnapshot(snap);
            }

            if (mongo != null) {
                Document duel =
                    mongo.getDuelsCollection()
                        .find(new Document("uuid", stats.uuid.toString()))
                        .first();

                if (duel != null) {
                    json.put("duel", new JSONObject()
                        .put("wins", duel.getInteger("wins", 0))
                        .put("losses", duel.getInteger("losses", 0))
                        .put("total_matches", duel.getInteger("total_matches", 0))
                        .put("last_match_ts", duel.getLong("last_match_ts"))
                        .put("rating", duel.get("rating"))
                    );
                }
            }


            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                json.toString(2)
            );
        }

        long now = System.currentTimeMillis();

        int chunks = 0;
        for (World w : Bukkit.getWorlds()) {
            chunks += w.getLoadedChunks().length;
        }

        long uptimeMs = now - ServerStatsPlugin.serverStartTime;
        long totalRuntimeMs =
            ServerStatsPlugin.totalRuntimeMs + uptimeMs;

        JSONObject json = new JSONObject()
            .put("timestamp", now)
            .put("player_count", Bukkit.getOnlinePlayers().size())
            .put("loaded_chunks", chunks)
            .put("total_joins", ServerStatsPlugin.totalJoins)
            .put("total_unique_joins", ServerStatsPlugin.totalUniqueJoins)
            .put("total_deaths", ServerStatsPlugin.totalDeaths)
            .put("uptime_ms", uptimeMs)
            .put("total_runtime_ms", totalRuntimeMs)
            .put("runtime_hms", hms(totalRuntimeMs));

        if (mongo != null) {
            mongo.insertServerMetrics(
                new Document()
                    .append("timestamp", new Date(now))
                    .append("player_count", Bukkit.getOnlinePlayers().size())
                    .append("loaded_chunks", chunks)
                    .append("total_joins", ServerStatsPlugin.totalJoins)
                    .append("total_unique_joins",
                            ServerStatsPlugin.totalUniqueJoins)
                    .append("total_deaths", ServerStatsPlugin.totalDeaths)
                    .append("uptime_ms", uptimeMs)
                    .append("total_runtime_ms", totalRuntimeMs)
            );
        }

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            json.toString(2)
        );
    }
}

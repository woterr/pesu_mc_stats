package dev.woter.serverstats;

import fi.iki.elonen.NanoHTTPD;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.json.JSONObject;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.util.UUID;

public class StatsHttpServer extends NanoHTTPD {

    public StatsHttpServer() {
        super(
            ServerStatsPlugin.HTTP_HOST,
            ServerStatsPlugin.HTTP_PORT
        );
    }

    private String formatDuration(long ms) {
        long s = ms / 1000;
        return String.format("%02dh %02dm %02ds",
                s / 3600, (s % 3600) / 60, s % 60);
    }

    private Response playerStatsResponse(PlayerStats stats) {
        JSONObject pj = new JSONObject();

        pj.put("uuid", stats.uuid.toString());
        pj.put("name", stats.lastKnownName);
        pj.put("online", Bukkit.getPlayer(stats.uuid) != null);

        pj.put("total_joins", stats.totalJoins);
        pj.put("total_deaths", stats.totalDeaths);
        pj.put("player_kills", stats.playerKills);
        pj.put("mob_kills", stats.mobKills);
        pj.put("messages_sent", stats.messagesSent);

        pj.put("total_playtime_ms", stats.totalPlaytimeMs);
        pj.put("first_join_ts", stats.firstJoinTs);
        pj.put("last_join_ts", stats.lastJoinTs);
        pj.put("last_seen_ts", stats.lastSeenTs);

        pj.put("advancement_count", stats.advancements.size());
        pj.put("advancements", stats.advancements);

        return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                pj.toString(2)
        );
    }

    @Override
    public Response serve(IHTTPSession session) {

        String token = session.getHeaders().get("x-stats-token");

        System.out.println("HEADERS = " + session.getHeaders());

        if (token == null ||
            !token.trim().equals(ServerStatsPlugin.HTTP_TOKEN)) {

            return newFixedLengthResponse(
                    Response.Status.UNAUTHORIZED,
                    "text/plain",
                    "Unauthorized"
            );
        }


        if (!"/mc/stats".equals(session.getUri())) {
            return newFixedLengthResponse(
                    Response.Status.NOT_FOUND, "text/plain", "Not Found");
        }

        String name = session.getParms().get("name");
        if (name != null) {
            for (PlayerStats stats :
                    ServerStatsPlugin.playerStorage.getAll()) {
                if (stats.lastKnownName.equalsIgnoreCase(name)) {
                    return playerStatsResponse(stats);
                }
            }
            return newFixedLengthResponse(
                    Response.Status.NOT_FOUND, "text/plain", "Player not found");
        }

        String uuidParam = session.getParms().get("player");
        if (uuidParam != null) {
            try {
                return playerStatsResponse(
                        ServerStatsPlugin.playerStorage
                                .load(UUID.fromString(uuidParam)));
            } catch (Exception e) {
                return newFixedLengthResponse(
                        Response.Status.BAD_REQUEST, "text/plain", "Invalid UUID");
            }
        }

        long now = System.currentTimeMillis();
        JSONObject json = new JSONObject();

        json.put("timestamp", now);
        json.put("player_count", Bukkit.getOnlinePlayers().size());
        json.put("uptime_ms", now - ServerStatsPlugin.serverStartTime);

        Runtime rt = Runtime.getRuntime();
        json.put("ram_used_mb",
                (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        json.put("ram_max_mb", rt.maxMemory() / (1024 * 1024));

        json.put("threads", Thread.activeCount());

        try {
            OperatingSystemMXBean os =
                    (OperatingSystemMXBean)
                            ManagementFactory.getOperatingSystemMXBean();
            json.put("cpu_load", os.getProcessCpuLoad());
        } catch (Exception e) {
            json.put("cpu_load", -1);
        }

        int chunks = 0;
        for (World w : Bukkit.getWorlds()) {
            chunks += w.getLoadedChunks().length;
        }
        json.put("loaded_chunks", chunks);

        json.put("total_joins", ServerStatsPlugin.totalJoins);
        json.put("total_deaths", ServerStatsPlugin.totalDeaths);

        long totalRuntime =
                ServerStatsPlugin.totalRuntimeMs +
                (now - ServerStatsPlugin.serverStartTime);

        json.put("total_runtime_ms", totalRuntime);
        json.put("total_runtime_hms", formatDuration(totalRuntime));

        return newFixedLengthResponse(
                Response.Status.OK, "application/json", json.toString(2));
    }
}

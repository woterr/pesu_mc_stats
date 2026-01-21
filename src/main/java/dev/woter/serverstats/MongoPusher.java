package dev.woter.serverstats.mongo;

import dev.woter.serverstats.ServerStatsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bson.Document;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.util.Date;

public class MongoPusher implements Runnable {

    private final MongoService mongo;

    public MongoPusher(MongoService mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run() {
        try {
            pushServerMetrics();
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Mongo] Server push failed: " + e.getMessage());
        }
    }

    private void pushServerMetrics() {
        long now = System.currentTimeMillis();

        Runtime rt = Runtime.getRuntime();
        OperatingSystemMXBean os =
                (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        int chunks = 0;
        for (World w : Bukkit.getWorlds()) {
            chunks += w.getLoadedChunks().length;
        }

        long uptimeMs = now - ServerStatsPlugin.serverStartTime;
        long totalRuntimeMs =
                ServerStatsPlugin.totalRuntimeMs + uptimeMs;

        Document doc = new Document()
            .append("timestamp", new Date(now))
            .append("player_count", Bukkit.getOnlinePlayers().size())
            .append("cpu_load", os.getProcessCpuLoad())
            .append("ram_used_mb",
                    (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024))
            .append("ram_max_mb", rt.maxMemory() / (1024 * 1024))
            .append("threads", Thread.activeCount())
            .append("loaded_chunks", chunks)
            .append("total_joins", ServerStatsPlugin.totalJoins)
            .append("total_deaths", ServerStatsPlugin.totalDeaths)
            .append("uptime_ms", uptimeMs)
            .append("total_runtime_ms", totalRuntimeMs);

        mongo.insertServerMetrics(doc);
    }
}

package dev.woter.serverstats.mongo;

import dev.woter.serverstats.ServerStatsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bson.Document;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;
import java.io.File;
import java.nio.file.Files;

import org.json.JSONObject;

import dev.woter.serverstats.duels.DuelStatsReader;
import dev.woter.serverstats.duels.DuelStatsAggregator;

import java.util.Date;

public class MongoPusher implements Runnable {

    private final MongoService mongo;

    private static final SystemInfo SI = new SystemInfo();
    private static final OperatingSystem OS = SI.getOperatingSystem();
    private static final CentralProcessor CPU = SI.getHardware().getProcessor();
    private static final GlobalMemory MEM = SI.getHardware().getMemory();

    private long[] prevCpuTicks = CPU.getSystemCpuLoadTicks();
    private OSProcess prevProc = null;

    public MongoPusher(MongoService mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run() {
        try {
            pushServerMetrics();
            pushAllDuelSnapshots();
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Mongo] Mongo push failed");
            e.printStackTrace();
        }
    }

    private void pushServerMetrics() {
        long now = System.currentTimeMillis();

        double cpuSystem =
            CPU.getSystemCpuLoadBetweenTicks(prevCpuTicks) * 100.0;
        prevCpuTicks = CPU.getSystemCpuLoadTicks();

        int pid = (int) ProcessHandle.current().pid();
        OSProcess proc = OS.getProcess(pid);

        double cpuJvm = 0.0;
        if (proc != null && prevProc != null) {
            cpuJvm = proc.getProcessCpuLoadBetweenTicks(prevProc) * 100.0;
        }
        prevProc = proc;

        long ramSystemTotal = MEM.getTotal();
        long ramSystemUsed  = ramSystemTotal - MEM.getAvailable();

        long jvmRss = proc != null ? proc.getResidentSetSize() : 0;

        Runtime rt = Runtime.getRuntime();
        long heapMax  = rt.maxMemory();
        long heapUsed = rt.totalMemory() - rt.freeMemory();

        int chunks = 0;
        for (World w : Bukkit.getWorlds()) {
            chunks += w.getLoadedChunks().length;
        }

        long uptimeMs = now - ServerStatsPlugin.serverStartTime;
        long totalRuntimeMs =
            ServerStatsPlugin.totalRuntimeMs + uptimeMs;

        Document doc = new Document()
            .append("timestamp", new Date(now))
            .append("cpu_system_pct", cpuSystem)
            .append("cpu_jvm_pct", cpuJvm)
            .append("ram_system_total", ramSystemTotal)
            .append("ram_system_used", ramSystemUsed)
            .append("jvm_rss_used", jvmRss)
            .append("jvm_heap_max", heapMax)
            .append("jvm_heap_used", heapUsed)
            .append("player_count", Bukkit.getOnlinePlayers().size())
            .append("loaded_chunks", chunks)
            .append("total_joins", ServerStatsPlugin.totalJoins)
            .append("total_unique_joins", ServerStatsPlugin.totalUniqueJoins)
            .append("total_deaths", ServerStatsPlugin.totalDeaths)
            .append("uptime_ms", uptimeMs)
            .append("total_runtime_ms", totalRuntimeMs);

        mongo.insertServerMetrics(doc);
    }

    private void pushAllDuelSnapshots() {
        File dir = new File(
            Bukkit.getPluginsFolder(),
            "Duels/users"
        );

        if (!dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File file : files) {
            try {
                String uuid = file.getName().replace(".json", "");
                JSONObject raw = DuelStatsReader.read(uuid);
                if (raw == null) continue;

                String name = raw.optString("name", null);
                if (name == null || name.isBlank()) continue;

                Document snap =
                    DuelStatsAggregator.buildSnapshot(uuid, name);

                if (snap != null) {
                    mongo.upsertDuelSnapshot(snap);
                }
            } catch (Exception ignored) {
                // one bad file should not break the loop
            }
        }
    }

}

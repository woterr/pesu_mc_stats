package dev.woter.serverstats;

import dev.woter.serverstats.mongo.MongoService;
import dev.woter.serverstats.mongo.MongoPusher;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bson.Document;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ServerStatsPlugin extends JavaPlugin {

    // ===== GLOBAL STATE =====
    public static long serverStartTime;
    public static long totalRuntimeMs = 0;

    public static int totalJoins = 0;
    public static int totalDeaths = 0;
    public static long totalUniqueJoins = 0;

    public static StatsStorage storage;
    public static PlayerStatsStorage playerStorage;

    public static final Map<UUID, PlayerStats> onlinePlayers = new HashMap<>();

    // ===== HTTP =====
    public static boolean HTTP_ENABLED;
    public static String HTTP_HOST;
    public static int HTTP_PORT;
    public static String HTTP_TOKEN;

    // ===== MONGO =====
    public static MongoService mongoService;

    private StatsHttpServer httpServer;

    @Override
    public void onEnable() {
        serverStartTime = System.currentTimeMillis();

        // ---- storage ----
        storage = new StatsStorage(this);
        storage.load();

        playerStorage = new PlayerStatsStorage(this);

        saveDefaultConfig();

        // ---- mongo ----
        if (getConfig().getBoolean("mongo.enabled", false)) {
            mongoService = new MongoService(
                getConfig().getString("mongo.uri"),
                getConfig().getString("mongo.database")
            );

            // hard reset online state on boot (crash-safe)
            mongoService.markAllPlayersOffline(System.currentTimeMillis());

            int interval = getConfig().getInt(
                "stats.mongo_push_interval_seconds", 30
            );

            Bukkit.getScheduler().runTaskTimerAsynchronously(
                this,
                new MongoPusher(mongoService),
                interval * 20L,
                interval * 20L
            );
        }

        // ---- HTTP ----
        HTTP_ENABLED = getConfig().getBoolean("http.enabled", true);
        HTTP_HOST = getConfig().getString("http.host", "127.0.0.1");
        HTTP_PORT = getConfig().getInt("http.port", 6767);
        HTTP_TOKEN = getConfig().getString("http.token", "").trim();

        if (HTTP_ENABLED) {
            httpServer = new StatsHttpServer();
            try {
                httpServer.start();
            } catch (Exception e) {
                getLogger().severe("Failed to start HTTP server");
                e.printStackTrace();
            }
        }

        // ---- online players at enable (reload-safe) ----
        for (Player p : Bukkit.getOnlinePlayers()) {
            long now = System.currentTimeMillis();

            PlayerStats stats = playerStorage.load(p.getUniqueId());
            stats.sessionStartTime = now;
            stats.lastKnownName = p.getName();
            stats.lastSeenTs = now;

            onlinePlayers.put(p.getUniqueId(), stats);

            if (mongoService != null) {
                mongoService.upsertPlayer(
                    new Document()
                        .append("uuid", p.getUniqueId().toString())
                        .append("name", p.getName())
                        .append("online", true)
                        .append("last_seen_ts", now)
                );
            }
        }

        // ---- listeners ----
        Bukkit.getPluginManager().registerEvents(
            new StatsListener(), this
        );

        // ---- playtime tick (authoritative) ----
        Bukkit.getScheduler().runTaskTimer(
            this,
            () -> tickPlaytime(),
            20L * 60,
            20L * 60
        );

        getLogger().info("ServerStats enabled");
    }

    private void tickPlaytime() {
        long now = System.currentTimeMillis();

        for (PlayerStats stats : onlinePlayers.values()) {
            long delta = now - stats.sessionStartTime;
            if (delta <= 0) continue;

            stats.totalPlaytimeMs += delta;
            stats.sessionStartTime = now;

            if (mongoService != null) {
                mongoService.inc(
                    stats.uuid.toString(),
                    "total_playtime_ms",
                    delta
                );
            }
        }
    }

    @Override
    public void onDisable() {
        long sessionRuntime = System.currentTimeMillis() - serverStartTime;
        totalRuntimeMs += sessionRuntime;

        long now = System.currentTimeMillis();

        for (PlayerStats stats : onlinePlayers.values()) {
            stats.totalPlaytimeMs += now - stats.sessionStartTime;
            stats.lastSeenTs = now;
            playerStorage.save(stats);
        }

        if (mongoService != null) {
          mongoService.markAllPlayersOffline(System.currentTimeMillis());
        }

        onlinePlayers.clear();

        if (storage != null) {
            storage.save();
        }

        if (httpServer != null) {
            httpServer.stop();
        }
    }
}

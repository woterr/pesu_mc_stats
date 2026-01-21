package dev.woter.serverstats;
import dev.woter.serverstats.mongo.MongoService;
import dev.woter.serverstats.mongo.MongoPusher;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class ServerStatsPlugin extends JavaPlugin {

    public static long serverStartTime;
    public static int totalJoins = 0;
    public static int totalDeaths = 0;
    public static StatsStorage storage;
    public static long totalRuntimeMs = 0;
    public static String HTTP_HOST;
    public static int HTTP_PORT;
    public static String HTTP_TOKEN;
    public static boolean HTTP_ENABLED;
    public static MongoService mongoService;

    public static PlayerStatsStorage playerStorage;
    public static Map<UUID, PlayerStats> onlinePlayers = new HashMap<>();

    private StatsHttpServer httpServer;

    @Override
    public void onEnable() {
        serverStartTime = System.currentTimeMillis();

        storage = new StatsStorage(this);
        storage.load();

        playerStorage = new PlayerStatsStorage(this);

        Bukkit.getPluginManager().registerEvents(
                new StatsListener(), this
        );

        saveDefaultConfig();

        boolean mongoEnabled = getConfig().getBoolean("mongo.enabled", false);

        if (mongoEnabled) {
            String uri = getConfig().getString("mongo.uri");
            String db = getConfig().getString("mongo.database");
            int interval = getConfig().getInt(
                    "stats.mongo_push_interval_seconds", 10);

            mongoService = new MongoService(uri, db);

            Bukkit.getScheduler().runTaskTimerAsynchronously(
                    this,
                    new MongoPusher(mongoService),
                    interval * 20L,
                    interval * 20L
            );

            getLogger().info("MongoDB metrics enabled (interval=" + interval + "s)");
        }

        HTTP_ENABLED = getConfig().getBoolean("http.enabled", true);
        HTTP_HOST = getConfig().getString("http.host", "127.0.0.1");
        HTTP_PORT = getConfig().getInt("http.port", 6767);
        HTTP_TOKEN = getConfig().getString("http.token", "").trim();
        getLogger().info(
            "HTTP TOKEN LOADED = [" +
            HTTP_TOKEN +
            "] len=" + HTTP_TOKEN.length()
        );


        if (HTTP_TOKEN.isEmpty()) {
            getLogger().warning("HTTP token is empty! Endpoint is unprotected.");
        }
        getLogger().info("HTTP TOKEN = [" + HTTP_TOKEN + "]");




        if (HTTP_ENABLED) {
            httpServer = new StatsHttpServer();
            try {
                httpServer.start();
                getLogger().info(
                    "Stats HTTP server started on " +
                    HTTP_HOST + ":" + HTTP_PORT
                );
            } catch (Exception e) {
                getLogger().severe("Failed to start HTTP server");
                e.printStackTrace();
            }
        }

        getLogger().info("ServerStats enabled");
    }

    @Override
    public void onDisable() {
        long sessionRuntime =
                System.currentTimeMillis() - serverStartTime;

        totalRuntimeMs += sessionRuntime;
        for (PlayerStats stats : onlinePlayers.values()) {
            long sessionTime =
                    System.currentTimeMillis() - stats.sessionStartTime;
            stats.totalPlaytimeMs += sessionTime;
            stats.lastSeenTs = System.currentTimeMillis();
            playerStorage.save(stats);
        }
        onlinePlayers.clear();

        if (mongoService != null) {
            mongoService.close();
        }

        if (storage != null) {
            storage.save();
        }

        if (httpServer != null) {
            httpServer.stop();
        }
    }


}

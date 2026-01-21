package dev.woter.serverstats;

import dev.woter.serverstats.mongo.MongoService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.util.UUID;


public class StatsListener implements Listener {

    private MongoService mongo() {
        return ServerStatsPlugin.mongoService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();

        ServerStatsPlugin.totalJoins++;
        ServerStatsPlugin.storage.save();

        PlayerStats stats =
                ServerStatsPlugin.playerStorage.load(p.getUniqueId());

        stats.totalJoins++;
        stats.sessionStartTime = now;
        stats.lastJoinTs = now;
        stats.lastSeenTs = now;
        stats.lastKnownName = p.getName();

        if (stats.firstJoinTs == 0) {
            stats.firstJoinTs = now;
        }

        ServerStatsPlugin.onlinePlayers.put(p.getUniqueId(), stats);
        ServerStatsPlugin.playerStorage.save(stats);

        if (mongo() != null) {
            mongo().onPlayerJoin(p.getUniqueId().toString(), p.getName(), now);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();

        PlayerStats stats =
                ServerStatsPlugin.onlinePlayers.remove(p.getUniqueId());

        if (stats == null) return;

        long sessionTime = now - stats.sessionStartTime;
        stats.totalPlaytimeMs += sessionTime;
        stats.lastSeenTs = now;

        ServerStatsPlugin.playerStorage.save(stats);

        if (mongo() != null) {
            mongo().onPlayerQuit(p.getUniqueId().toString(), sessionTime, now);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        ServerStatsPlugin.totalDeaths++;
        ServerStatsPlugin.storage.save();

        PlayerStats stats =
                ServerStatsPlugin.onlinePlayers.get(
                        event.getEntity().getUniqueId());

        if (stats != null) {
            stats.totalDeaths++;
            ServerStatsPlugin.playerStorage.save(stats);

            if (mongo() != null) {
                mongo().incField(stats.uuid.toString(), "total_deaths");
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        PlayerStats stats =
                ServerStatsPlugin.onlinePlayers.get(killer.getUniqueId());
        if (stats == null) return;

        if (event.getEntity() instanceof Player) {
            stats.playerKills++;
            if (mongo() != null)
                mongo().incField(stats.uuid.toString(), "player_kills");
        } else {
            stats.mobKills++;
            if (mongo() != null)
                mongo().incField(stats.uuid.toString(), "mob_kills");
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        PlayerStats stats =
                ServerStatsPlugin.onlinePlayers.get(
                        event.getPlayer().getUniqueId());

        if (stats != null) {
            stats.messagesSent++;
            if (mongo() != null)
                mongo().incField(stats.uuid.toString(), "messages_sent");
        }
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        PlayerStats stats =
                ServerStatsPlugin.onlinePlayers.get(
                        event.getPlayer().getUniqueId());

        if (stats == null) return;

        String key = event.getAdvancement().getKey().toString();

        if (!(key.startsWith("minecraft:story/")
            || key.startsWith("minecraft:nether/")
            || key.startsWith("minecraft:end/")
            || key.startsWith("minecraft:adventure/")
            || key.startsWith("minecraft:husbandry/"))) {
            return;
        }

        if (stats.advancements.add(key)) {
            ServerStatsPlugin.playerStorage.save(stats);
            if (mongo() != null)
                mongo().addAdvancement(stats.uuid.toString(), key);
        }
    }
}
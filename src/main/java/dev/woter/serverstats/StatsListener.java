package dev.woter.serverstats;

import dev.woter.serverstats.duels.DuelStatsAggregator;
import dev.woter.serverstats.mongo.MongoService;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bson.Document;

public class StatsListener implements Listener {

    private MongoService mongo() {
        return ServerStatsPlugin.mongoService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();

        PlayerStats stats =
            ServerStatsPlugin.playerStorage.load(p.getUniqueId());

        stats.sessionStartTime = now;
        stats.lastKnownName = p.getName();
        stats.lastSeenTs = now;

        boolean firstJoin = !p.hasPlayedBefore();

        if (firstJoin) {
            ServerStatsPlugin.totalUniqueJoins++;
            ServerStatsPlugin.storage.save();
        }

        ServerStatsPlugin.totalJoins++;
        ServerStatsPlugin.storage.save();

        ServerStatsPlugin.onlinePlayers.put(p.getUniqueId(), stats);

        if (mongo() != null) {
            mongo().upsertPlayer(
                new Document()
                    .append("uuid", p.getUniqueId().toString())
                    .append("name", p.getName())
                    .append("online", true)
                    .append("last_seen_ts", now)
                    .append("first_join_ts", p.getFirstPlayed())
            );

            mongo().inc(p.getUniqueId().toString(), "total_joins", 1);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();

        PlayerStats stats =
            ServerStatsPlugin.onlinePlayers.remove(p.getUniqueId());

        if (stats != null) {
            stats.totalPlaytimeMs += (now - stats.sessionStartTime);
            stats.lastSeenTs = now;
            ServerStatsPlugin.playerStorage.save(stats);
        }

        if (mongo() != null) {
            mongo().set(p.getUniqueId().toString(), "online", false);
            mongo().set(p.getUniqueId().toString(), "last_seen_ts", now);

            Document duelSnap =
                DuelStatsAggregator.buildSnapshot(
                    p.getUniqueId().toString(),
                    p.getName()
                );

            if (duelSnap != null) {
                mongo().upsertDuelSnapshot(duelSnap);
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!WorldClassifier.isTrackedWorld(
                event.getEntity().getWorld().getName())) return;

        ServerStatsPlugin.totalDeaths++;
        ServerStatsPlugin.storage.save();

        if (mongo() != null) {
            mongo().inc(
                event.getEntity().getUniqueId().toString(),
                "total_deaths",
                1
            );
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (!WorldClassifier.isTrackedWorld(
                killer.getWorld().getName())) return;

        if (mongo() != null) {
            mongo().inc(
                killer.getUniqueId().toString(),
                event.getEntity() instanceof Player
                    ? "player_kills"
                    : "mob_kills",
                1
            );
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (mongo() != null) {
            mongo().inc(
                event.getPlayer().getUniqueId().toString(),
                "messages_sent",
                1
            );
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!WorldClassifier.isTrackedWorld(
                event.getPlayer().getWorld().getName())) return;

        if (mongo() != null) {
            mongo().inc(
                event.getPlayer().getUniqueId().toString(),
                "blocks_broken",
                1
            );
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!WorldClassifier.isTrackedWorld(
                event.getPlayer().getWorld().getName())) return;

        if (mongo() != null) {
            mongo().inc(
                event.getPlayer().getUniqueId().toString(),
                "blocks_placed",
                1
            );
        }
    }

    @EventHandler
    public void onStatIncrease(PlayerStatisticIncrementEvent event) {
        Player p = event.getPlayer();

        if (!WorldClassifier.isTrackedWorld(
                p.getWorld().getName())) return;
        if (mongo() == null) return;

        long delta =
            (long) event.getNewValue() -
            (long) event.getPreviousValue();

        if (delta <= 0) return;

        switch (event.getStatistic()) {
            case FISH_CAUGHT ->
                mongo().inc(
                    p.getUniqueId().toString(),
                    "fish_caught",
                    delta
                );

            case TRADED_WITH_VILLAGER ->
                mongo().inc(
                    p.getUniqueId().toString(),
                    "villager_trades",
                    delta
                );
            case ANIMALS_BRED ->
                mongo().inc(
                    p.getUniqueId().toString(),
                    "animals_bred",
                    delta
                );


            default -> {}
        }
    }
}

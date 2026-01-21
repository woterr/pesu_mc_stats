package dev.woter.serverstats;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerStats {

    public UUID uuid;

    public int totalJoins = 0;
    public int totalDeaths = 0;

    public int playerKills = 0;
    public int mobKills = 0;
    public int messagesSent = 0;

    public long totalPlaytimeMs = 0;
    public long sessionStartTime = 0;

    public long firstJoinTs = 0;
    public long lastJoinTs = 0;
    public long lastSeenTs = 0;

    public String lastKnownName = "";
    public Set<String> advancements = new HashSet<>();

    public PlayerStats(UUID uuid) {
        this.uuid = uuid;
    }
}

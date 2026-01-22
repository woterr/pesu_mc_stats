package dev.woter.serverstats;

import java.util.Set;

public final class WorldClassifier {

    private static final Set<String> EXCLUDED_WORLDS = Set.of(
        "authworld",
        "duelworld"
    );

    private WorldClassifier() {}

    public static boolean isTrackedWorld(String worldName) {
        return !EXCLUDED_WORLDS.contains(worldName.toLowerCase());
    }
}

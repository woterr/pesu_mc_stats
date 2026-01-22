package dev.woter.serverstats.duels;

import org.bukkit.Bukkit;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;

public final class DuelStatsReader {

    private DuelStatsReader() {}

    public static JSONObject read(String uuid) {
        try {
            File dir = new File(Bukkit.getPluginsFolder(), "Duels/users");
            File file = new File(dir, uuid + ".json");
            if (!file.exists()) return null;

            return new JSONObject(Files.readString(file.toPath()));
        } catch (Exception e) {
            return null;
        }
    }
}

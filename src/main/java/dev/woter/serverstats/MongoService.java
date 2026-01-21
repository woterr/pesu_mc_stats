package dev.woter.serverstats.mongo;

import com.mongodb.client.*;
import com.mongodb.client.model.*;
import org.bson.Document;

import static com.mongodb.client.model.Filters.eq;

public class MongoService {

    private final MongoClient client;
    private final MongoDatabase db;

    private final MongoCollection<Document> serverMetrics;
    private final MongoCollection<Document> players;

    public MongoService(String uri, String dbName) {
        this.client = MongoClients.create(uri);
        this.db = client.getDatabase(dbName);

        this.serverMetrics = db.getCollection("server_metrics");
        this.players = db.getCollection("players");

        players.createIndex(Indexes.ascending("uuid"), new IndexOptions().unique(true));
        serverMetrics.createIndex(Indexes.ascending("timestamp"));
    }
    public void insertServerMetrics(Document doc) {
        serverMetrics.insertOne(doc);
    }

    public void onPlayerJoin(String uuid, String name, long now) {
        players.updateOne(
            eq("uuid", uuid),
            Updates.combine(
                Updates.set("name", name),
                Updates.set("online", true),
                Updates.set("last_seen_ts", now),
                Updates.inc("total_joins", 1),
                Updates.setOnInsert("first_join_ts", now),
                Updates.currentDate("updated_at")
            ),
            new UpdateOptions().upsert(true)
        );
    }

    public void onPlayerQuit(String uuid, long playtimeDelta, long now) {
        players.updateOne(
            eq("uuid", uuid),
            Updates.combine(
                Updates.set("online", false),
                Updates.set("last_seen_ts", now),
                Updates.inc("total_playtime_ms", playtimeDelta),
                Updates.currentDate("updated_at")
            )
        );
    }

    public void incField(String uuid, String field) {
        players.updateOne(
            eq("uuid", uuid),
            Updates.combine(
                Updates.inc(field, 1),
                Updates.currentDate("updated_at")
            )
        );
    }

    public void addAdvancement(String uuid, String key) {
        players.updateOne(
            eq("uuid", uuid),
            Updates.combine(
                Updates.addToSet("advancements", key),
                Updates.inc("advancement_count", 1),
                Updates.currentDate("updated_at")
            )
        );
    }

    public void heartbeat(String uuid, long playtimeDelta) {
        players.updateOne(
            eq("uuid", uuid),
            Updates.combine(
                Updates.inc("total_playtime_ms", playtimeDelta),
                Updates.set("online", true),
                Updates.currentDate("updated_at")
            )
        );
    }

    public void close() {
        client.close();
    }
}

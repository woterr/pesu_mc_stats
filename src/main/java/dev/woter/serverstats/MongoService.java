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
    private final MongoCollection<Document> duels;


    public MongoService(String uri, String dbName) {
        this.client = MongoClients.create(uri);
        this.db = client.getDatabase(dbName);

        this.serverMetrics = db.getCollection("server_metrics");
        this.players = db.getCollection("players");
        this.duels = db.getCollection("duels");

        players.createIndex(Indexes.ascending("uuid"), new IndexOptions().unique(true));
        serverMetrics.createIndex(Indexes.descending("timestamp"));
        duels.createIndex(Indexes.ascending("uuid"), new IndexOptions().unique(true));
    }


    public void insertServerMetrics(Document doc) {
        serverMetrics.insertOne(doc);
    }

    // public void incServer(String field, long by) {
    //     serverMetrics.updateOne(
    //         new Document("_id", "server"),
    //         Updates.inc(field, by),
    //         new UpdateOptions().upsert(true)
    //     );
    // }


    public void upsertPlayer(Document doc) {
        String uuid = doc.getString("uuid");

        Document set = new Document(doc);
        Object firstJoin = set.remove("first_join_ts");

        Document update = new Document("$set", set)
            .append("$currentDate", new Document("updated_at", true));

        if (firstJoin != null) {
            update.append("$setOnInsert", new Document("first_join_ts", firstJoin));
        }

        players.updateOne(eq("uuid", uuid), update, new UpdateOptions().upsert(true));
    }

    public void upsertPlayerSnapshot(Document snapshot) {
        players.updateOne(
            eq("uuid", snapshot.getString("uuid")),
            new Document("$set", snapshot)
                .append("$currentDate", new Document("updated_at", true)),
            new UpdateOptions().upsert(true)
        );
    }
    public void inc(String uuid, String field, int by) {
        players.updateOne(
            eq("uuid", uuid),
            Updates.combine(
                Updates.inc(field, by),
                Updates.currentDate("updated_at")
            )
        );
    }

    public void inc(String uuid, String field, long by) {
        players.updateOne(
            eq("uuid", uuid),
            Updates.combine(
                Updates.inc(field, by),
                Updates.currentDate("updated_at")
            )
        );
    }

    public void set(String uuid, String field, Object value) {
        players.updateOne(
            eq("uuid", uuid),
            Updates.combine(
                Updates.set(field, value),
                Updates.currentDate("updated_at")
            )
        );
    }

    public void upsertDuelSnapshot(Document doc) {
        duels.updateOne(
            eq("uuid", doc.getString("uuid")),
            new Document("$set", doc)
                .append("$currentDate", new Document("updated_at", true)),
            new UpdateOptions().upsert(true)
        );
    }


    public MongoCollection<Document> getDuelsCollection() {
        return duels;
    }

    public void markAllPlayersOffline(long ts) {
        players.updateMany(
            new Document("online", true),
            new Document("$set",
                new Document("online", false)
                    .append("last_seen_ts", ts)
            )
        );
    }


    public void close() {
        client.close();
    }
}

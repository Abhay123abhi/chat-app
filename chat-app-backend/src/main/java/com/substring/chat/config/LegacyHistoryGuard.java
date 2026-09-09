package com.substring.chat.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
public class LegacyHistoryGuard implements ApplicationRunner {
    private final MongoTemplate mongo;
    public LegacyHistoryGuard(MongoTemplate mongo) { this.mongo = mongo; }

    @Override
    public void run(ApplicationArguments args) {
        if (mongo.exists(Query.query(Criteria.where("messages.0").exists(true)), "rooms")) {
            throw new IllegalStateException("Legacy embedded history found. Stop the old app, back up MongoDB, then run scripts/migrate-history.js before starting. See README.");
        }
    }
}

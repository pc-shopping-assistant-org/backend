package com.ecm.catalog.bootstrap;

import java.sql.Connection;
import java.util.Map;

/** Explicit demo reset; never called by production seeding or application startup. */
public final class CatalogStockSeed {
    public static final String ARGUMENT = "--seed-demo-stock";
    private static final String RESOURCE = "/db/seed/demo/stock.sql";

    private CatalogStockSeed() {}

    public static void main(String[] args) throws Exception {
        run(System.getenv());
    }

    public static void run(Map<String, String> environment) throws Exception {
        String user = environment.getOrDefault("SPRING_DATASOURCE_USERNAME", environment.getOrDefault("POSTGRES_USER", "postgres"));
        String password = environment.get("SPRING_DATASOURCE_PASSWORD");
        if (password == null || password.isBlank()) password = SeedImages.required(environment, "POSTGRES_PASSWORD");
        String url = ProductionSeed.databaseUrl(environment, "CATALOG_DB", "catalog_db");
        try (var database = new SeedDatabase("catalog", url, user, password)) {
            seed(database.connection);
            database.connection.commit();
        }
        System.out.println("Demo stock seed complete: eligible catalog ACTIVE, each SKU quantity=10. Deleted records preserved.");
        System.out.println("Rebuild search with POST /sync-service/products/sync if CDC is not running.");
    }

    static void seed(Connection connection) throws Exception {
        CatalogSeed.seed(connection, RESOURCE);
    }
}

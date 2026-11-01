package com.ecm.catalog.bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public final class CatalogSeed {

    private static final String PROD_SEED_RESOURCE = "/db/seed/prod/hardware_categories.sql";

    private CatalogSeed() {}

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        ProductionSeed.run(System.getenv());
    }

    static void seed(Connection connection) throws SQLException, IOException {
        seed(connection, PROD_SEED_RESOURCE);
    }

    static void seed(Connection connection, String resourceName) throws SQLException, IOException {
        try (var resource = Objects.requireNonNull(CatalogSeed.class.getResourceAsStream(resourceName));
             var statement = connection.createStatement()) {
            statement.execute(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

}

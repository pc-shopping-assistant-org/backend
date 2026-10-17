package com.ecm.catalog.bootstrap;

import org.flywaydb.core.Flyway;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public final class CatalogSeed {

    private static final String PROD_SEED_RESOURCE = "/db/seed/prod/hardware_categories.sql";

    private CatalogSeed() {}

    public static void run() throws SQLException, IOException {
        Flyway flyway = Flyway.configure()
                .dataSource(requiredEnvironment("SPRING_DATASOURCE_URL"),
                        requiredEnvironment("SPRING_DATASOURCE_USERNAME"),
                        requiredEnvironment("SPRING_DATASOURCE_PASSWORD"))
                .load();
        flyway.migrate();
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            seed(connection);
        }
        System.out.println("Production catalog reference data seeded");
    }

    static void seed(Connection connection) throws SQLException, IOException {
        try (var resource = Objects.requireNonNull(CatalogSeed.class.getResourceAsStream(PROD_SEED_RESOURCE));
             var statement = connection.createStatement()) {
            statement.execute(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing seed configuration: " + name);
        }
        return value;
    }
}

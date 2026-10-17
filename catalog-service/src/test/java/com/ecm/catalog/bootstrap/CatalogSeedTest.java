package com.ecm.catalog.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "CATALOG_TEST_JDBC_URL", matches = "jdbc:postgresql:.*")
class CatalogSeedTest {

    @Test
    void upsertIsRepeatablePreservesExistingDataAndEnforcesHardwareCodes() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("CATALOG_TEST_JDBC_URL"),
                System.getenv("CATALOG_TEST_DB_USER"), System.getenv("CATALOG_TEST_DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                // Temporary table shadows any real categories; every write rolls back.
                statement.execute("""
                        CREATE TEMP TABLE categories (
                            id UUID PRIMARY KEY DEFAULT uuidv7(), name TEXT NOT NULL,
                            seo_name TEXT NOT NULL, status VARCHAR(15) NOT NULL DEFAULT 'ACTIVE',
                            description TEXT, parent_id UUID, created_at TIMESTAMPTZ DEFAULT now()
                        ) ON COMMIT DROP;
                        CREATE UNIQUE INDEX seed_test_name ON categories(name) WHERE status <> 'DELETED';
                        CREATE UNIQUE INDEX seed_test_seo ON categories(seo_name) WHERE status <> 'DELETED';
                        INSERT INTO categories (name, seo_name, status, description)
                        VALUES ('Edited CPU name', 'cpu', 'INACTIVE', 'Keep admin edits'),
                               ('Chuột', 'existing-mice', 'ACTIVE', 'Keep existing slug'),
                               ('Tai nghe', 'headset', 'DELETED', 'Do not recreate');
                        """);
                var existingIds = new ArrayList<UUID>();
                try (var rows = statement.executeQuery("SELECT id FROM categories ORDER BY id")) {
                    while (rows.next()) existingIds.add(rows.getObject(1, UUID.class));
                }
                try (var resource = getClass().getResourceAsStream("/db/migration/V9__hardware_category_type.sql")) {
                    statement.execute(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
                }

                CatalogSeed.seed(connection);
                var firstIds = new ArrayList<UUID>();
                try (var rows = statement.executeQuery("SELECT id FROM categories ORDER BY id")) {
                    while (rows.next()) firstIds.add(rows.getObject(1, UUID.class));
                }
                assertEquals(12, firstIds.size());
                assertTrue(firstIds.containsAll(existingIds));

                CatalogSeed.seed(connection);
                var secondIds = new ArrayList<UUID>();
                try (var rows = statement.executeQuery("SELECT id FROM categories ORDER BY id")) {
                    while (rows.next()) secondIds.add(rows.getObject(1, UUID.class));
                }
                assertEquals(firstIds, secondIds);
                try (var rows = statement.executeQuery("SELECT name, status, description, hardware_type FROM categories WHERE seo_name = 'cpu'")) {
                    rows.next();
                    assertEquals("Edited CPU name", rows.getString(1));
                    assertEquals("INACTIVE", rows.getString(2));
                    assertEquals("Keep admin edits", rows.getString(3));
                    assertEquals("CPU", rows.getString(4));
                }
                try (var rows = statement.executeQuery("SELECT count(*) FROM categories WHERE seo_name = 'headset' AND status <> 'DELETED'")) {
                    rows.next();
                    assertEquals(0, rows.getInt(1));
                }
                var savepoint = connection.setSavepoint();
                assertThrows(SQLException.class, () -> statement.execute("UPDATE categories SET hardware_type = 'FAKE' WHERE seo_name = 'cpu'"));
                connection.rollback(savepoint);
            } finally {
                connection.rollback();
            }
        }
    }
}

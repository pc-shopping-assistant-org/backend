package com.ecm.catalog.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfEnvironmentVariable(named = "CATALOG_TEST_JDBC_URL", matches = "jdbc:postgresql:.*")
class CatalogStockSeedTest {
    @Test
    void databaseFailureRollsBackEarlierActivation() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("CATALOG_TEST_JDBC_URL"),
                System.getenv("CATALOG_TEST_DB_USER"), System.getenv("CATALOG_TEST_DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TEMP TABLE brands (id int PRIMARY KEY, status text) ON COMMIT DROP;
                        CREATE TEMP TABLE categories (id int PRIMARY KEY, status text) ON COMMIT DROP;
                        CREATE TEMP TABLE products (id int PRIMARY KEY, category_id int, brand_id int, status text, updated_at timestamptz) ON COMMIT DROP;
                        CREATE TEMP TABLE product_variants (id int PRIMARY KEY, product_id int, status text, quantity int CHECK(quantity < 10), updated_at timestamptz) ON COMMIT DROP;
                        INSERT INTO brands VALUES (1,'INACTIVE');
                        INSERT INTO categories VALUES (1,'INACTIVE');
                        INSERT INTO products VALUES (1,1,1,'INACTIVE',null);
                        INSERT INTO product_variants VALUES (1,1,'INACTIVE',0,null);
                        """);
                var beforeSeed = connection.setSavepoint();
                assertThrows(java.sql.SQLException.class, () -> CatalogStockSeed.seed(connection));
                connection.rollback(beforeSeed);
                try (var rows = statement.executeQuery("SELECT status FROM brands WHERE id=1")) {
                    rows.next();
                    assertEquals("INACTIVE", rows.getString(1));
                }
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void stocksVisibleVariantsAndPreservesDeletedRowsOnRepeat() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("CATALOG_TEST_JDBC_URL"),
                System.getenv("CATALOG_TEST_DB_USER"), System.getenv("CATALOG_TEST_DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TEMP TABLE brands (id int PRIMARY KEY, status text, updated_at timestamptz) ON COMMIT DROP;
                        CREATE TEMP TABLE categories (id int PRIMARY KEY, status text, updated_at timestamptz) ON COMMIT DROP;
                        CREATE TEMP TABLE products (id int PRIMARY KEY, category_id int, brand_id int, status text, updated_at timestamptz) ON COMMIT DROP;
                        CREATE TEMP TABLE product_variants (id int PRIMARY KEY, product_id int, status text, quantity int, updated_at timestamptz) ON COMMIT DROP;
                        INSERT INTO brands VALUES (1,'INACTIVE',null), (2,'DELETED',null);
                        INSERT INTO categories VALUES (1,'INACTIVE',null), (2,'DELETED',null);
                        INSERT INTO products VALUES (1,1,1,'INACTIVE',null), (2,1,1,'DELETED',null), (3,2,1,'ACTIVE',null), (4,1,2,'ACTIVE',null);
                        INSERT INTO product_variants VALUES (1,1,'INACTIVE',0,null), (2,1,'ACTIVE',99,null),
                            (3,1,'DELETED',7,null), (4,2,'ACTIVE',8,null), (5,3,'ACTIVE',9,null), (6,4,'ACTIVE',9,null);
                        """);
                CatalogStockSeed.seed(connection);
                CatalogStockSeed.seed(connection);
                try (var rows = statement.executeQuery("SELECT id,status,quantity FROM product_variants ORDER BY id")) {
                    for (int id = 1; id <= 6; id++) {
                        rows.next();
                        assertEquals(id, rows.getInt("id"));
                        assertEquals(id == 3 ? "DELETED" : "ACTIVE", rows.getString("status"));
                        assertEquals(switch (id) {case 1, 2 -> 10; case 3 -> 7; case 4 -> 8; default -> 9;}, rows.getInt("quantity"));
                    }
                }
                try (var rows = statement.executeQuery("SELECT status FROM products WHERE id=2")) {
                    rows.next();
                    assertEquals("DELETED", rows.getString(1));
                }
            } finally {
                connection.rollback();
            }
        }
    }
}

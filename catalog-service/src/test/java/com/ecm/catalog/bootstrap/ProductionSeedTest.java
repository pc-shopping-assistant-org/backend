package com.ecm.catalog.bootstrap;

import com.ecm.catalog.entity.AttributeDataType;
import com.ecm.catalog.entity.CatalogStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProductionSeedTest {
    @TempDir Path temporary;

    @Test
    void bundledDatasetLoadsTypedContractsAndIncludesReferencedImages() throws Exception {
        SeedData data = SeedData.load(null);
        assertFalse(data.products().isEmpty());
        assertTrue(data.productVariants().stream().allMatch(variant -> variant.status() == CatalogStatus.ACTIVE && variant.quantity() == 0));
        var files = data.files().stream().map(SeedData.Media::id).collect(java.util.stream.Collectors.toSet());
        assertTrue(data.productImages().stream().allMatch(image -> files.contains(image.imageFileId())));
        assertTrue(data.productVariants().stream().allMatch(variant -> variant.imageFileId() == null || files.contains(variant.imageFileId())));
        assertEquals(1, data.products().stream().map(SeedData.Product::createdBy).distinct().count());
    }

    @Test
    void configuredDatasetsMergeWithoutDuplicatingSharedMasterRows() throws Exception {
        SeedData data = SeedData.loadAll("/seed/catalog_seed.json,/seed/pc_catalog_seed.json,/seed/gear_catalog_seed.json");

        assertEquals(2_389, data.products().size());
        assertEquals(2_389, data.productVariants().size());
        assertEquals(20, data.categories().stream().map(SeedData.Category::hardwareType).distinct().count());
        // SSD and HDD are hardware types of their own, not STORAGE
        assertEquals(Set.of("SSD", "HDD"), data.categories().stream().filter(category -> category.seoName().equals("ssd") || category.seoName().equals("hdd"))
                .map(SeedData.Category::hardwareType).collect(java.util.stream.Collectors.toSet()));
        assertTrue(data.categories().stream().allMatch(category -> category.hardwareType() != null));
        assertEquals(data.brands().size(), data.brands().stream().map(SeedData.Brand::id).distinct().count());
        assertEquals(10, data.attributeDefinitions().stream().filter(attribute ->
                Set.of("cpu", "mainboard", "ram", "ram_2", "storage", "psu", "gpu", "case", "cooler", "monitor")
                        .contains(attribute.key())).count());
    }

    @Test
    void naturalKeyMappingReusesDatabaseIdsInsteadOfBreakingReferences() {
        UUID sourceId = UUID.randomUUID();
        UUID databaseId = UUID.randomUUID();
        var brand = new SeedData.Brand(sourceId, "Seed Brand", "seed-brand", null, null, CatalogStatus.ACTIVE);
        var ids = SeedDatabase.ids(List.of(brand), SeedData.Brand::id, SeedData.Brand::seoName,
                List.of(Map.of("id", databaseId, "seo_name", "seed-brand")), row -> (String) row.get("seo_name"));
        assertEquals(databaseId, SeedDatabase.reference(ids, sourceId));
    }

    @Test
    void siblingDatabaseUrlsKeepHostPortAndConnectionOptions() {
        var environment = Map.of("SPRING_DATASOURCE_URL", "jdbc:postgresql://db.internal:5432/catalog_db?sslmode=require", "IDENTITY_DB", "tenant_identity");
        assertEquals("jdbc:postgresql://db.internal:5432/tenant_identity?sslmode=require",
                ProductionSeed.databaseUrl(environment, "IDENTITY_DB", "identity_db"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SEED_TEST_JDBC_URL", matches = "jdbc:postgresql:.*")
    void fullMergedCatalogStagesAgainstCurrentSchemaAndTemplatesWithoutImageRequests() throws Exception {
        String adminUrl = System.getenv("SEED_TEST_JDBC_URL");
        String user = System.getenv().getOrDefault("SEED_TEST_DB_USER", "postgres");
        String password = System.getenv().getOrDefault("SEED_TEST_DB_PASSWORD", "postgres");
        String name = "seed_full_" + UUID.randomUUID().toString().replace("-", "");
        String url = ProductionSeed.databaseUrl(Map.of("SPRING_DATASOURCE_URL", adminUrl, "CATALOG_DB", name), "CATALOG_DB", "catalog_db");
        try (var admin = DriverManager.getConnection(adminUrl, user, password)) {
            try {
                try (var statement = admin.createStatement()) { statement.execute("CREATE DATABASE " + name); }
                SeedData data = SeedData.loadAll("/seed/catalog_seed.json,/seed/pc_catalog_seed.json,/seed/gear_catalog_seed.json");
                var mediaIds = data.files().stream().map(SeedData.Media::id)
                        .collect(java.util.stream.Collectors.toMap(id -> id, id -> id));
                try (var database = new SeedDatabase("catalog", url, user, password)) {
                    var seed = new CatalogDatasetSeed(database);
                    seed.insert(data, data.products().getFirst().createdBy(), mediaIds);
                    assertEquals(data.products().size(), scalar(database.connection, "SELECT count(*) FROM products"));
                    assertEquals(data.productVariants().size(), scalar(database.connection, "SELECT count(*) FROM product_variants"));
                    assertEquals(data.productImages().size(), scalar(database.connection, "SELECT count(*) FROM product_images"));
                    assertEquals(19, scalar(database.connection, "SELECT count(*) FROM hardware_types"));
                    assertEquals(1, scalar(database.connection, "SELECT count(*) FROM categories WHERE seo_name = 'other'"));
                    assertEquals(0, scalar(database.connection, "SELECT count(*) FROM categories WHERE hardware_type_id IS NULL"));
                    assertEquals(data.productVariants().size(), scalar(database.connection,
                            "SELECT count(*) FROM product_variants WHERE status = 'ACTIVE' AND quantity = 0"));
                    try (var statement = database.connection.createStatement()) {
                        statement.execute("UPDATE categories SET display_order = 777 WHERE seo_name = 'pc-gaming'");
                    }
                    seed.insert(data, data.products().getFirst().createdBy(), mediaIds);
                    assertEquals(data.products().size(), scalar(database.connection, "SELECT count(*) FROM products"));
                    assertEquals(data.productImages().size(), scalar(database.connection, "SELECT count(*) FROM product_images"));
                    assertEquals(777, scalar(database.connection, "SELECT display_order FROM categories WHERE seo_name = 'pc-gaming'"));
                    // Closing an uncommitted bootstrap connection must roll all staged seed rows back.
                }
                try (var connection = DriverManager.getConnection(url, user, password)) {
                    assertEquals(0, scalar(connection, "SELECT count(*) FROM products"));
                }
            } finally {
                try (var statement = admin.createStatement()) { statement.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)"); }
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SEED_TEST_JDBC_URL", matches = "jdbc:postgresql:.*")
    void productionSeedValidatesBeforeUploadAndRerunsPreserveExistingRowsAndStock() throws Exception {
        String adminUrl = System.getenv("SEED_TEST_JDBC_URL");
        String user = System.getenv().getOrDefault("SEED_TEST_DB_USER", "postgres");
        String password = System.getenv().getOrDefault("SEED_TEST_DB_PASSWORD", "postgres");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        var names = Map.of("IDENTITY_DB", "seed_identity_" + suffix, "MEDIA_DB", "seed_media_" + suffix,
                "CATALOG_DB", "seed_catalog_" + suffix);
        try (Connection admin = DriverManager.getConnection(adminUrl, user, password);
             var server = new SeedImagesTest.StorageServer()) {
            try {
                for (String name : names.values()) try (var statement = admin.createStatement()) { statement.execute("CREATE DATABASE " + name); }
                var environment = new HashMap<>(server.environment());
                environment.putAll(names);
                environment.put("SPRING_DATASOURCE_URL", adminUrl);
                environment.put("SPRING_DATASOURCE_USERNAME", user);
                environment.put("SPRING_DATASOURCE_PASSWORD", password);
                Path seedFile = temporary.resolve("catalog.json");
                environment.put("SEED_FILE", seedFile.toString());
                Fixture fixture = new Fixture(server);
                String catalogUrl = ProductionSeed.databaseUrl(environment, "CATALOG_DB", "catalog_db");
                prepareExistingCatalog(catalogUrl, user, password, fixture);

                SeedData.JSON.writeValue(seedFile.toFile(), fixture.data("invalid number"));
                assertThrows(IllegalStateException.class, () -> ProductionSeed.run(environment));
                assertEquals(0, server.sourceRequests.get());
                assertEquals(0, server.putRequests.get());
                try (var media = DriverManager.getConnection(ProductionSeed.databaseUrl(environment, "MEDIA_DB", "media_db"), user, password)) {
                    assertEquals(0, scalar(media, "SELECT count(*) FROM files"));
                }
                try (var identity = DriverManager.getConnection(ProductionSeed.databaseUrl(environment, "IDENTITY_DB", "identity_db"), user, password)) {
                    assertEquals(0, scalar(identity, "SELECT count(*) FROM accounts WHERE email = 'catalog.seed@pcshop.local'"));
                }

                SeedData.JSON.writeValue(seedFile.toFile(), fixture.data(8));
                server.sourceContentType = "text/html";
                server.sourceImage = "<html>source image no longer exists</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                ProductionSeed.run(environment);
                assertEquals(1, server.sourceRequests.get());
                assertEquals(0, server.putRequests.get());
                try (var catalog = DriverManager.getConnection(catalogUrl, user, password)) {
                    assertEquals(2, scalar(catalog, "SELECT count(*) FROM products"));
                    assertEquals(1, scalar(catalog, "SELECT count(*) FROM product_images"));
                    assertEquals(1, scalar(catalog, "SELECT count(*) FROM product_variants WHERE id = '" + fixture.newVariant + "' AND image_file_id IS NULL"));
                }
                try (var media = DriverManager.getConnection(ProductionSeed.databaseUrl(environment, "MEDIA_DB", "media_db"), user, password)) {
                    assertEquals(0, scalar(media, "SELECT count(*) FROM files"));
                }

                server.sourceContentType = "image/png";
                server.sourceImage = SeedImagesTest.StorageServer.IMAGE;
                ProductionSeed.run(environment);
                assertEquals(2, server.sourceRequests.get());
                assertEquals(1, server.putRequests.get());
                try (var catalog = DriverManager.getConnection(catalogUrl, user, password)) {
                    assertEquals(2, scalar(catalog, "SELECT count(*) FROM products"));
                    assertEquals(2, scalar(catalog, "SELECT count(*) FROM product_variants"));
                    assertEquals(3, scalar(catalog, "SELECT count(*) FROM product_images"));
                    assertEquals(1, scalar(catalog, "SELECT count(*) FROM product_variants WHERE id = '" + fixture.storedVariant + "' AND quantity = 7 AND price = 999"));
                    assertEquals(1, scalar(catalog, "SELECT count(*) FROM product_variants WHERE product_id = '" + fixture.storedProduct + "'"));
                    try (var statement = catalog.createStatement()) {
                        statement.execute("UPDATE products SET name = 'Admin edited', status = 'INACTIVE' WHERE id = '" + fixture.newProduct + "'");
                        statement.execute("UPDATE product_variants SET quantity = 9, price = 123 WHERE id = '" + fixture.newVariant + "'");
                        statement.execute("UPDATE categories SET status = 'DELETED', seo_name = 'admin-cpu' WHERE seo_name = 'cpu'");
                    }
                }

                environment.remove("AWS_ACCESS_KEY_ID");
                environment.remove("AWS_SECRET_ACCESS_KEY");
                ProductionSeed.run(environment);
                assertEquals(2, server.sourceRequests.get());
                assertEquals(1, server.putRequests.get());
                try (var catalog = DriverManager.getConnection(catalogUrl, user, password)) {
                    assertEquals(2, scalar(catalog, "SELECT count(*) FROM products"));
                    assertEquals(2, scalar(catalog, "SELECT count(*) FROM product_variants"));
                    assertEquals(3, scalar(catalog, "SELECT count(*) FROM product_images"));
                    assertEquals(1, scalar(catalog, "SELECT count(*) FROM products WHERE id = '" + fixture.newProduct + "' AND name = 'Admin edited' AND status = 'INACTIVE'"));
                    assertEquals(1, scalar(catalog, "SELECT count(*) FROM product_variants WHERE id = '" + fixture.newVariant + "' AND quantity = 9 AND price = 123"));
                    assertEquals(1, scalar(catalog, "SELECT count(*) FROM categories WHERE seo_name = 'admin-cpu' AND name = 'CPU' AND status = 'DELETED'"));
                    assertEquals(1, scalar(catalog, "SELECT count(*) FROM categories WHERE name = 'CPU'"));
                    assertEquals(0, scalar(catalog, "SELECT count(*) FROM categories WHERE seo_name = 'cpu'"));
                }
                try (var media = DriverManager.getConnection(ProductionSeed.databaseUrl(environment, "MEDIA_DB", "media_db"), user, password)) {
                    assertEquals(1, scalar(media, "SELECT count(*) FROM files WHERE storage_provider = 'S3' AND size_bytes = " + SeedImagesTest.StorageServer.IMAGE.length));
                }
            } finally {
                for (String name : names.values()) try (var statement = admin.createStatement()) { statement.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)"); }
            }
        }
    }

    private static long scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static void prepareExistingCatalog(String url, String user, String password, Fixture fixture) throws Exception {
        try (var database = new SeedDatabase("catalog", url, user, password)) {
            CatalogSeed.seed(database.connection);
            UUID category = (UUID) database.query("SELECT id FROM categories WHERE seo_name = 'cpu'").getFirst().get("id");
            database.insert("INSERT INTO brands (id, name, seo_name) VALUES (?, ?, ?)",
                    java.util.Collections.singletonList(new Object[] {fixture.storedBrand, "Existing Brand", "seed-brand"}));
            database.insert("INSERT INTO products (id, name, seo_name, brand_id, category_id, specifications, created_by) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)",
                    java.util.Collections.singletonList(new Object[] {fixture.storedProduct, "Existing product", "existing-product", fixture.storedBrand, category, "{\"core_count\":16}", fixture.employee}));
            database.insert("INSERT INTO product_variants (id, product_id, sku, price, quantity, warranty_months, created_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    java.util.Collections.singletonList(new Object[] {fixture.storedVariant, fixture.storedProduct, "existing-sku", 999L, 7, 12, fixture.employee}));
            database.insert("INSERT INTO product_images (id, product_id, image_file_id, is_main) VALUES (?, ?, ?, ?)",
                    java.util.Collections.singletonList(new Object[] {UUID.randomUUID(), fixture.storedProduct, UUID.randomUUID(), true}));
            database.connection.commit();
        }
    }

    private static final class Fixture {
        final UUID employee = UUID.randomUUID();
        final UUID category = UUID.randomUUID();
        final UUID seedBrand = UUID.randomUUID();
        final UUID storedBrand = UUID.randomUUID();
        final UUID seedProduct = UUID.randomUUID();
        final UUID storedProduct = UUID.randomUUID();
        final UUID newProduct = UUID.randomUUID();
        final UUID seedVariant = UUID.randomUUID();
        final UUID storedVariant = UUID.randomUUID();
        final UUID newVariant = UUID.randomUUID();
        final UUID fileId = UUID.randomUUID();
        final UUID existingImage = UUID.randomUUID();
        final UUID newImage = UUID.randomUUID();
        final String sourceUrl;

        Fixture(SeedImagesTest.StorageServer server) { sourceUrl = server.endpoint() + "/source.png"; }

        SeedData data(Object cores) {
            return new SeedData(
                    List.of(new SeedData.Media(fileId, "source-key", "seed.png", "image/png", sourceUrl)),
                    List.of(new SeedData.Brand(seedBrand, "Seed Brand", "seed-brand", null, null, CatalogStatus.ACTIVE)),
                    List.of(new SeedData.Category(category, "CPU", "cpu", null, null, "CPU", 10, CatalogStatus.ACTIVE)),
                    List.of(new SeedData.Product(seedProduct, "Seed product", "existing-product", seedBrand, category, Map.of("core_count", 8), null, CatalogStatus.ACTIVE, employee),
                            new SeedData.Product(newProduct, "New product", "new-product", seedBrand, category, Map.of("core_count", cores), null, CatalogStatus.ACTIVE, employee)),
                    List.of(new SeedData.Variant(seedVariant, seedProduct, 100L, 0, "existing-sku", null, null, 12, null, null, CatalogStatus.INACTIVE, fileId),
                            new SeedData.Variant(newVariant, newProduct, 200L, 0, "new-sku", null, null, 12, null, null, CatalogStatus.INACTIVE, fileId)),
                    List.of(new SeedData.Image(existingImage, seedProduct, fileId, true), new SeedData.Image(newImage, newProduct, fileId, true)),
                    List.of(new SeedData.Attribute("core_count", "Cores", AttributeDataType.NUMBER, null, null, true, true)),
                    List.of(new SeedData.CategoryAttribute("cpu", "core_count", true, 0)));
        }
    }
}

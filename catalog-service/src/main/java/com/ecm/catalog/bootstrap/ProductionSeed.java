package com.ecm.catalog.bootstrap;

import com.ecm.catalog.entity.CatalogStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ProductionSeed {
    private static final String EMPLOYEE_ROLE = "ROLE_EMPLOYEE";
    private static final String EMPLOYEE_EMAIL = "catalog.seed@pcshop.local";
    private static final String DEFAULT_SEED_FILES =
            "/seed/catalog_seed.json,/seed/pc_catalog_seed.json,/seed/gear_catalog_seed.json";

    private ProductionSeed() {}

    static void run(Map<String, String> environment) throws Exception {
        String seedFiles = environment.get("SEED_FILES");
        String singleSeedFile = environment.get("SEED_FILE");
        SeedData dataset;
        if (seedFiles != null && !seedFiles.isBlank()) {
            dataset = SeedData.loadAll(seedFiles);
        } else if (singleSeedFile != null && !singleSeedFile.isBlank()) {
            dataset = SeedData.load(singleSeedFile);
        } else {
            dataset = SeedData.loadAll(DEFAULT_SEED_FILES);
        }
        if (dataset.products().isEmpty()) throw new IllegalArgumentException("Catalog seed contains no products");
        String user = environment.getOrDefault("SPRING_DATASOURCE_USERNAME", environment.getOrDefault("POSTGRES_USER", "postgres"));
        String password = environment.get("SPRING_DATASOURCE_PASSWORD");
        if (password == null || password.isBlank()) password = SeedImages.required(environment, "POSTGRES_PASSWORD");
        var committed = new ArrayList<String>();
        try (var identity = new SeedDatabase("identity", databaseUrl(environment, "IDENTITY_DB", "identity_db"), user, password);
             var media = new SeedDatabase("media", databaseUrl(environment, "MEDIA_DB", "media_db"), user, password);
             var catalog = new SeedDatabase("catalog", databaseUrl(environment, "CATALOG_DB", "catalog_db"), user, password)) {
            // Stage and validate catalog before making any object-storage writes or seed commits.
            UUID employeeId = employee(identity, dataset);
            var catalogSeed = new CatalogDatasetSeed(catalog);
            var prepared = catalogSeed.prepare(dataset);
            var images = new SeedImages(media, dataset.files());
            images.insert(environment);
            catalogSeed.insert(dataset, employeeId, images.ids, prepared);
            for (var database : List.of(identity, media, catalog)) {
                database.connection.commit();
                committed.add(database.name);
                System.out.println("Committed " + database.name);
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Production seed failed; committed databases=" + committed
                    + ". Uploaded objects remain reusable on retry. " + failure.getMessage(), failure);
        }
        sync(environment);
        System.out.println("Production seed complete; existing rows and objects preserved");
    }

    static UUID employee(SeedDatabase identity, SeedData data) throws Exception {
        var owners = data.products().stream().map(SeedData.Product::createdBy).distinct().toList();
        if (owners.size() != 1 || owners.getFirst() == null) throw new IllegalArgumentException("Expected one seed employee ID");
        UUID seedId = owners.getFirst();
        var existing = identity.query("""
                SELECT a.id, r.name AS role FROM accounts a JOIN roles r ON r.id = a.role_id
                WHERE a.id = ? OR a.email = ? ORDER BY a.id
                """, seedId, EMPLOYEE_EMAIL);
        if (existing.size() > 1) throw new IllegalStateException("Seed employee ID and email belong to different accounts");
        UUID id = seedId;
        if (!existing.isEmpty()) {
            if (!EMPLOYEE_ROLE.equals(existing.getFirst().get("role"))) throw new IllegalStateException("Seed account is not an employee");
            id = (UUID) existing.getFirst().get("id");
        } else {
            var roles = identity.query("SELECT id FROM roles WHERE name = ?", EMPLOYEE_ROLE);
            if (roles.isEmpty()) throw new IllegalStateException("Employee role is missing");
            identity.insert("INSERT INTO accounts (id, email, role_id, status) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                    java.util.Collections.singletonList(new Object[] {id, EMPLOYEE_EMAIL, roles.getFirst().get("id"), CatalogStatus.ACTIVE}));
        }
        identity.insert("""
                INSERT INTO employees (account_id, first_name, last_name, gender)
                VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, java.util.Collections.singletonList(new Object[] {id, "Catalog", "Seed", "MALE"}));
        return id;
    }

    static String databaseUrl(Map<String, String> environment, String key, String defaultName) {
        String name = environment.getOrDefault(key, defaultName);
        if (!name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) throw new IllegalArgumentException("Invalid database name: " + key);
        String base = environment.getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://"
                + environment.getOrDefault("POSTGRES_HOST", "localhost") + ":"
                + environment.getOrDefault("POSTGRES_PORT", "5433") + "/catalog_db");
        if (!base.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException("Seed requires a PostgreSQL JDBC URL");
        URI uri = URI.create(base.substring("jdbc:".length()));
        return "jdbc:" + uri.resolve("/" + name) + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    }

    private static void sync(Map<String, String> environment) throws Exception {
        String url = environment.get("SYNC_URL");
        if (url == null || url.isBlank()) {
            System.out.println("Search: catalog committed; rebuild with POST /sync-service/products/sync when services are ready");
            return;
        }
        try (var http = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5))
                    .header("Authorization", "Bearer " + SeedImages.required(environment, "SYNC_TOKEN"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("Catalog committed; search sync HTTP " + response.statusCode());
            var result = SeedData.JSON.readTree(response.body()).path("data");
            if (!result.has("failed") || result.path("failed").asInt() != 0) {
                throw new IllegalStateException("Catalog committed; search sync did not report zero failures");
            }
        }
    }
}

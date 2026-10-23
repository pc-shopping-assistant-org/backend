package com.ecm.catalog.bootstrap;

import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

final class SeedDatabase implements AutoCloseable {
    private static final int BATCH_SIZE = 500;
    final Connection connection;
    final String name;

    SeedDatabase(String name, String url, String user, String password) throws SQLException {
        this.name = name;
        Flyway.configure().dataSource(url, user, password)
                .locations(name.equals("catalog") ? "classpath:db/migration" : "classpath:seed-migration/" + name)
                .load().migrate();
        connection = DriverManager.getConnection(url, user, password);
        connection.setAutoCommit(false);
    }

    List<Map<String, Object>> query(String sql, Object... parameters) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (var results = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (results.next()) {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    for (int column = 1; column <= results.getMetaData().getColumnCount(); column++) {
                        row.put(results.getMetaData().getColumnLabel(column), results.getObject(column));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    int insert(String sql, List<Object[]> rows) throws SQLException {
        int inserted = 0;
        try (var statement = connection.prepareStatement(sql)) {
            int batch = 0;
            for (Object[] row : rows) {
                bind(statement, row);
                statement.addBatch();
                if (++batch == BATCH_SIZE) {
                    inserted += count(statement.executeBatch());
                    batch = 0;
                }
            }
            if (batch > 0) inserted += count(statement.executeBatch());
        }
        return inserted;
    }

    static <T> Map<UUID, UUID> ids(List<T> seeds, Function<T, UUID> seedId,
                                   Function<T, String> seedKey, List<Map<String, Object>> existing,
                                   Function<Map<String, Object>, String> storedKey) {
        Map<String, UUID> byKey = new java.util.HashMap<>();
        Map<UUID, UUID> mapping = new java.util.HashMap<>();
        for (var row : existing) {
            UUID id = (UUID) row.get("id");
            byKey.put(storedKey.apply(row), id);
            mapping.put(id, id);
        }
        for (T seed : seeds) {
            UUID id = seedId.apply(seed);
            mapping.putIfAbsent(id, byKey.getOrDefault(seedKey.apply(seed), id));
        }
        return mapping;
    }

    static UUID reference(Map<UUID, UUID> ids, UUID id) {
        if (id == null) return null;
        UUID mapped = ids.get(id);
        if (mapped == null) throw new IllegalArgumentException("Unknown seed reference: " + id);
        return mapped;
    }

    void requireIds(String table, java.util.Collection<UUID> expected) throws SQLException {
        var present = query("SELECT id FROM " + table).stream().map(row -> row.get("id"))
                .collect(java.util.stream.Collectors.toSet());
        for (UUID id : expected) {
            if (!present.contains(id)) throw new IllegalStateException(table + ": seed row skipped by a conflicting unique key: " + id);
        }
    }

    private static void bind(java.sql.PreparedStatement statement, Object[] values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            statement.setObject(i + 1, value instanceof Enum<?> code ? code.name() : value);
        }
    }

    private static int count(int[] counts) {
        return java.util.Arrays.stream(counts).map(count -> count == java.sql.Statement.SUCCESS_NO_INFO ? 1 : count).sum();
    }

    @Override
    public void close() throws SQLException {
        try { connection.rollback(); } finally { connection.close(); }
    }
}

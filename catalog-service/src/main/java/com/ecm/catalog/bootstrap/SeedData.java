package com.ecm.catalog.bootstrap;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ecm.catalog.entity.AttributeDataType;
import com.ecm.catalog.entity.CatalogStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record SeedData(List<Media> files, List<Brand> brands, List<Category> categories,
                List<Product> products, List<Variant> productVariants, List<Image> productImages,
                List<Attribute> attributeDefinitions, List<CategoryAttribute> categoryAttributes) {
    static final ObjectMapper JSON = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    static SeedData load(String seedFile) throws IOException {
        try (InputStream input = open(seedFile)) {
            if (input == null) throw new IOException("Missing catalog seed resource");
            return JSON.readValue(input, SeedData.class);
        }
    }

    /** Loads a comma-separated set of datasets and merges records by their stable seed keys. */
    static SeedData loadAll(String configuredFiles) throws IOException {
        List<SeedData> datasets = new ArrayList<>();
        for (String raw : configuredFiles.split(",")) {
            String file = raw.trim();
            if (!file.isEmpty()) datasets.add(load(file));
        }
        if (datasets.isEmpty()) throw new IOException("SEED_FILES contains no files");
        return merge(datasets);
    }

    private static InputStream open(String seedFile) throws IOException {
        if (seedFile == null || seedFile.isBlank()) return SeedData.class.getResourceAsStream("/seed/catalog_seed.json");
        Path path = Path.of(seedFile);
        if (Files.exists(path)) return Files.newInputStream(path);
        String resource = seedFile.startsWith("/") ? seedFile : "/" + seedFile;
        return SeedData.class.getResourceAsStream(resource);
    }

    private static SeedData merge(List<SeedData> datasets) {
        return new SeedData(
                mergeBy(datasets, SeedData::files, SeedData.Media::id),
                mergeBy(datasets, SeedData::brands, SeedData.Brand::id),
                mergeBy(datasets, SeedData::categories, SeedData.Category::id),
                mergeBy(datasets, SeedData::products, SeedData.Product::id),
                mergeBy(datasets, SeedData::productVariants, SeedData.Variant::id),
                mergeBy(datasets, SeedData::productImages, SeedData.Image::id),
                mergeBy(datasets, SeedData::attributeDefinitions, SeedData.Attribute::key),
                mergeBy(datasets, SeedData::categoryAttributes,
                        link -> link.categorySeoName() + "\u0000" + link.attributeKey()));
    }

    private static <T, K> List<T> mergeBy(List<SeedData> datasets,
                                          Function<SeedData, List<T>> values,
                                          Function<T, K> key) {
        Map<K, T> merged = new LinkedHashMap<>();
        for (SeedData dataset : datasets) {
            for (T value : values.apply(dataset)) merged.putIfAbsent(key.apply(value), value);
        }
        return List.copyOf(merged.values());
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Media(UUID id, String storageKey, String originalName, String mimeType, String publicUrl) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Brand(UUID id, String name, String seoName, String description, UUID imageFileId, CatalogStatus status) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Category(UUID id, String name, String seoName, String description, UUID parentId,
                    String hardwareType, int displayOrder, CatalogStatus status) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Product(UUID id, String name, String seoName, UUID brandId, UUID categoryId,
                   Map<String, Object> specifications, String description, CatalogStatus status, UUID createdBy) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Variant(UUID id, UUID productId, long price, int quantity, String sku, String model,
                   String description, int warrantyMonths, String barcode, String releaseAt,
                   CatalogStatus status, UUID imageFileId) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Image(UUID id, UUID productId, UUID imageFileId, boolean isMain) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Attribute(String key, String displayName, AttributeDataType dataType, String unit,
                     List<String> allowedValues, boolean filterable, boolean comparable) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record CategoryAttribute(String categorySeoName, String attributeKey, boolean required, int displayOrder) {}
}

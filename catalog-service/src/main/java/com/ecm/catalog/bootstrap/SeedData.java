package com.ecm.catalog.bootstrap;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ecm.catalog.entity.AttributeDataType;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.HardwareType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record SeedData(List<Media> files, List<Brand> brands, List<Category> categories,
                List<Product> products, List<Variant> productVariants, List<Image> productImages,
                List<Attribute> attributeDefinitions, List<CategoryAttribute> categoryAttributes) {
    static final ObjectMapper JSON = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    static SeedData load(String seedFile) throws IOException {
        try (InputStream input = seedFile == null
                ? SeedData.class.getResourceAsStream("/seed/catalog_seed.json")
                : Files.newInputStream(Path.of(seedFile))) {
            if (input == null) throw new IOException("Missing catalog seed resource");
            return JSON.readValue(input, SeedData.class);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Media(UUID id, String storageKey, String originalName, String mimeType, String publicUrl) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Brand(UUID id, String name, String seoName, String description, UUID imageFileId, CatalogStatus status) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Category(UUID id, String name, String seoName, String description, HardwareType hardwareType, CatalogStatus status) {}
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

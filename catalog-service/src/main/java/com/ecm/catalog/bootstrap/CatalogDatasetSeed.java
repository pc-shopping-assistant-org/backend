package com.ecm.catalog.bootstrap;

import java.sql.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static com.ecm.catalog.bootstrap.SeedDatabase.reference;

final class CatalogDatasetSeed {
    private static final String STORAGE_CATEGORY = "storage";
    private static final String STORAGE_CATEGORY_NAME = "Ổ cứng";
    private static final String STORAGE_HARDWARE_TYPE = "STORAGE";
    private final SeedDatabase catalog;

    CatalogDatasetSeed(SeedDatabase catalog) { this.catalog = catalog; }

    void insert(SeedData data, UUID employeeId, Map<UUID, UUID> mediaIds) throws Exception {
        insert(data, employeeId, mediaIds, prepare(data));
    }

    record Prepared(Map<UUID, UUID> categories, Map<UUID, Map<String, Object>> specifications) {}

    Prepared prepare(SeedData data) throws Exception {
        Set<UUID> sourceFiles = data.files().stream().map(SeedData.Media::id).collect(java.util.stream.Collectors.toSet());
        for (var brand : data.brands()) requireFile(sourceFiles, brand.imageFileId());
        for (var variant : data.productVariants()) requireFile(sourceFiles, variant.imageFileId());
        for (var image : data.productImages()) requireFile(sourceFiles, image.imageFileId());
        // 1. Reference taxonomy and template must exist before product validation.
        CatalogSeed.seed(catalog.connection);
        var resolvedCategories = categories(data.categories());
        Map<UUID, UUID> categories = resolvedCategories.ids();
        attributes(data, resolvedCategories.slugs());
        var specifications = new SeedSpecifications(catalog);
        var normalized = new HashMap<UUID, Map<String, Object>>();
        var existingProducts = catalog.query("SELECT id, seo_name FROM products");
        var products = SeedDatabase.ids(data.products(), SeedData.Product::id, SeedData.Product::seoName,
                existingProducts, row -> (String) row.get("seo_name"));
        var storedProducts = existingProducts.stream().map(row -> row.get("id")).collect(java.util.stream.Collectors.toSet());
        for (var product : data.products()) {
            if (storedProducts.contains(reference(products, product.id()))) continue;
            try { normalized.put(product.id(), specifications.normalize(reference(categories, product.categoryId()), product.specifications())); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException(product.name() + ": " + invalid.getMessage(), invalid); }
        }
        return new Prepared(categories, normalized);
    }

    void insert(SeedData data, UUID employeeId, Map<UUID, UUID> mediaIds, Prepared prepared) throws Exception {
        Map<UUID, UUID> categories = prepared.categories();
        // 2. Map natural keys to existing IDs without modifying existing rows.
        var existingBrands = catalog.query("SELECT id, seo_name FROM brands");
        var brands = SeedDatabase.ids(data.brands(), SeedData.Brand::id, SeedData.Brand::seoName,
                existingBrands, row -> (String) row.get("seo_name"));
        report("brands", catalog.insert("""
                INSERT INTO brands (id, name, seo_name, description, image_file_id, status)
                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, data.brands().stream().map(brand -> new Object[] {reference(brands, brand.id()), brand.name(),
                brand.seoName(), brand.description(), mediaIds.get(brand.imageFileId()), brand.status()}).toList()));
        catalog.requireIds("brands", brands.values());

        var existingProducts = catalog.query("SELECT id, seo_name FROM products");
        var products = SeedDatabase.ids(data.products(), SeedData.Product::id, SeedData.Product::seoName,
                existingProducts, row -> (String) row.get("seo_name"));
        var storedProducts = existingProducts.stream().map(row -> row.get("id")).collect(java.util.stream.Collectors.toSet());
        var productRows = new java.util.ArrayList<Object[]>();
        for (var product : data.products()) {
            UUID id = reference(products, product.id());
            if (storedProducts.contains(id)) continue;
            Map<String, Object> normalized = java.util.Objects.requireNonNull(prepared.specifications().get(product.id()), product.name());
            productRows.add(new Object[] {id, product.name(), product.seoName(), reference(brands, product.brandId()),
                    reference(categories, product.categoryId()), SeedData.JSON.writeValueAsString(normalized),
                    product.description(), product.status(), employeeId});
        }
        report("products", catalog.insert("""
                INSERT INTO products (id, name, seo_name, brand_id, category_id, specifications, description, status, created_by)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?) ON CONFLICT DO NOTHING
                """, productRows));
        catalog.requireIds("products", products.values());

        // 3. Inventory and lifecycle come from the dataset only for newly inserted variants.
        var variants = SeedDatabase.ids(data.productVariants(), SeedData.Variant::id, SeedData.Variant::sku,
                catalog.query("SELECT id, sku FROM product_variants"), row -> (String) row.get("sku"));
        report("variants", catalog.insert("""
                INSERT INTO product_variants (id, product_id, price, quantity, sku, model, description,
                    warranty_months, barcode, release_at, status, created_by, image_file_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, data.productVariants().stream().map(variant -> new Object[] {reference(variants, variant.id()),
                reference(products, variant.productId()), variant.price(), variant.quantity(), variant.sku(), variant.model(),
                variant.description(), variant.warrantyMonths(), variant.barcode(),
                variant.releaseAt() == null ? null : Date.valueOf(variant.releaseAt()), variant.status(), employeeId,
                mediaIds.get(variant.imageFileId())}).toList()));
        catalog.requireIds("product_variants", variants.values());

        // 4. Existing galleries keep their main image; missing seed images can still be added.
        var existingImages = catalog.query("SELECT id, product_id, image_file_id, is_main FROM product_images");
        var availableImages = data.productImages().stream().filter(image -> mediaIds.containsKey(image.imageFileId())).toList();
        var images = SeedDatabase.ids(availableImages, SeedData.Image::id,
                image -> reference(products, image.productId()) + "/" + reference(mediaIds, image.imageFileId()),
                existingImages, row -> row.get("product_id") + "/" + row.get("image_file_id"));
        var withMain = new HashSet<UUID>();
        existingImages.stream().filter(row -> Boolean.TRUE.equals(row.get("is_main")))
                .forEach(row -> withMain.add((UUID) row.get("product_id")));
        report("images", catalog.insert("""
                INSERT INTO product_images (id, product_id, image_file_id, is_main)
                VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, availableImages.stream().map(image -> new Object[] {reference(images, image.id()),
                reference(products, image.productId()), reference(mediaIds, image.imageFileId()),
                image.isMain() && !withMain.contains(reference(products, image.productId()))}).toList()));
        catalog.requireIds("product_images", images.values());
    }

    private record Categories(Map<UUID, UUID> ids, Map<String, UUID> slugs) {}

    private static void requireFile(Set<UUID> sourceFiles, UUID fileId) {
        if (fileId != null && !sourceFiles.contains(fileId)) throw new IllegalArgumentException("Unknown seed image: " + fileId);
    }

    private Categories categories(List<SeedData.Category> seeds) throws Exception {
        var stored = categoryIds();
        var hardwareTypes = hardwareTypeIds();
        var byName = new HashMap<String, UUID>();
        for (var row : catalog.query("SELECT id, name FROM categories ORDER BY (status <> 'DELETED'), created_at")) {
            byName.put(((String) row.get("name")).toLowerCase(Locale.ROOT), (UUID) row.get("id"));
        }
        UUID storageId = stored.getOrDefault(STORAGE_CATEGORY, byName.get(STORAGE_CATEGORY_NAME.toLowerCase(Locale.ROOT)));
        if (storageId != null) stored.put(STORAGE_CATEGORY, storageId);
        var mapped = new HashMap<UUID, UUID>();
        for (var category : seeds) {
            if (!hardwareTypes.containsKey(category.hardwareType())) {
                throw new IllegalArgumentException("Unknown hardware type: " + category.hardwareType());
            }
            if (!stored.containsKey(category.seoName())) {
                UUID named = byName.get(category.name().toLowerCase(Locale.ROOT));
                if (named != null) stored.put(category.seoName(), named);
            }
            if (!stored.containsKey(category.seoName())) {
                UUID parentId = category.parentId() == null ? null : mapped.get(category.parentId());
                if (category.parentId() != null && parentId == null) {
                    throw new IllegalArgumentException("Unknown parent category: " + category.seoName());
                }
                if (STORAGE_HARDWARE_TYPE.equals(category.hardwareType())) parentId = storageId;
                if (STORAGE_HARDWARE_TYPE.equals(category.hardwareType()) && parentId == null) {
                    throw new IllegalArgumentException("Missing storage parent category: " + category.seoName());
                }
                catalog.insert("""
                        INSERT INTO categories (id, name, seo_name, description, parent_id, hardware_type_id, display_order, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                        """, java.util.Collections.singletonList(new Object[] {category.id(), category.name(), category.seoName(),
                        category.description(), parentId, hardwareTypes.get(category.hardwareType()),
                        category.displayOrder(), category.status()}));
                stored.putAll(categoryIds());
            }
            UUID id = stored.get(category.seoName());
            if (id == null) throw new IllegalStateException("Category is missing or deleted: " + category.seoName());
            mapped.put(category.id(), id);
        }
        return new Categories(mapped, stored);
    }

    private Map<String, UUID> categoryIds() throws Exception {
        var ids = new HashMap<String, UUID>();
        for (var row : catalog.query("SELECT id, seo_name FROM categories ORDER BY (status <> 'DELETED'), created_at")) {
            ids.put((String) row.get("seo_name"), (UUID) row.get("id"));
        }
        return ids;
    }

    private Map<String, UUID> hardwareTypeIds() throws Exception {
        var ids = new HashMap<String, UUID>();
        for (var row : catalog.query("SELECT id, code FROM hardware_types WHERE status <> 'DELETED'")) {
            ids.put((String) row.get("code"), (UUID) row.get("id"));
        }
        return ids;
    }

    private void attributes(SeedData data, Map<String, UUID> categories) throws Exception {
        var rows = new java.util.ArrayList<Object[]>();
        for (var attribute : data.attributeDefinitions()) rows.add(new Object[] {attribute.key(), attribute.displayName(),
                attribute.dataType(), attribute.unit(), attribute.allowedValues() == null ? null : SeedData.JSON.writeValueAsString(attribute.allowedValues()),
                attribute.filterable(), attribute.comparable()});
        catalog.insert("""
                INSERT INTO attribute_definitions (key, display_name, data_type, unit, allowed_values, filterable, comparable)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?) ON CONFLICT DO NOTHING
                """, rows);
        var definitions = catalog.query("SELECT id, key, data_type FROM attribute_definitions").stream()
                .collect(java.util.stream.Collectors.toMap(row -> (String) row.get("key"), Function.identity()));
        catalog.insert("""
                INSERT INTO category_attributes (category_id, attribute_id, required, display_order)
                VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, data.categoryAttributes().stream().map(link -> new Object[] {
                java.util.Objects.requireNonNull(categories.get(link.categorySeoName()), link.categorySeoName()),
                java.util.Objects.requireNonNull(definitions.get(link.attributeKey()), link.attributeKey()).get("id"),
                link.required(), link.displayOrder()}).toList());
    }

    private static void report(String table, int inserted) { System.out.println(table + ": inserted " + inserted); }
}

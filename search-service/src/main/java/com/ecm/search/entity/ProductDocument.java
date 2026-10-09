package com.ecm.search.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.Instant;

/**
 * A product as the search sees it, flattened from the catalog: only products the storefront shows are indexed.
 * Text fields are accent-folded so "card do hoa" finds "Card đồ họa"; the name also has an edge-ngram field so a
 * half-typed word ("rtx 40") finds "RTX 4070".
 */
@Document(indexName = "products")
@Setting(settingPath = "elasticsearch/product-settings.json")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDocument {

    public static final String FIELD_BRAND_ID = "brandId";
    public static final String FIELD_CATEGORY_ID = "categoryId";

    @Id
    private String id;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "folded"),
            otherFields = @InnerField(suffix = "prefix", type = FieldType.Text, analyzer = "folded_prefix", searchAnalyzer = "folded"))
    private String name;

    @Field(type = FieldType.Keyword, index = false)
    private String seoName;

    @Field(type = FieldType.Keyword)
    private String brandId;

    @Field(type = FieldType.Text, analyzer = "folded")
    private String brandName;

    @Field(type = FieldType.Keyword)
    private String categoryId;

    @Field(type = FieldType.Text, analyzer = "folded")
    private String categoryName;

    @Field(type = FieldType.Text, analyzer = "folded")
    private String description;

    @Field(type = FieldType.Long)
    private Long minPrice;

    @Field(type = FieldType.Long)
    private Long maxPrice;

    @Field(type = FieldType.Boolean)
    private boolean inStock;

    @Field(type = FieldType.Keyword, index = false)
    private String mainImageUrl;

    @Field(type = FieldType.Date)
    private Instant indexedAt;
}

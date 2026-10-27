package com.ecm.catalog.helper;

import com.ecm.catalog.dto.response.ProductDetailResponse;
import com.ecm.catalog.dto.response.ProductVariantResponse;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Category;
import com.ecm.catalog.entity.HardwareType;
import com.ecm.catalog.entity.Product;
import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.helper.MediaFileResolver;
import com.ecm.catalog.mapper.OptionMapper;
import com.ecm.catalog.mapper.ProductMapper;
import com.ecm.catalog.mapper.ProductVariantMapper;
import com.ecm.catalog.repository.BrandRepository;
import com.ecm.catalog.repository.CategoryRepository;
import com.ecm.catalog.repository.OptionRepository;
import com.ecm.catalog.repository.ProductImageRepository;
import com.ecm.catalog.repository.ProductVariantRepository;
import com.ecm.catalog.repository.VariantOptionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSnapshotAssemblerTest {
    @Test
    void sixteenProductsKeepVariantIdentityAndPriceWithOneBatchPerAssociation() {
        BrandRepository brands = mock(BrandRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        ProductImageRepository images = mock(ProductImageRepository.class);
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        VariantOptionRepository links = mock(VariantOptionRepository.class);
        OptionRepository options = mock(OptionRepository.class);
        ProductMapper productMapper = mock(ProductMapper.class);
        ProductVariantMapper variantMapper = mock(ProductVariantMapper.class);
        MediaFileResolver media = mock(MediaFileResolver.class);
        ProductAssembler assembler = new ProductAssembler(brands, categories, images, variants, links, options,
                productMapper, variantMapper, mock(OptionMapper.class), media);
        UUID categoryId = UUID.randomUUID();
        when(categories.findAllById(anyCollection())).thenReturn(List.of(Category.builder()
                .id(categoryId).name("Renamed processors").hardwareType(HardwareType.CPU)
                .status(CatalogStatus.ACTIVE).build()));
        List<Product> products = IntStream.range(0, 16).mapToObj(index -> Product.builder()
                .id(UUID.randomUUID()).name("P" + index).categoryId(categoryId).status(CatalogStatus.ACTIVE).build()).toList();
        List<UUID> ids = products.stream().map(Product::getId).toList();
        List<ProductVariant> rows = IntStream.range(0, products.size()).mapToObj(index -> ProductVariant.builder()
                .id(UUID.randomUUID()).productId(ids.get(index)).sku("SKU-" + index).price(1_000_000L + index)
                .quantity(index).status(CatalogStatus.ACTIVE).build()).toList();
        when(variants.findByProductIdInAndStatusIn(ids, List.of(CatalogStatus.ACTIVE))).thenReturn(rows);
        when(images.findByProductIdIn(ids)).thenReturn(List.of());
        when(links.findByProductVariantIdIn(rows.stream().map(ProductVariant::getId).toList())).thenReturn(List.of());
        when(media.resolveUrls(anyCollection())).thenReturn(Map.of());
        when(productMapper.toDetailResponse(any())).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            return ProductDetailResponse.builder().id(product.getId()).name(product.getName()).build();
        });
        when(variantMapper.toResponse(any())).thenAnswer(invocation -> {
            ProductVariant variant = invocation.getArgument(0);
            return ProductVariantResponse.builder().id(variant.getId()).productId(variant.getProductId())
                    .sku(variant.getSku()).price(variant.getPrice()).quantity(variant.getQuantity()).build();
        });

        List<ProductDetailResponse> result = assembler.details(products, List.of(CatalogStatus.ACTIVE));

        assertEquals(ids, result.stream().map(ProductDetailResponse::getId).toList());
        for (int index = 0; index < result.size(); index++) {
            assertEquals(HardwareType.CPU, result.get(index).getHardwareType());
            ProductVariantResponse variant = result.get(index).getVariants().get(0);
            assertEquals(rows.get(index).getId(), variant.getId());
            assertEquals(rows.get(index).getPrice(), variant.getPrice());
            assertEquals(rows.get(index).getQuantity(), variant.getQuantity());
        }
        verify(variants).findByProductIdInAndStatusIn(ids, List.of(CatalogStatus.ACTIVE));
        verify(variants, never()).findByProductIdAndStatusIn(any(), anyCollection());
        verify(images).findByProductIdIn(ids);
        verify(categories).findAllById(anyCollection());
        verify(links).findByProductVariantIdIn(rows.stream().map(ProductVariant::getId).toList());
        verify(media).resolveUrls(anyCollection());
    }
}

package com.ecm.catalog.service;

import com.ecm.catalog.dto.response.CartVariantDetailsResponse;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Product;
import com.ecm.catalog.entity.ProductImage;
import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.entity.Option;
import com.ecm.catalog.entity.VariantOption;
import com.ecm.catalog.repository.OptionRepository;
import com.ecm.catalog.repository.ProductImageRepository;
import com.ecm.catalog.repository.ProductRepository;
import com.ecm.catalog.repository.ProductVariantRepository;
import com.ecm.catalog.repository.VariantOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartVariantDetailsServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID VARIANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID VARIANT_FILE = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID PRODUCT_FILE = UUID.fromString("00000000-0000-0000-0000-0000000000f2");

    private ProductVariantRepository variantRepository;
    private ProductRepository productRepository;
    private ProductImageRepository imageRepository;
    private VariantOptionRepository variantOptionRepository;
    private OptionRepository optionRepository;
    private ProductMediaResolver mediaResolver;
    private CartVariantDetailsService service;

    @BeforeEach
    void setUp() {
        variantRepository = mock(ProductVariantRepository.class);
        productRepository = mock(ProductRepository.class);
        imageRepository = mock(ProductImageRepository.class);
        variantOptionRepository = mock(VariantOptionRepository.class);
        optionRepository = mock(OptionRepository.class);
        mediaResolver = mock(ProductMediaResolver.class);
        service = new CartVariantDetailsService(variantRepository, productRepository, imageRepository, variantOptionRepository, optionRepository, mediaResolver);
        when(mediaResolver.resolveUrls(anyCollection())).thenReturn(Map.of(VARIANT_FILE, "https://img/variant.png", PRODUCT_FILE, "https://img/product.png"));
        when(imageRepository.findMainByProductIdIn(anyCollection()))
                .thenReturn(List.of(ProductImage.builder().productId(PRODUCT_ID).imageFileId(PRODUCT_FILE).isMain(true).build()));
    }

    private void stub(CatalogStatus productStatus, CatalogStatus variantStatus, UUID imageFileId) {
        when(productRepository.findAllById(anyCollection()))
                .thenReturn(List.of(Product.builder().id(PRODUCT_ID).name("Kit").categoryId(CATEGORY_ID).status(productStatus).build()));
        when(variantRepository.findAllById(anyCollection())).thenReturn(List.of(ProductVariant.builder().id(VARIANT_ID)
                .productId(PRODUCT_ID).sku("SKU").price(1000L).quantity(4).status(variantStatus).imageFileId(imageFileId).build()));
    }

    @Test
    void aVariantIsSellableOnlyWhenItAndItsProductAreActive() {
        stub(CatalogStatus.ACTIVE, CatalogStatus.ACTIVE, null);
        assertTrue(service.getDetails(List.of(VARIANT_ID)).getFirst().sellable());

        stub(CatalogStatus.INACTIVE, CatalogStatus.ACTIVE, null);
        assertFalse(service.getDetails(List.of(VARIANT_ID)).getFirst().sellable());

        stub(CatalogStatus.ACTIVE, CatalogStatus.INACTIVE, null);
        assertFalse(service.getDetails(List.of(VARIANT_ID)).getFirst().sellable());

        stub(CatalogStatus.ACTIVE, CatalogStatus.DELETED, null);
        assertFalse(service.getDetails(List.of(VARIANT_ID)).getFirst().sellable());
    }

    @Test
    void theVariantImageWinsAndTheProductMainImageIsTheFallback() {
        stub(CatalogStatus.ACTIVE, CatalogStatus.ACTIVE, VARIANT_FILE);
        assertEquals("https://img/variant.png", service.getDetails(List.of(VARIANT_ID)).getFirst().mainImageUrl());

        stub(CatalogStatus.ACTIVE, CatalogStatus.ACTIVE, null);
        CartVariantDetailsResponse fallback = service.getDetails(List.of(VARIANT_ID)).getFirst();
        assertEquals("https://img/product.png", fallback.mainImageUrl());
        assertEquals(1000L, fallback.price());
        assertEquals(4, fallback.quantity());
    }

    @Test
    void theVariantLabelJoinsTheOptionsOrderedByName() {
        UUID storage = UUID.randomUUID();
        UUID color = UUID.randomUUID();
        stub(CatalogStatus.ACTIVE, CatalogStatus.ACTIVE, null);
        when(variantOptionRepository.findByProductVariantIdIn(anyCollection())).thenReturn(List.of(
                VariantOption.builder().productVariantId(VARIANT_ID).optionId(storage).build(),
                VariantOption.builder().productVariantId(VARIANT_ID).optionId(color).build()));
        when(optionRepository.findAllById(anyCollection())).thenReturn(List.of(
                Option.builder().id(storage).name("Storage").value("256GB").build(),
                Option.builder().id(color).name("Color").value("Blue").build()));

        CartVariantDetailsResponse details = service.getDetails(List.of(VARIANT_ID)).getFirst();

        assertEquals("Color: Blue, Storage: 256GB", details.variantLabel());
        assertEquals(CATEGORY_ID, details.categoryId());
    }

    @Test
    void nullAndEmptyIdListsReturnNothingWithoutQuerying() {
        assertTrue(service.getDetails(null).isEmpty());
        assertTrue(service.getDetails(List.of()).isEmpty());
        verify(variantRepository, never()).findAllById(anyCollection());
    }
}

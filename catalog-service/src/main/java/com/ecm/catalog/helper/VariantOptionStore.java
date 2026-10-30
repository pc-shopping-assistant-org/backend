package com.ecm.catalog.helper;

import com.ecm.catalog.dto.request.VariantOptionRequest;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Option;
import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.entity.VariantOption;
import com.ecm.catalog.repository.OptionRepository;
import com.ecm.catalog.repository.ProductVariantRepository;
import com.ecm.catalog.repository.VariantOptionRepository;
import com.ecm.catalog.util.VariantOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reads and writes the options of variants; an option is a (name, value) row shared by every variant that uses it. */
@Component
@RequiredArgsConstructor
public class VariantOptionStore {

    private static final List<CatalogStatus> ACTIVE = List.of(CatalogStatus.ACTIVE);

    private final OptionRepository optionRepository;
    private final VariantOptionRepository variantOptionRepository;
    private final ProductVariantRepository productVariantRepository;

    /** Replaces the options of a variant. It clears the persistence context, so save the variant itself first. */
    public void replace(UUID variantId, List<VariantOptionRequest> options) {
        variantOptionRepository.deleteByProductVariantId(variantId);
        if (options == null || options.isEmpty()) {
            return;
        }
        variantOptionRepository.saveAll(options.stream()
                .map(option -> VariantOption.builder().productVariantId(variantId).optionId(findOrCreate(option).getId()).build())
                .toList());
    }

    /** The combination of each variant (see {@link VariantOptions}); a variant without options gets an empty one. */
    public Map<UUID, Map<String, String>> combinations(Collection<UUID> variantIds) {
        Map<UUID, Map<String, String>> combinations = new HashMap<>();
        variantIds.forEach(id -> combinations.put(id, new HashMap<>()));
        if (variantIds.isEmpty()) {
            return combinations;
        }
        List<VariantOption> links = variantOptionRepository.findByProductVariantIdIn(variantIds);
        Map<UUID, Option> options = optionRepository.findAllById(links.stream().map(VariantOption::getOptionId).distinct().toList())
                .stream().collect(Collectors.toMap(Option::getId, Function.identity()));
        links.stream().filter(link -> options.containsKey(link.getOptionId())).forEach(link -> {
            Option option = options.get(link.getOptionId());
            combinations.get(link.getProductVariantId())
                    .put(VariantOptions.normalized(option.getName()), VariantOptions.normalized(option.getValue()));
        });
        return combinations;
    }

    /** The combinations of the ACTIVE variants of a product, leaving out the variant being saved (null leaves none out). */
    public List<Map<String, String>> activeCombinations(UUID productId, UUID excludedVariantId) {
        List<UUID> ids = productVariantRepository.findByProductIdAndStatusIn(productId, ACTIVE).stream()
                .map(ProductVariant::getId)
                .filter(id -> !Objects.equals(id, excludedVariantId))
                .toList();
        Map<UUID, Map<String, String>> combinations = combinations(ids);
        return ids.stream().map(combinations::get).toList();
    }

    private Option findOrCreate(VariantOptionRequest request) {
        String name = request.name().trim();
        String value = request.value().trim();
        optionRepository.insertIfAbsent(name, value);
        return optionRepository.findByNameAndValueIgnoringCase(name, value)
                .orElseThrow(() -> new IllegalStateException("Option " + name + ": " + value + " was not saved"));
    }
}

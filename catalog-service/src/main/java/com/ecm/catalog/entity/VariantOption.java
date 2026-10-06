package com.ecm.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Link between a variant and an option. The DB enforces "one option per option name per variant" with a
 * constraint trigger (trg_variant_options_unique_type) because it depends on joining {@code options.name}.
 */
@Entity
@Table(name = "variant_options")
@IdClass(VariantOptionId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VariantOption {

    @Id
    @Column(name = "product_variant_id", nullable = false)
    private UUID productVariantId;

    @Id
    @Column(name = "option_id", nullable = false)
    private UUID optionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

package com.ecm.catalog.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class VariantOptionId implements Serializable {

    private UUID productVariantId;
    private UUID optionId;
}

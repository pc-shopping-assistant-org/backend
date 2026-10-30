package com.ecm.catalog.dto.request;

import java.util.List;
import java.util.UUID;

/** Body of the Order Service call that tells which of these variants have been ordered. */
public record OrderedVariantsRequest(List<UUID> variantIds) {}

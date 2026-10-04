package com.ecm.catalog.dto.response;

import java.util.UUID;

public record SupplierResponse(UUID id, String name, String email, String phone, String address,
                               String description, String status) {}

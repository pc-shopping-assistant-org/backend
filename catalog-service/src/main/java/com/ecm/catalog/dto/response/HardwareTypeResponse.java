package com.ecm.catalog.dto.response;

import java.util.UUID;

public record HardwareTypeResponse(UUID id, String code, String nameVi, String nameEn, int displayOrder) {}

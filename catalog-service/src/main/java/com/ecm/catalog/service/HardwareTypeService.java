package com.ecm.catalog.service;

import com.ecm.catalog.dto.response.HardwareTypeResponse;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.mapper.HardwareTypeMapper;
import com.ecm.catalog.repository.HardwareTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HardwareTypeService {
    private final HardwareTypeRepository repository;
    private final HardwareTypeMapper mapper;

    @Transactional(readOnly = true)
    public List<HardwareTypeResponse> getActiveTypes() {
        return mapper.toResponseList(repository.findByStatusOrderByDisplayOrderAscCodeAsc(CatalogStatus.ACTIVE));
    }
}

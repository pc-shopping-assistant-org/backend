package com.ecm.catalog.service;

import com.ecm.catalog.dto.request.SupplierRequest;
import com.ecm.catalog.dto.request.UpdateSupplierRequest;
import com.ecm.catalog.dto.response.SupplierResponse;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Supplier;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.repository.ProductSupplierRepository;
import com.ecm.catalog.repository.SupplierRepository;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class SupplierService {
    private final SupplierRepository supplierRepository;
    private final ProductSupplierRepository productSupplierRepository;

    public SupplierService(SupplierRepository supplierRepository, ProductSupplierRepository productSupplierRepository) {
        this.supplierRepository = supplierRepository;
        this.productSupplierRepository = productSupplierRepository;
    }

    public List<SupplierResponse> getActiveSuppliers() {
        return supplierRepository.findByStatusOrderByName(CatalogStatus.ACTIVE).stream().map(this::toResponse).toList();
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        if (supplierRepository.existsByNameIgnoreCase(request.name().trim())) throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT);
        Supplier entity = Supplier.builder().name(request.name().trim()).email(request.email()).phone(request.phone())
                .address(request.address()).description(request.description()).status(CatalogStatus.ACTIVE).build();
        return toResponse(supplierRepository.save(entity));
    }

    @Transactional
    public SupplierResponse update(UUID id, UpdateSupplierRequest request) {
        Supplier entity = supplierRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
        if (entity.getStatus() == CatalogStatus.DELETED) throw new BusinessException(CatalogErrorCode.INVALID_CATALOG_REFERENCE);
        if (!entity.getName().equalsIgnoreCase(request.name().trim()) && supplierRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT);
        }
        entity.setName(request.name().trim()); entity.setEmail(request.email()); entity.setPhone(request.phone());
        entity.setAddress(request.address()); entity.setDescription(request.description()); entity.setStatus(request.status());
        return toResponse(supplierRepository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        Supplier entity = supplierRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
        if (productSupplierRepository.existsBySupplierId(id)) throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT, "Supplier is assigned to products");
        entity.setStatus(CatalogStatus.DELETED);
        supplierRepository.save(entity);
    }

    private SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getEmail(), supplier.getPhone(), supplier.getAddress(), supplier.getDescription(), supplier.getStatus().name());
    }
}

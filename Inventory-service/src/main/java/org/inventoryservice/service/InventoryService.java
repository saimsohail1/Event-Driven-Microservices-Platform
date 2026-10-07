package org.inventoryservice.service;

import org.inventoryservice.dto.UpsertInventoryItemRequest;
import org.inventoryservice.entity.InventoryItem;
import org.inventoryservice.exception.UnknownProductException;
import org.inventoryservice.repository.InventoryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

    private final InventoryRepository repository;

    public InventoryService(InventoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public InventoryItem getByProductId(String productId) {
        return repository.findById(productId)
                .orElseThrow(() -> new UnknownProductException(productId));
    }

    @Transactional(readOnly = true)
    public List<InventoryItem> getAll() {
        return repository.findAll(Sort.by("productId"));
    }

    /**
     * Sets the stock level for a product, creating the record if it is new.
     */
    @Transactional
    public InventoryItem upsertInventoryItem(UpsertInventoryItemRequest request) {
        InventoryItem item = repository.findById(request.getProductId())
                .orElseGet(() -> {
                    InventoryItem created = new InventoryItem();
                    created.setProductId(request.getProductId());
                    return created;
                });
        item.setAvailableQuantity(request.getAvailableQuantity());
        return repository.save(item);
    }
}

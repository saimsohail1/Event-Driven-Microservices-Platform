package org.inventoryservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class UpsertInventoryItemRequest {

    @NotBlank(message = "productId is required")
    @Size(max = 255, message = "productId must be at most 255 characters")
    private String productId;

    @PositiveOrZero(message = "availableQuantity must not be negative")
    private int availableQuantity;

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
    }
}

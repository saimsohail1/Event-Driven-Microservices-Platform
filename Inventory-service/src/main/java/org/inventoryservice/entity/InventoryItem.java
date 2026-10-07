package org.inventoryservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "inventory")
public class InventoryItem {

    @Id
    private String productId;

    @Column(nullable = false)
    private int availableQuantity;

    /**
     * Guards against lost updates when replicas process events for the same
     * product concurrently.
     */
    @Version
    @JsonIgnore
    private Long version;

    // getters & setters

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

    public Long getVersion() {
        return version;
    }
}

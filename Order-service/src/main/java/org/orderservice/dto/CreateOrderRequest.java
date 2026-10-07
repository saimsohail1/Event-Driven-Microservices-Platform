package org.orderservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CreateOrderRequest {

    @NotBlank(message = "productId is required")
    @Size(max = 255, message = "productId must be at most 255 characters")
    private String productId;

    @Positive(message = "quantity must be greater than zero")
    private int quantity;

    @NotNull(message = "price is required")
    @DecimalMin(value = "0.00", message = "price must not be negative")
    @Digits(integer = 17, fraction = 2, message = "price supports at most 2 decimal places")
    private BigDecimal price;

    // getters & setters

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}

package com.msd.smartcart.infrastructure.adapter.in.http.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddItemRequest {

    @NotBlank(message = "productId is required")
    private String productId;

    @Min(value = 1, message = "quantity must be greater than 0")
    private int quantity;
}
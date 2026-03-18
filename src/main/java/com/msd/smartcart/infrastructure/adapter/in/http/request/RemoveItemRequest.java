package com.msd.smartcart.infrastructure.adapter.in.http.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class RemoveItemRequest {

    @Min(value = 1, message = "quantity must be greater than 0")
    private int quantity;
}
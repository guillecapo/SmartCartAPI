package com.msd.smartcart.infrastructure.adapter.out.mongodb.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartHashProjection {
    private String itemsHash;
}
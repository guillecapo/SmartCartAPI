package com.msd.smartcart.domain.port.out;

import com.msd.smartcart.domain.model.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Optional<Product> findById(String productId);

    List<Product> findAllByIds(List<String> productIds);
}
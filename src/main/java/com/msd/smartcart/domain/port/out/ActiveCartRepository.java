package com.msd.smartcart.domain.port.out;

import com.msd.smartcart.domain.model.Cart;

import java.util.Optional;

public interface ActiveCartRepository {

    void save(Cart cart);

    Optional<Cart> findByUserId(String userId);

    void deleteByUserId(String userId);
}
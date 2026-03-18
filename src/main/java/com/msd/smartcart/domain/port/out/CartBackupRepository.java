package com.msd.smartcart.domain.port.out;

import com.msd.smartcart.domain.model.Cart;

import java.util.Optional;

public interface CartBackupRepository {

    void saveOrUpdate(Cart cart);

    Optional<Cart> findLatestByUserId(String userId);

    void deleteByUserId(String userId);
}
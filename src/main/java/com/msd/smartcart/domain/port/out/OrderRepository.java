package com.msd.smartcart.domain.port.out;

import com.msd.smartcart.domain.model.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    void save(Order order);

    Optional<Order> findById(String orderId);

    List<Order> findAllByUserId(String userId);
}
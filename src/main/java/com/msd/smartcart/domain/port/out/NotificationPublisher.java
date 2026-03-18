package com.msd.smartcart.domain.port.out;

import com.msd.smartcart.domain.model.OrderConfirmedEvent;

public interface NotificationPublisher {

    void publish(OrderConfirmedEvent event);
}
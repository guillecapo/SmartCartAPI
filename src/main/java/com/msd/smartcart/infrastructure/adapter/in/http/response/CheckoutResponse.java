package com.msd.smartcart.infrastructure.adapter.in.http.response;

import com.msd.smartcart.domain.model.Order;
import com.msd.smartcart.domain.model.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutResponse {

    private String orderId;
    private String cartId;
    private String userId;
    private List<OrderItemResponse> items;
    private BigDecimal totalAmount;
    private Instant confirmedAt;

    public static CheckoutResponse from(Order order) {
        return new CheckoutResponse(
                order.orderId(),
                order.cartId(),
                order.userId(),
                order.items().stream()
                        .map(OrderItemResponse::from)
                        .toList(),
                order.totalAmount(),
                order.confirmedAt()
        );
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private String productId;
        private String productName;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;

        public static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(
                    item.productId(),
                    item.productName(),
                    item.quantity(),
                    item.unitPrice(),
                    item.subtotal()
            );
        }
    }
}
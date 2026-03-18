package com.msd.smartcart.domain.port.in;

import com.msd.smartcart.domain.model.Cart;
import com.msd.smartcart.shared.AppError;
import com.msd.smartcart.shared.Result;

public interface CartUseCase {

    Result<Cart, AppError> addItem(String userId, String productId, int quantity);

    Result<Cart, AppError> removeItem(String userId, String productId, int quantity);

    Result<Cart, AppError> getCart(String userId);

    Result<Void, AppError> clearCart(String userId);
}
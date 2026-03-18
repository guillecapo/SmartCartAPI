package com.msd.smartcart.domain.port.in;

import com.msd.smartcart.domain.model.CheckoutResult;
import com.msd.smartcart.shared.AppError;
import com.msd.smartcart.shared.Result;

public interface CheckoutUseCase {

    Result<CheckoutResult, AppError> checkout(String userId);
}
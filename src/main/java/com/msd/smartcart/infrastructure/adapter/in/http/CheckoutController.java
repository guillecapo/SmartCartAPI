package com.msd.smartcart.infrastructure.adapter.in.http;

import com.msd.smartcart.domain.model.CheckoutResult;
import com.msd.smartcart.domain.port.in.CheckoutUseCase;
import com.msd.smartcart.infrastructure.adapter.in.http.response.CheckoutResponse;
import com.msd.smartcart.shared.annotation.WebAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Slf4j
@WebAdapter
@RestController
@RequestMapping("/v1/checkout")
@RequiredArgsConstructor
@Tag(name = "Checkout", description = "Process cart checkout and generate orders")
@SecurityRequirement(name = "Bearer JWT")
public class CheckoutController {

    private final CheckoutUseCase checkoutUseCase;
    private final IdempotencyStore idempotencyStore;

    @Operation(
            summary = "Checkout",
            description = """
                    Processes the active cart and generates an order.
                    
                    **Flow:**
                    1. Validates cart is not empty
                    2. Validates stock for all items simultaneously
                    3. Auto-corrects cart by removing out-of-stock items
                    4. Persists order in MongoDB
                    5. Publishes `OrderConfirmedEvent` to RabbitMQ (best-effort)
                    
                    **Possible outcomes:**
                    - `200 OK` — Order confirmed
                    - `400 Bad Request` — Empty cart or domain error
                    - `422 Unprocessable Entity` — Some items were out of stock and removed. Retry to confirm with remaining items.
                    - `424 Failed Dependency` — Infrastructure failure, retry later
                    
                    **Idempotency:** Provide a unique `Idempotency-Key` header per operation.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Order confirmed",
            content = @Content(schema = @Schema(implementation = CheckoutResponse.class)))
    @ApiResponse(responseCode = "400", description = "Empty cart or domain validation error")
    @ApiResponse(responseCode = "422", description = "Out of stock items removed — retry to confirm with remaining items")
    @ApiResponse(responseCode = "424", description = "Infrastructure failure — retry later")
    @ApiResponse(responseCode = "401", description = "Unauthorized — invalid or missing JWT")
    @PostMapping
    public ResponseEntity<?> checkout(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Unique key per operation for idempotency", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        String userId = userDetails.getUsername();

        CheckoutResponse cached = idempotencyStore.get(idempotencyKey, CheckoutResponse.class);
        if (cached != null) {
            log.info("Idempotent checkout detected [userId={}, key={}]", userId, idempotencyKey);
            return ResponseEntity.ok(cached);
        }

        var result = checkoutUseCase.checkout(userId);

        if (result.isFailure()) {
            log.warn("Checkout failed [userId={}, error={}]", userId, result.getError());
            return ResponseEntity.badRequest().body(result.getError());
        }

        return switch (result.getValue()) {

            case CheckoutResult.Confirmed confirmed -> {
                CheckoutResponse response = CheckoutResponse.from(confirmed.order());
                idempotencyStore.save(idempotencyKey, response);
                log.info("Checkout confirmed [userId={}, orderId={}]",
                        userId, confirmed.order().orderId());
                yield ResponseEntity.ok(response);
            }

            case CheckoutResult.OutOfStock outOfStock -> {
                log.warn("Checkout out of stock [userId={}, products={}]",
                        userId, outOfStock.products());
                yield ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(outOfStock.products());
            }

            case CheckoutResult.Rejected rejected -> {
                log.warn("Checkout rejected [userId={}, reason={}]",
                        userId, rejected.reason());
                yield ResponseEntity.badRequest()
                        .body(rejected.reason());
            }

            case CheckoutResult.InfrastructureFailure failure -> {
                log.error("Checkout infrastructure failure [userId={}, cause={}]",
                        userId, failure.cause());
                yield ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY)
                        .body("Service temporarily unavailable");
            }
        };
    }
}
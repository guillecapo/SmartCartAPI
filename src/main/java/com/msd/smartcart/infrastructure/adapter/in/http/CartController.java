package com.msd.smartcart.infrastructure.adapter.in.http;

import com.msd.smartcart.domain.model.Cart;
import com.msd.smartcart.domain.port.in.CartUseCase;
import com.msd.smartcart.infrastructure.adapter.in.http.request.AddItemRequest;
import com.msd.smartcart.infrastructure.adapter.in.http.request.RemoveItemRequest;
import com.msd.smartcart.infrastructure.adapter.in.http.response.CartResponse;
import com.msd.smartcart.shared.Result;
import com.msd.smartcart.shared.annotation.WebAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Slf4j
@WebAdapter
@RestController
@RequestMapping("/v1/carts")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Manage the active shopping cart for the authenticated user")
@SecurityRequirement(name = "Bearer JWT")
public class CartController {

    private final CartUseCase cartUseCase;
    private final IdempotencyStore idempotencyStore;

    @Operation(
            summary = "Get active cart",
            description = "Returns the current active cart for the authenticated user. Returns 404 if no active cart exists."
    )
    @ApiResponse(responseCode = "200", description = "Cart found",
            content = @Content(schema = @Schema(implementation = CartResponse.class)))
    @ApiResponse(responseCode = "404", description = "No active cart found")
    @ApiResponse(responseCode = "401", description = "Unauthorized — invalid or missing JWT")
    @GetMapping
    public ResponseEntity<CartResponse> getCart(
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userDetails.getUsername();
        return cartUseCase.getCart(userId)
                .fold(
                        cart -> ResponseEntity.ok(CartResponse.from(cart)),
                        error -> ResponseEntity.status(404).build()
                );
    }

    @Operation(
            summary = "Add item to cart",
            description = """
                    Adds a product to the active cart. Creates a new cart if none exists.
                    If the product already exists in the cart, the quantity is incremented.
                    
                    **Idempotency:** Provide a unique `Idempotency-Key` header per operation.
                    Retrying with the same key returns the cached response without re-executing.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Item added successfully",
            content = @Content(schema = @Schema(implementation = CartResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request — product not found or invalid quantity")
    @ApiResponse(responseCode = "401", description = "Unauthorized — invalid or missing JWT")
    @PostMapping("/products")
    public ResponseEntity<CartResponse> addItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Unique key per operation for idempotency", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AddItemRequest request) {

        String userId = userDetails.getUsername();

        CartResponse cached = idempotencyStore.get(idempotencyKey, CartResponse.class);
        if (cached != null) {
            log.info("Idempotent request detected [userId={}, key={}]", userId, idempotencyKey);
            return ResponseEntity.ok(cached);
        }

        Result<Cart, ?> result = cartUseCase.addItem(userId, request.getProductId(), request.getQuantity());

        return result.fold(
                cart -> {
                    CartResponse response = CartResponse.from(cart);
                    idempotencyStore.save(idempotencyKey, response);
                    return ResponseEntity.ok(response);
                },
                error -> ResponseEntity.badRequest().build()
        );
    }

    @Operation(
            summary = "Remove item from cart",
            description = """
                    Removes a quantity of a product from the active cart.
                    If the resulting quantity is zero or less, the product is removed entirely.
                    
                    **Idempotency:** Provide a unique `Idempotency-Key` header per operation.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Item removed successfully",
            content = @Content(schema = @Schema(implementation = CartResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "404", description = "Cart not found")
    @ApiResponse(responseCode = "401", description = "Unauthorized — invalid or missing JWT")
    @DeleteMapping("/products/{productId}")
    public ResponseEntity<CartResponse> removeItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "ID of the product to remove", required = true)
            @PathVariable String productId,
            @Parameter(description = "Unique key per operation for idempotency", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RemoveItemRequest request) {

        String userId = userDetails.getUsername();

        CartResponse cached = idempotencyStore.get(idempotencyKey, CartResponse.class);
        if (cached != null) {
            log.info("Idempotent request detected [userId={}, key={}]", userId, idempotencyKey);
            return ResponseEntity.ok(cached);
        }

        Result<Cart, ?> result = cartUseCase.removeItem(userId, productId, request.getQuantity());

        return result.fold(
                cart -> {
                    CartResponse response = CartResponse.from(cart);
                    idempotencyStore.save(idempotencyKey, response);
                    return ResponseEntity.ok(response);
                },
                error -> ResponseEntity.badRequest().build()
        );
    }
}
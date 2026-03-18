package com.msd.smartcart.infrastructure.adapter.out.mongodb;

import com.msd.smartcart.domain.model.Cart;
import com.msd.smartcart.domain.model.CartItem;
import com.msd.smartcart.domain.port.out.CartBackupRepository;
import com.msd.smartcart.infrastructure.adapter.out.mongodb.document.CartBackupDocument;
import com.msd.smartcart.infrastructure.adapter.out.mongodb.document.CartHashProjection;
import com.msd.smartcart.shared.annotation.PersistenceAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.DigestUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@PersistenceAdapter
@RequiredArgsConstructor
public class CartBackupRepositoryAdapter implements CartBackupRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public void saveOrUpdate(Cart cart) {
        String incomingHash = computeHash(cart.items());

        Query query = Query.query(Criteria.where("_id").is(cart.userId()));
        query.fields().include("itemsHash");

        CartHashProjection projection = mongoTemplate.findOne(
                query, CartHashProjection.class, "saved_carts"
        );

        if (projection != null && projection.getItemsHash().equals(incomingHash)) {
            log.debug("Cart backup skipped, no changes detected [userId={}]", cart.userId());
            return;
        }

        CartBackupDocument doc = CartBackupDocument.from(cart, incomingHash);

        mongoTemplate.findAndReplace(
                Query.query(Criteria.where("_id").is(cart.userId())),
                doc,
                FindAndReplaceOptions.options().upsert(),
                CartBackupDocument.class,
                "saved_carts"
        );

        log.info("Cart backup saved [userId={}, cartId={}, totalValue={}]",
                cart.userId(), cart.cartId(), cart.totalValue());
    }

    @Override
    public Optional<Cart> findLatestByUserId(String userId) {
        CartBackupDocument doc = mongoTemplate.findById(
                userId, CartBackupDocument.class, "saved_carts"
        );
        return Optional.ofNullable(doc).map(CartBackupDocument::toDomain);
    }

    @Override
    public void deleteByUserId(String userId) {
        mongoTemplate.remove(
                Query.query(Criteria.where("_id").is(userId)),
                CartBackupDocument.class,
                "saved_carts"
        );
        log.debug("Cart backup deleted [userId={}]", userId);
    }

    private String computeHash(List<CartItem> items) {
        String normalized = items.stream()
                .sorted(Comparator.comparing(CartItem::productId))
                .map(i -> i.productId() + i.quantity())
                .collect(Collectors.joining("|"));

        return DigestUtils.md5DigestAsHex(normalized.getBytes());
    }
}
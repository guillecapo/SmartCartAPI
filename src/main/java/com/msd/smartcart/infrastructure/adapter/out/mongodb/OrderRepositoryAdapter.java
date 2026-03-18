package com.msd.smartcart.infrastructure.adapter.out.mongodb;

import com.msd.smartcart.domain.model.Order;
import com.msd.smartcart.domain.port.out.OrderRepository;
import com.msd.smartcart.infrastructure.adapter.out.mongodb.document.OrderDocument;
import com.msd.smartcart.shared.annotation.PersistenceAdapter;
import com.msd.smartcart.shared.exception.DuplicateOrderException;
import com.msd.smartcart.shared.exception.InfrastructureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Optional;

@Slf4j
@PersistenceAdapter
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepository {

    private final MongoTemplate mongoTemplate;

    // OrderRepositoryAdapter.java — infraestructura
    @Override
    public void save(Order order) {
        try {
            mongoTemplate.save(OrderDocument.from(order));
        } catch (DuplicateKeyException e) {
            log.error("Duplicate order [orderId={}]", order.orderId());
            throw new DuplicateOrderException(order.orderId()); // excepción de dominio
        } catch (DataAccessResourceFailureException e) {
            log.error("MongoDB unavailable [orderId={}]", order.orderId());
            throw new InfrastructureException("order.infra.failed");  // excepción de dominio
        }
    }

    @Override
    public Optional<Order> findById(String orderId) {
        OrderDocument doc = mongoTemplate.findById(orderId, OrderDocument.class);
        return Optional.ofNullable(doc).map(OrderDocument::toDomain);
    }

    @Override
    public List<Order> findAllByUserId(String userId) {
        List<OrderDocument> docs = mongoTemplate.find(
                Query.query(Criteria.where("userId").is(userId)),
                OrderDocument.class
        );
        return docs.stream()
                .map(OrderDocument::toDomain)
                .toList();
    }
}
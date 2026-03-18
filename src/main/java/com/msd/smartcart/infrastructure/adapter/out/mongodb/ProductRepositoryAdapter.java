package com.msd.smartcart.infrastructure.adapter.out.mongodb;

import com.msd.smartcart.domain.model.Product;
import com.msd.smartcart.domain.port.out.ProductRepository;
import com.msd.smartcart.shared.exception.InfrastructureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductRepositoryAdapter implements ProductRepository {

    private final ProductMongoRepository productMongoRepository;

    @Override
    public Optional<Product> findById(String productId) {
        try {
            return productMongoRepository.findById(productId)
                    .map(doc -> new Product(
                            doc.getId(),
                            doc.getName(),
                            doc.getDescription(),
                            doc.getUnitPrice(),
                            doc.getStock()
                    ));
        } catch (DataAccessException e) {
            log.error("Infrastructure failure finding product [productId={}] — {}", productId, e.getMessage());
            throw new InfrastructureException("product.find.failed");
        }
    }

    @Override
    public List<Product> findAllByIds(List<String> productIds) {
        try {
            return productMongoRepository.findAllByIdIn(productIds)
                    .stream()
                    .map(doc -> new Product(
                            doc.getId(),
                            doc.getName(),
                            doc.getDescription(),
                            doc.getUnitPrice(),
                            doc.getStock()
                    ))
                    .toList();
        } catch (DataAccessException e) {
            log.error("Infrastructure failure finding products [count={}] — {}", productIds.size(), e.getMessage());
            throw new InfrastructureException("products.find.failed");
        }
    }
}
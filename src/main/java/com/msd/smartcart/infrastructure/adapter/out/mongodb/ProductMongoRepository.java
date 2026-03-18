package com.msd.smartcart.infrastructure.adapter.out.mongodb;

import com.msd.smartcart.infrastructure.adapter.out.mongodb.document.ProductDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProductMongoRepository extends MongoRepository<ProductDocument, String> {
    List<ProductDocument> findAllByIdIn(List<String> ids);
}
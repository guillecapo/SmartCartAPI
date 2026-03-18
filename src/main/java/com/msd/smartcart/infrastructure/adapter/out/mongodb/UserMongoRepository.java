package com.msd.smartcart.infrastructure.adapter.out.mongodb;

import com.msd.smartcart.infrastructure.adapter.out.mongodb.document.UserDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface UserMongoRepository extends MongoRepository<UserDocument, String> {
    Optional<UserDocument> findByEmail(String email);
    boolean existsByEmail(String email);
}
package com.msd.smartcart.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoAuditing
@EnableMongoRepositories(basePackages = "com.msd.smartcart.infrastructure.adapter.out.mongodb")
public class MongoConfig {

    // Spring Boot autoconfigura la conexión via application.yml
    // spring.data.mongodb.uri=${MONGODB_URI}
    // Este bean solo es necesario si usamos transacciones multi-documento

    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
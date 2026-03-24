package com.msd.smartcart.infrastructure.adapter.out.mongodb;

import com.msd.smartcart.domain.model.Product;
import com.msd.smartcart.infrastructure.adapter.out.mongodb.document.ProductDocument;
import com.msd.smartcart.shared.exception.InfrastructureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductRepositoryAdapterTest {

    @Mock private ProductMongoRepository productMongoRepository;

    private ProductRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ProductRepositoryAdapter(productMongoRepository);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private ProductDocument laptopDoc() {
        return ProductDocument.builder()
                .id("prod-001")
                .name("Laptop Dell XPS 15")
                .description("Desc")
                .unitPrice(new BigDecimal("1299.99"))
                .stock(10)
                .build();
    }

    // =========================================================================
    // findById
    // =========================================================================

    @Test
    void should_returnEmpty_when_productNotFound() {
        when(productMongoRepository.findById("prod-999")).thenReturn(Optional.empty());

        Optional<Product> result = adapter.findById("prod-999");

        assertThat(result).isEmpty();
    }

    @Test
    void should_returnMappedProduct_when_documentFound() {
        when(productMongoRepository.findById("prod-001")).thenReturn(Optional.of(laptopDoc()));

        Optional<Product> result = adapter.findById("prod-001");

        assertThat(result).isPresent();
        assertThat(result.get().productId()).isEqualTo("prod-001");
        assertThat(result.get().name()).isEqualTo("Laptop Dell XPS 15");
        assertThat(result.get().stock()).isEqualTo(10);
        assertThat(result.get().unitPrice()).isEqualByComparingTo(new BigDecimal("1299.99"));
    }

    @Test
    void should_throwInfrastructureException_when_dataAccessExceptionOnFindById() {
        when(productMongoRepository.findById(any()))
                .thenThrow(mock(DataAccessResourceFailureException.class));

        assertThatThrownBy(() -> adapter.findById("prod-001"))
                .isInstanceOf(InfrastructureException.class)
                .hasMessageContaining("product.find.failed");
    }

    // =========================================================================
    // findAllByIds
    // =========================================================================

    @Test
    void should_returnEmptyList_when_noProductsFound() {
        when(productMongoRepository.findAllByIdIn(any())).thenReturn(List.of());

        List<Product> result = adapter.findAllByIds(List.of("prod-999"));

        assertThat(result).isEmpty();
    }

    @Test
    void should_returnMappedProducts_when_documentsFound() {
        when(productMongoRepository.findAllByIdIn(List.of("prod-001")))
                .thenReturn(List.of(laptopDoc()));

        List<Product> result = adapter.findAllByIds(List.of("prod-001"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo("prod-001");
    }

    @Test
    void should_returnAllMappedProducts_when_multipleDocumentsFound() {
        ProductDocument mouseDoc = ProductDocument.builder()
                .id("prod-002").name("Mouse").description("Desc")
                .unitPrice(new BigDecimal("79.99")).stock(5).build();

        when(productMongoRepository.findAllByIdIn(any()))
                .thenReturn(List.of(laptopDoc(), mouseDoc));

        List<Product> result = adapter.findAllByIds(List.of("prod-001", "prod-002"));

        assertThat(result).hasSize(2);
    }

    @Test
    void should_throwInfrastructureException_when_dataAccessExceptionOnFindAllByIds() {
        when(productMongoRepository.findAllByIdIn(any()))
                .thenThrow(mock(DataAccessResourceFailureException.class));

        assertThatThrownBy(() -> adapter.findAllByIds(List.of("prod-001")))
                .isInstanceOf(InfrastructureException.class)
                .hasMessageContaining("products.find.failed");
    }
}
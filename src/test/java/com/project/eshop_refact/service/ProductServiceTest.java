package com.project.eshop_refact.service;

import com.project.eshop_refact.domain.Product;
import com.project.eshop_refact.dto.ProductDto;
import com.project.eshop_refact.exception.BusinessException;
import com.project.eshop_refact.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @Test
    @DisplayName("상품 등록 성공")
    void registerProduct_success(){
        ProductDto.RegisterRequest requestDto = new ProductDto.RegisterRequest();
        requestDto.setName("새우깡");
        requestDto.setPrice(1500);
        requestDto.setStockQuantity(100);

        Product fakeSavedProduct = new Product(requestDto.getName(), requestDto.getPrice(), requestDto.getStockQuantity());

        ReflectionTestUtils.setField(fakeSavedProduct, "id", 1L);

        when(productRepository.save(any(Product.class)))
                .thenReturn(fakeSavedProduct);

        Long savedId = productService.registerProduct(requestDto);

        assertThat(savedId).isEqualTo(1L);
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("상품 ID로 조회 성공")
    void getProductById_success(){
        Long productId = 1L;
        Product fakeProduct = new Product("새우깡", 1500, 100);
        ReflectionTestUtils.setField(fakeProduct, "id", 1L); // ID 주입

        when(productRepository.findById(productId)).thenReturn(Optional.of(fakeProduct));

        ProductDto.Response foundProduct = productService.getProductById(productId);

        assertThat(foundProduct).isNotNull();
        assertThat(foundProduct.getName()).isEqualTo("새우깡");
        assertThat(foundProduct.getPrice()).isEqualTo(1500);
    }

    @Test
    @DisplayName("상품 ID로 조회 실패 - 상품 없음")
    void getProductById_fail_notFound(){
        Long productId = 999L;

        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> {
            productService.getProductById(productId);
        });
    }
}
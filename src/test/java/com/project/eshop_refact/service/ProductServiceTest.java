package com.project.eshop_refact.service;

import com.project.eshop_refact.domain.Product;
import com.project.eshop_refact.dto.ProductDto;
import com.project.eshop_refact.exception.BusinessException;
import com.project.eshop_refact.exception.ErrorCode;
import com.project.eshop_refact.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("상품 등록 성공")
    void registerProduct_success(){
        //Given
        ProductDto.RegisterRequest requestDto = new ProductDto.RegisterRequest();
        requestDto.setName("새우깡");
        requestDto.setPrice(1500);
        requestDto.setStockQuantity(100);

        Product fakeSavedProduct = new Product(requestDto.getName(), requestDto.getPrice(), requestDto.getStockQuantity());

        ReflectionTestUtils.setField(fakeSavedProduct, "id", 1L);

        given(productRepository.save(any(Product.class))).willReturn(fakeSavedProduct);

        //when
        Long savedId = productService.registerProduct(requestDto);

        //then
        assertThat(savedId).isEqualTo(1L);
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("상품 ID로 조회 성공")
    void getProductById_success(){
        Long productId = 1L;
        Product fakeProduct = new Product("새우깡", 1500, 100);
        ReflectionTestUtils.setField(fakeProduct, "id", 1L);

        given(productRepository.findById(productId)).willReturn(Optional.of(fakeProduct));

        // When
        ProductDto.Response foundProduct = productService.getProductById(productId);

        //then
        assertThat(foundProduct).isNotNull();
        assertThat(foundProduct.getName()).isEqualTo("새우깡");
        assertThat(foundProduct.getPrice()).isEqualTo(1500);
    }

    @Test
    @DisplayName("상품 ID로 조회 실패 - 상품 없음(->예외 발생)")
    void getProductById_fail_notFound(){
        //given
        Long productId = 999L;
        given(productRepository.findById(productId)).willReturn(Optional.empty());

        // When & That
        assertThatThrownBy( () -> productService.getProductById(productId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }
    @Test
    @DisplayName("상품 가격 수정 성공")
    void updateProductPrice_success() {
        // given
        Long productId = 1L;
        int newPrice = 2000;
        Product fakeProduct = new Product("새우깡", 1500, 100);
        ReflectionTestUtils.setField(fakeProduct, "id", productId);

        given(productRepository.findById(productId)).willReturn(Optional.of(fakeProduct));

        // when
        ProductDto.Response response = productService.updateProductPrice(productId, newPrice);

        // then
        assertThat(response.getPrice()).isEqualTo(newPrice);
        assertThat(fakeProduct.getPrice()).isEqualTo(newPrice); // 도메인 객체의 상태가 변했는지 검증

        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("상품 검색 (Offset 페이징) 성공")
    void search_success() {
        // given
        ProductDto.SearchCondition condition = new ProductDto.SearchCondition();
        PageRequest pageable = PageRequest.of(0, 10);

        Product fakeProduct = new Product("새우깡", 1500, 100);
        ReflectionTestUtils.setField(fakeProduct, "id", 1L);

        Page<Product> expectedPage = new PageImpl<>(List.of(fakeProduct));

        given(productRepository.search(any(), any())).willReturn(expectedPage);

        // when
        Page<ProductDto.Response> result = productService.search(condition, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("새우깡");
    }

    @Test
    @DisplayName("상품 검색 (No-Offset 페이징) 성공")
    void searchNoOffset_success() {
        // given
        Long lastProductId = 10L;
        ProductDto.SearchCondition condition = new ProductDto.SearchCondition();
        PageRequest pageable = PageRequest.of(0, 10);

        Product fakeProduct = new Product("새우깡", 1500, 100);
        ReflectionTestUtils.setField(fakeProduct, "id", 9L); // No-Offset이므로 lastId보다 작은 ID 응답 가정

        Slice<Product> expectedSlice = new SliceImpl<>(List.of(fakeProduct));

        given(productRepository.searchNoOffset(any(), any(), any())).willReturn(expectedSlice);

        // when
        Slice<ProductDto.Response> result = productService.searchNoOffset(lastProductId, condition, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(9L);
        assertThat(result.hasNext()).isFalse(); // 다음 페이지 존재 여부(Slice 특성) 확인
    }

}
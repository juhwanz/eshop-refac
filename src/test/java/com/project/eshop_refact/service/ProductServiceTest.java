package com.project.eshop_refact.service;

import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.product.ProductService;
import com.project.eshop_refact.domain.product.ProductDto;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import com.project.eshop_refact.domain.product.ProductRepository;
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

/**
 * ProductService 비즈니스 로직 단위 테스트
 * Mockito를 활용하여 영속성 계층(Repository) 및 외부 의존성(EventPublisher)을 격리하고,
 * 상품 도메인의 핵심 비즈니스 흐름(등록, 조회, 상태 변경, 검색)을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("상품 등록: 유효한 데이터 요청 시 Repository를 통해 상품이 저장된다")
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
    @DisplayName("단일 상품 조회: 존재하는 식별자(ID) 요청 시 상품 정보가 반환된다")
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
    @DisplayName("단일 상품 조회 실패: 존재하지 않는 식별자(ID) 요청 시 비즈니스 예외가 발생한다")
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
    @DisplayName("상품 가격 수정: 가격 상태 변경 시 도메인이 업데이트되고 부가 로직을 위한 이벤트가 발행된다")
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
        assertThat(fakeProduct.getPrice()).isEqualTo(newPrice); // 도메인 객체의 상태(Dirty Checking 대상) 변경 검증

        // 도메인 로직 처리 후, 시스템 결합도를 낮추기 위해 스프링 이벤트를 정상적으로 발행했는지 검증
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

        // No-Offset 페이징의 특성에 따라 기준점(lastProductId) 미만의 데이터가 조회됨을 가정
        ReflectionTestUtils.setField(fakeProduct, "id", 9L);

        Slice<Product> expectedSlice = new SliceImpl<>(List.of(fakeProduct));

        given(productRepository.searchNoOffset(any(), any(), any())).willReturn(expectedSlice);

        // when
        Slice<ProductDto.Response> result = productService.searchNoOffset(lastProductId, condition, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(9L);

        // Count 쿼리를 회피하는 Slice 인터페이스의 특성을 활용하여 다음 페이지 존재 여부 정합성 검증
        assertThat(result.hasNext()).isFalse();
    }

}
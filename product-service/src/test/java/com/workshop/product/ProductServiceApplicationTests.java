package com.workshop.product;

import com.workshop.product.dto.ProductDto;
import com.workshop.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductServiceApplicationTests {

    @Autowired
    private ProductService productService;

    @Test
    void contextLoads() {
    }

    @Test
    void migratedProductsHaveProperTypes() {
        List<ProductDto> products = productService.getAllProducts();
        assertThat(products).hasSize(5);

        ProductDto fxd30 = productService.getProductByCode("FXD30");
        assertThat(fxd30.getName()).isEqualTo("30-Year Fixed Rate Mortgage");
        assertThat(fxd30.getTermMonths()).isEqualTo(360);
        assertThat(fxd30.getMaxAmount()).isEqualByComparingTo("1500000.00");
        assertThat(fxd30.getActive()).isTrue();
    }
}

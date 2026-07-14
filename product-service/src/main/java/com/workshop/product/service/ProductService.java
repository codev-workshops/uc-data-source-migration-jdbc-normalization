package com.workshop.product.service;

import com.workshop.product.dto.ProductDto;
import com.workshop.product.entity.LoanProduct;
import com.workshop.product.repository.LoanProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ProductService {

    private final LoanProductRepository productRepository;

    public ProductService(LoanProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<ProductDto> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public ProductDto getProductByCode(String code) {
        LoanProduct product = productRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found: " + code));
        return toDto(product);
    }

    private ProductDto toDto(LoanProduct product) {
        ProductDto dto = new ProductDto();
        dto.setCode(product.getCode());
        dto.setName(product.getName());
        dto.setType(product.getType());
        dto.setTermMonths(product.getTermMonths());
        dto.setRateType(product.getRateType());
        dto.setMinAmount(product.getMinAmount());
        dto.setMaxAmount(product.getMaxAmount());
        dto.setActive(product.getActive());
        dto.setEffectiveDate(product.getEffectiveDate());
        dto.setExpirationDate(product.getExpirationDate());
        return dto;
    }
}

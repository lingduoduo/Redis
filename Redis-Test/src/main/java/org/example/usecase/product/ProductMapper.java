package org.example.usecase.product;

public interface ProductMapper {
    Product selectById(Long id);

    void updateById(Product product);
}

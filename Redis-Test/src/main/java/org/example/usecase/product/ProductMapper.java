package org.example.usecase.product;

public interface ProductMapper {
    Product selectById(Long id);

    void updateById(Product product);

    void deleteById(Long id);

    /** Returns every known product ID; used to pre-load the Bloom filter on startup. */
    java.util.List<Long> selectAllIds();
}

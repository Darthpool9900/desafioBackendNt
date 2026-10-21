package com.teste.pagamento.Services;

import com.teste.pagamento.ExceptionHandlers.CheckoutException;
import com.teste.pagamento.data.CheckoutError;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductService {

    private final List<Product> products = List.of(
            new Product(
                    "basic",
                    "Plano Básico",
                    "Assinatura mensal do plano básico",
                    new BigDecimal("19.90"),
                    "BRL"
            ),
            new Product(
                    "premium",
                    "Plano Premium",
                    "Assinatura mensal com catálogo completo",
                    new BigDecimal("39.90"),
                    "BRL"
            ),
            new Product(
                    "family",
                    "Plano Família",
                    "Assinatura mensal para toda a família",
                    new BigDecimal("59.90"),
                    "BRL"
            )
    );

    public Product GetProduct(String slug){
        return products.stream()
                .filter(product -> product.slug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new CheckoutException(CheckoutError.PRODUCT_NOT_FOUND));
    }

    public List<Product> GetProducts(List<String> slugs){
        if(slugs == null || slugs.isEmpty()){
            throw new CheckoutException(CheckoutError.EMPTY_CART);
        }

        return slugs.stream()
                .map(this::GetProduct)
                .toList();
    }

    public List<Product> GetAllProducts(){
        return products;
    }

    public record Product(
            String slug,
            String name,
            String description,
            BigDecimal price,
            String currency
    ) {}
}

package com.teste.pagamento.Models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "checkouts")
public class Checkout {

    @Id
    private String cartId;

    private String status;
    private Instant paidAt;

    @Embedded
    private Payment payment;

    @ElementCollection
    @CollectionTable(name = "checkout_products",joinColumns = @JoinColumn(name = "cart_id"))
    private List<Product> products;

    public Checkout(){}

    public Checkout(String cartId,String status,Instant paidAt,Payment payment,List<Product> products){
        this.cartId = cartId;
        this.status = status;
        this.paidAt = paidAt;
        this.payment = payment;
        this.products = products;
    }

    public String getCartId(){
        return cartId;
    }

    public String getStatus(){
        return status;
    }

    @Embeddable
    public static class Payment {

        private String method;
        private String lastDigits;
        private BigDecimal amount;
        private String currency;

        public Payment(){}

        public Payment(String method,String lastDigits,BigDecimal amount,String currency){
            this.method = method;
            this.lastDigits = lastDigits;
            this.amount = amount;
            this.currency = currency;
        }
    }

    @Embeddable
    public static class Product {

        private String slug;
        private String name;
        private BigDecimal price;

        public Product(){}

        public Product(String slug,String name,BigDecimal price){
            this.slug = slug;
            this.name = name;
            this.price = price;
        }
    }
}

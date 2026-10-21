package com.teste.pagamento.Services;

import com.teste.pagamento.Connections.RedisConnect;
import com.teste.pagamento.ExceptionHandlers.CheckoutException;
import com.teste.pagamento.data.CheckoutError;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PreCheckoutService {

    private final ProductService productService;
    private final PaymentMethodService paymentMethodService;
    private final RedisConnect redisConnect;
    private final ObjectMapper mapper;

    public PreCheckoutService(ProductService productService, PaymentMethodService paymentMethodService, RedisConnect redisConnect, ObjectMapper mapper){
        this.productService = productService;
        this.paymentMethodService = paymentMethodService;
        this.redisConnect = redisConnect;
        this.mapper = mapper;
    }

    public JsonNode CreatePreCheckout(JsonNode body){
        List<String> slugs = new ArrayList<>();

        if(body == null || !body.isObject()){
            throw new CheckoutException(CheckoutError.INVALID_REQUEST);
        }

        JsonNode requestedProducts = body.path("products");

        if(!requestedProducts.isArray() || requestedProducts.isEmpty()){
            throw new CheckoutException(CheckoutError.EMPTY_CART);
        }

        for(JsonNode product : requestedProducts){
            String slug = product.path("slug").asText();
            slugs.add(slug);
        }

        List<ProductService.Product> products = productService.GetProducts(slugs);
        List<PaymentMethodService.PaymentMethod> payments = paymentMethodService.GetPaymentMethods();

        String cartId = UUID.randomUUID().toString();

        ObjectNode cart = mapper.createObjectNode();

        cart.put("cartId",cartId);
        cart.set("products",mapper.valueToTree(products));
        cart.set("paymentMethods",mapper.valueToTree(payments));

        return redisConnect.InsertRedis(cartId,cart);
    }
}

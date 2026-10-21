package com.teste.pagamento.Services;

import com.teste.pagamento.Connections.RedisConnect;
import com.teste.pagamento.ExceptionHandlers.CheckoutException;
import com.teste.pagamento.data.CheckoutError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PreCheckoutServiceTest {
    ProductService products = new ProductService();
    PaymentMethodService methods = new PaymentMethodService();
    RedisConnect redis = mock(RedisConnect.class);
    ObjectMapper mapper = new ObjectMapper();
    PreCheckoutService service = new PreCheckoutService(products,methods,redis,mapper);

    @Test void createsCartWithProductsAndPaymentMethods() throws Exception{
        when(redis.InsertRedis(anyString(),any(JsonNode.class))).thenAnswer(i -> i.getArgument(1));
        JsonNode result = service.CreatePreCheckout(mapper.readTree("{\"products\":[{\"slug\":\"basic\"},{\"slug\":\"premium\"}]}"));
        assertEquals(36,result.path("cartId").asText().length());
        assertEquals(2,result.path("products").size());
        assertEquals(3,result.path("paymentMethods").size());
        verify(redis).InsertRedis(eq(result.path("cartId").asText()),any(JsonNode.class));
    }
    @Test void rejectsEmptyProducts() throws Exception{
        CheckoutException e = assertThrows(CheckoutException.class,() -> service.CreatePreCheckout(mapper.readTree("{\"products\":[]}")));
        assertEquals(CheckoutError.EMPTY_CART,e.getError());
        verifyNoInteractions(redis);
    }
    @Test void rejectsUnknownProduct() throws Exception{
        CheckoutException e = assertThrows(CheckoutException.class,() -> service.CreatePreCheckout(mapper.readTree("{\"products\":[{\"slug\":\"unknown\"}]}")));
        assertEquals(CheckoutError.PRODUCT_NOT_FOUND,e.getError());
    }
    @Test void rejectsNullRequest(){
        assertEquals(CheckoutError.INVALID_REQUEST,assertThrows(CheckoutException.class,() -> service.CreatePreCheckout(null)).getError());
    }
}

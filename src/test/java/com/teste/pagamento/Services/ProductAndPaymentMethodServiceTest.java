package com.teste.pagamento.Services;

import com.teste.pagamento.ExceptionHandlers.CheckoutException;
import com.teste.pagamento.data.CheckoutError;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProductAndPaymentMethodServiceTest {
    @Test void productsAreReturnedInRequestedOrder(){
        var result = new ProductService().GetProducts(List.of("premium","basic"));
        assertEquals("premium",result.get(0).slug());
        assertEquals("basic",result.get(1).slug());
    }
    @Test void unknownProductThrows(){
        assertEquals(CheckoutError.PRODUCT_NOT_FOUND,assertThrows(CheckoutException.class,() -> new ProductService().GetProduct("missing")).getError());
    }
    @Test void paymentMethodsAreAvailable(){
        PaymentMethodService service = new PaymentMethodService();
        assertEquals(3,service.GetPaymentMethods().size());
        assertTrue(service.IsValidPaymentMethod("pix"));
        assertEquals("Pix",service.GetPaymentMethod("PIX").name());
    }
    @Test void invalidPaymentMethodThrows(){
        assertEquals(CheckoutError.INVALID_PAYMENT_METHOD,assertThrows(CheckoutException.class,() -> new PaymentMethodService().GetPaymentMethod("Boleto")).getError());
    }
}

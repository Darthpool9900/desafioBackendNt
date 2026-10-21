package com.teste.pagamento.Controllers;

import com.teste.pagamento.Services.CheckoutService;
import com.teste.pagamento.Services.PreCheckoutService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutControllerTest {
    PreCheckoutService pre = mock(PreCheckoutService.class);
    CheckoutService checkout = mock(CheckoutService.class);
    CheckoutController controller = new CheckoutController(pre,checkout);
    ObjectMapper mapper = new ObjectMapper();
    @Test void preCheckoutDelegates(){
        JsonNode request = mapper.readTree("{\"products\":[]}");
        JsonNode response = mapper.readTree("{\"cartId\":\"abc\"}");
        when(pre.CreatePreCheckout(request)).thenReturn(response);
        assertSame(response,controller.preCheckout(request));
        verify(pre).CreatePreCheckout(request);
    }
    @Test void checkoutDelegates(){
        JsonNode request = mapper.readTree("{\"cartId\":\"abc\"}");
        JsonNode response = mapper.readTree("{\"status\":\"APROVADO\"}");
        when(checkout.ProcessCheckout(request)).thenReturn(response);
        assertSame(response,controller.checkout(request));
        verify(checkout).ProcessCheckout(request);
    }
}

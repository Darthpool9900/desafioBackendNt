package com.teste.pagamento.Services;

import com.teste.pagamento.Connections.RedisConnect;
import com.teste.pagamento.ExceptionHandlers.CheckoutException;
import com.teste.pagamento.Models.Checkout;
import com.teste.pagamento.Models.OutboxEvent;
import com.teste.pagamento.data.CheckoutError;
import com.teste.pagamento.repositories.CheckoutRepository;
import com.teste.pagamento.repositories.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CheckoutServiceTest {
    final String cartId = UUID.randomUUID().toString();
    RedisConnect redis = mock(RedisConnect.class);
    CheckoutRepository checkouts = mock(CheckoutRepository.class);
    OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    TransactionTemplate transactions = mock(TransactionTemplate.class);
    ObjectMapper mapper = new ObjectMapper();
    CheckoutService service = new CheckoutService(redis,new PaymentMethodService(),checkouts,outbox,mapper,transactions);

    JsonNode json(String s){return mapper.readTree(s);}
    JsonNode request(String method,String card){
        return json("{\"cartId\":\""+cartId+"\",\"paymentMethod\":{\"name\":\""+method+"\""+(card == null ? "" : ",\"card\":"+card)+"}}");
    }
    @BeforeEach void setup(){
        when(redis.AcquireLock(cartId)).thenReturn("token");
        when(redis.GetInformation(cartId)).thenReturn(json("{\"cartId\":\""+cartId+"\",\"products\":[{\"slug\":\"premium\",\"name\":\"Plano Premium\",\"price\":39.90}],\"paymentMethods\":[{\"name\":\"Pix\"},{\"name\":\"Cartão de Crédito\"},{\"name\":\"Cartão de Débito\"}]}"));
        when(checkouts.findById(cartId)).thenReturn(Optional.empty());
        when(transactions.execute(any())).thenAnswer(i -> ((org.springframework.transaction.support.TransactionCallback<?>)i.getArgument(0)).doInTransaction(mock(org.springframework.transaction.TransactionStatus.class)));
    }
    @Test void pixApprovedPersistsCheckoutAndOutbox(){
        JsonNode result = service.ProcessCheckout(request("Pix",null));
        assertEquals("APROVADO",result.path("status").asText());
        assertEquals("Pix",result.path("payment").path("method").asText());
        assertTrue(result.path("payment").path("pixCode").asText().startsWith("PIX-"));
        verify(checkouts).saveAndFlush(any(Checkout.class));
        verify(outbox).save(argThat(e -> e.getPayload().contains("PAYMENT_APPROVED") && e.getPayload().contains(cartId)));
        verify(redis).ReleaseLock(cartId,"token");
    }
    @Test void creditCardApproved(){
        String card = "{\"number\":\"4111111111111111\",\"expiry\":\"12/30\",\"cvv\":\"123\",\"holderName\":\"Maria Teste\"}";
        JsonNode result = service.ProcessCheckout(request("Cartão de Crédito",card));
        assertEquals("1111",result.path("payment").path("lastDigits").asText());
        verify(outbox).save(any(OutboxEvent.class));
    }
    @Test void invalidCardIsRejectedAndLockReleased(){
        String card = "{\"number\":\"123\",\"expiry\":\"12/30\",\"cvv\":\"123\",\"holderName\":\"Maria Teste\"}";
        assertEquals(CheckoutError.INVALID_CARD_NUMBER,assertThrows(CheckoutException.class,() -> service.ProcessCheckout(request("Cartão de Crédito",card))).getError());
        verifyNoInteractions(outbox);
        verify(redis).ReleaseLock(cartId,"token");
    }
    @Test void duplicateApprovedIsRejected(){
        Checkout existing = mock(Checkout.class);
        when(existing.getStatus()).thenReturn("APROVADO");
        when(existing.getCartId()).thenReturn(cartId);
        when(checkouts.findById(cartId)).thenReturn(Optional.of(existing));
        assertEquals(CheckoutError.PAYMENT_ALREADY_APPROVED,assertThrows(CheckoutException.class,() -> service.ProcessCheckout(request("Pix",null))).getError());
        verifyNoInteractions(outbox);
    }
    @Test void concurrentLockIsRejected(){
        when(redis.AcquireLock(cartId)).thenReturn(null);
        assertEquals(CheckoutError.PAYMENT_IN_PROGRESS,assertThrows(CheckoutException.class,() -> service.ProcessCheckout(request("Pix",null))).getError());
        verify(redis,never()).ReleaseLock(anyString(),anyString());
    }
    @Test void missingCartIsRejected(){
        when(redis.GetInformation(cartId)).thenThrow(new CheckoutException(CheckoutError.CART_NOT_FOUND,cartId));
        assertEquals(CheckoutError.CART_NOT_FOUND,assertThrows(CheckoutException.class,() -> service.ProcessCheckout(request("Pix",null))).getError());
        verifyNoInteractions(outbox);
    }
    @Test void pixWithCardIsRejected(){
        assertEquals(CheckoutError.INVALID_PIX_REQUEST,assertThrows(CheckoutException.class,() -> service.ProcessCheckout(request("Pix","{}"))).getError());
    }
    @Test void invalidPaymentMethodIsRejected(){
        assertEquals(CheckoutError.INVALID_PAYMENT_METHOD,assertThrows(CheckoutException.class,() -> service.ProcessCheckout(request("Boleto",null))).getError());
    }
}

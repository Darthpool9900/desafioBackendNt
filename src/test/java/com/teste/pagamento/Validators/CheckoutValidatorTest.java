package com.teste.pagamento.Validators;

import com.teste.pagamento.ExceptionHandlers.CheckoutException;
import com.teste.pagamento.data.CheckoutError;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CheckoutValidatorTest {
    @Test void acceptsValidLuhnCard(){assertDoesNotThrow(() -> CheckoutValidator.ValidateCardNumber("4111111111111111","cart"));}
    @Test void rejectsInvalidLuhnCard(){assertEquals(CheckoutError.INVALID_CARD_NUMBER,assertThrows(CheckoutException.class,() -> CheckoutValidator.ValidateCardNumber("4111111111111112","cart")).getError());}
    @Test void rejectsExpiredCard(){assertEquals(CheckoutError.INVALID_CARD_EXPIRY,assertThrows(CheckoutException.class,() -> CheckoutValidator.ValidateCardExpiry("01/20","cart")).getError());}
    @Test void rejectsCvv(){assertEquals(CheckoutError.INVALID_CARD_CVV,assertThrows(CheckoutException.class,() -> CheckoutValidator.ValidateCardCVV("12","cart")).getError());}
    @Test void rejectsHolder(){assertEquals(CheckoutError.INVALID_CARD_HOLDER,assertThrows(CheckoutException.class,() -> CheckoutValidator.ValidateCardHolder(" ","cart")).getError());}
    @Test void rejectsCardInPix(){assertEquals(CheckoutError.INVALID_PIX_REQUEST,assertThrows(CheckoutException.class,() -> CheckoutValidator.ValidatePix(true,"cart")).getError());}
    @Test void rejectsNonPositiveAmount(){assertEquals(CheckoutError.VALIDATION_ERROR,assertThrows(CheckoutException.class,() -> CheckoutValidator.ValidateAmount(BigDecimal.ZERO,"cart")).getError());}
    @Test void rejectsWrongCurrency(){assertEquals(CheckoutError.VALIDATION_ERROR,assertThrows(CheckoutException.class,() -> CheckoutValidator.ValidateCurrency("USD","cart")).getError());}
    @Test void rejectsDeclinedCard(){assertEquals(CheckoutError.PAYMENT_DECLINED,assertThrows(CheckoutException.class,() -> CheckoutValidator.ValidatePaymentDeclined("12340002","cart")).getError());}
    @Test void rejectsEmptyProducts(){assertEquals(CheckoutError.EMPTY_CART,assertThrows(CheckoutException.class,() -> CheckoutValidator.ValidateProducts(List.of())).getError());}
}

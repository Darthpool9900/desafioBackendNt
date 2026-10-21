package com.teste.pagamento.Validators;

import com.teste.pagamento.ExceptionHandlers.CheckoutException;
import com.teste.pagamento.data.CheckoutError;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CheckoutValidator {

    private static final Set<String> PAYMENT_METHODS = Set.of(
            "CREDIT_CARD",
            "DEBIT_CARD",
            "PIX"
    );

    private CheckoutValidator(){}

    public static void ValidateCartId(String cartId){
        try{
            UUID.fromString(cartId);
        }catch (Exception e){
            throw new CheckoutException(CheckoutError.CART_NOT_FOUND,cartId);
        }
    }

    public static void ValidateProducts(List<String> products){
        if(products == null || products.isEmpty()){
            throw new CheckoutException(CheckoutError.EMPTY_CART);
        }

        for(String slug : products){
            ValidateProductSlug(slug);
        }
    }

    public static void ValidateProductSlug(String slug){
        if(slug == null || slug.isBlank() || !slug.matches("[a-zA-Z0-9_-]+")){
            throw new CheckoutException(CheckoutError.VALIDATION_ERROR);
        }
    }

    public static void ValidateProductExists(boolean exists, String cartId){
        if(!exists){
            throw new CheckoutException(CheckoutError.PRODUCT_NOT_FOUND,cartId);
        }
    }

    public static void ValidateCartExists(boolean exists, String cartId){
        if(!exists){
            throw new CheckoutException(CheckoutError.CART_NOT_FOUND,cartId);
        }
    }

    public static void ValidatePaymentMethod(String method, String cartId){
        if(method == null || !PAYMENT_METHODS.contains(method.toUpperCase())){
            throw new CheckoutException(CheckoutError.INVALID_PAYMENT_METHOD,cartId);
        }
    }

    public static void ValidateCardNumber(String number, String cartId){
        if(number == null || !number.matches("\\d{13,19}")){
            throw new CheckoutException(CheckoutError.INVALID_CARD_NUMBER,cartId);
        }

        int sum = 0;
        boolean doubleDigit = false;

        for(int i = number.length() - 1; i >= 0; i--){
            int digit = Character.getNumericValue(number.charAt(i));

            if(doubleDigit){
                digit *= 2;

                if(digit > 9){
                    digit -= 9;
                }
            }

            sum += digit;
            doubleDigit = !doubleDigit;
        }

        if(sum % 10 != 0){
            throw new CheckoutException(CheckoutError.INVALID_CARD_NUMBER,cartId);
        }
    }

    public static void ValidateCardExpiry(String expiry, String cartId){
        try{
            if(expiry == null || !expiry.matches("\\d{2}/\\d{2}")){
                throw new CheckoutException(CheckoutError.INVALID_CARD_EXPIRY,cartId);
            }

            YearMonth expiration = YearMonth.parse(
                    expiry,
                    DateTimeFormatter.ofPattern("MM/yy")
            );

            if(expiration.isBefore(YearMonth.now())){
                throw new CheckoutException(CheckoutError.INVALID_CARD_EXPIRY,cartId);
            }

        }catch (DateTimeParseException e){
            throw new CheckoutException(CheckoutError.INVALID_CARD_EXPIRY,cartId);
        }
    }

    public static void ValidateCardCVV(String cvv, String cartId){
        if(cvv == null || !cvv.matches("\\d{3,4}")){
            throw new CheckoutException(CheckoutError.INVALID_CARD_CVV,cartId);
        }
    }

    public static void ValidateCardHolder(String holderName, String cartId){
        if(holderName == null || holderName.isBlank() || holderName.trim().length() < 3){
            throw new CheckoutException(CheckoutError.INVALID_CARD_HOLDER,cartId);
        }
    }

    public static void ValidateCard(
            String number,
            String expiry,
            String cvv,
            String holderName,
            String cartId
    ){
        ValidateCardNumber(number,cartId);
        ValidateCardExpiry(expiry,cartId);
        ValidateCardCVV(cvv,cartId);
        ValidateCardHolder(holderName,cartId);
    }

    public static void ValidatePix(boolean hasCardData, String cartId){
        if(hasCardData){
            throw new CheckoutException(CheckoutError.INVALID_PIX_REQUEST,cartId);
        }
    }

    public static void ValidateAmount(BigDecimal amount, String cartId){
        if(amount == null || amount.compareTo(BigDecimal.ZERO) <= 0){
            throw new CheckoutException(CheckoutError.VALIDATION_ERROR,cartId);
        }
    }

    public static void ValidateCurrency(String currency, String cartId){
        if(currency == null || !currency.equals("BRL")){
            throw new CheckoutException(CheckoutError.VALIDATION_ERROR,cartId);
        }
    }

    public static void ValidateCartState(String status, String cartId){
        if(status == null){
            throw new CheckoutException(CheckoutError.INVALID_CART_STATE,cartId);
        }

        switch(status.toUpperCase()){
            case "APROVADO" -> throw new CheckoutException(
                    CheckoutError.PAYMENT_ALREADY_APPROVED,cartId
            );

            case "PROCESSANDO" -> throw new CheckoutException(
                    CheckoutError.PAYMENT_IN_PROGRESS,cartId
            );

            case "PENDENTE" -> {}

            default -> throw new CheckoutException(
                    CheckoutError.INVALID_CART_STATE,cartId
            );
        }
    }

    public static void ValidateDuplicateRequest(boolean duplicated, String cartId){
        if(duplicated){
            throw new CheckoutException(CheckoutError.DUPLICATE_REQUEST,cartId);
        }
    }

    public static void ValidatePaymentDeclined(String cardNumber, String cartId){
        if(cardNumber != null && cardNumber.endsWith("0002")){
            throw new CheckoutException(CheckoutError.PAYMENT_DECLINED,cartId);
        }
    }
}

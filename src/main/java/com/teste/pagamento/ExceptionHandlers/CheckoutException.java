package com.teste.pagamento.ExceptionHandlers;

import com.teste.pagamento.data.CheckoutError;

public class CheckoutException extends RuntimeException {

    private final CheckoutError error;
    private final String cartId;

    public CheckoutException(CheckoutError error, String cartId) {
        super(error.name());
        this.error = error;
        this.cartId = cartId;
    }

    public CheckoutException(CheckoutError error) {
        this(error, null);
    }

    public CheckoutError getError() {
        return error;
    }

    public String getCartId() {
        return cartId;
    }
}
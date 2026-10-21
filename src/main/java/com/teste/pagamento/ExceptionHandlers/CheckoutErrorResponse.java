package com.teste.pagamento.ExceptionHandlers;


public record CheckoutErrorResponse(
        String displayType,
        String errorCode,
        String cartId,
        FrontendError frontendError
) {
    public record FrontendError(
            String code,
            String title,
            String description,
            ErrorButton primaryButton,
            ErrorButton secondaryButton
    ) {}

    public record ErrorButton(
            String label,
            String url
    ) {}
}

package com.teste.pagamento.Services;

import com.teste.pagamento.ExceptionHandlers.CheckoutException;
import com.teste.pagamento.data.CheckoutError;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentMethodService {

    private final List<PaymentMethod> paymentMethods = List.of(
            new PaymentMethod("Cartão de Crédito"),
            new PaymentMethod("Cartão de Débito"),
            new PaymentMethod("Pix")
    );

    public List<PaymentMethod> GetPaymentMethods(){
        return paymentMethods;
    }

    public PaymentMethod GetPaymentMethod(String name){
        return paymentMethods.stream()
                .filter(method -> method.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new CheckoutException(CheckoutError.INVALID_PAYMENT_METHOD));
    }

    public boolean IsValidPaymentMethod(String name){
        return paymentMethods.stream()
                .anyMatch(method -> method.name().equalsIgnoreCase(name));
    }

    public record PaymentMethod(
            String name
    ) {}
}
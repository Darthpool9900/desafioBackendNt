package com.teste.pagamento.Controllers;

import com.teste.pagamento.Services.CheckoutService;
import com.teste.pagamento.Services.PreCheckoutService;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/operations")
public class CheckoutController {

    private final PreCheckoutService preCheckoutService;
    private final CheckoutService checkoutService;

    public CheckoutController(PreCheckoutService preCheckoutService, CheckoutService checkoutService){
        this.preCheckoutService = preCheckoutService;
        this.checkoutService = checkoutService;
    }

    @PostMapping("/pre-checkout")
    public JsonNode preCheckout(@RequestBody JsonNode body){
        return preCheckoutService.CreatePreCheckout(body);
    }

    @PostMapping("/checkout")
    public JsonNode checkout(@RequestBody JsonNode body){
        return checkoutService.ProcessCheckout(body);
    }
}
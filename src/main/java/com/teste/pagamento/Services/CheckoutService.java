
package com.teste.pagamento.Services;

import com.teste.pagamento.Connections.RedisConnect;
import com.teste.pagamento.ExceptionHandlers.CheckoutException;
import com.teste.pagamento.Models.Checkout;
import com.teste.pagamento.Models.OutboxEvent;
import com.teste.pagamento.repositories.CheckoutRepository;
import com.teste.pagamento.repositories.OutboxEventRepository;
import com.teste.pagamento.data.CheckoutError;
import com.teste.pagamento.Validators.CheckoutValidator;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CheckoutService {

    private static final Set<String> BLOCKED_STATUS = Set.of(
            "PENDENTE",
            "EM_PROCESSAMENTO"
    );

    private final RedisConnect redisConnect;
    private final PaymentMethodService paymentMethodService;
    private final CheckoutRepository checkoutRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactionTemplate;

    public CheckoutService(RedisConnect redisConnect,PaymentMethodService paymentMethodService,CheckoutRepository checkoutRepository,OutboxEventRepository outboxEventRepository,ObjectMapper mapper,TransactionTemplate transactionTemplate){
        this.redisConnect = redisConnect;
        this.paymentMethodService = paymentMethodService;
        this.checkoutRepository = checkoutRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.mapper = mapper;
        this.transactionTemplate = transactionTemplate;
    }

    public void VerifyCart(String cartId,String payment){
        verifyOrder(cartId);

        JsonNode cart = redisConnect.GetInformation(cartId);

        VerifyPaymentMethod(cart,payment);
    }

    public void verifyOrder(String cartId){
        if(cartId == null || cartId.isBlank()){
            throw new CheckoutException(CheckoutError.INVALID_REQUEST);
        }

        CheckoutValidator.ValidateCartId(cartId);

        checkoutRepository.findById(cartId).ifPresent(this::ValidateOrderStatus);

        JsonNode cart = redisConnect.GetInformation(cartId);
        JsonNode products = cart.path("products");

        if(!cartId.equals(cart.path("cartId").asText())){
            throw new CheckoutException(CheckoutError.INVALID_CART_STATE,cartId);
        }

        if(!products.isArray() || products.isEmpty()){
            throw new CheckoutException(CheckoutError.EMPTY_CART,cartId);
        }

        for(JsonNode product : products){
            if(!IsValidProduct(product)){
                throw new CheckoutException(CheckoutError.VALIDATION_ERROR,cartId);
            }
        }
    }

    private void ValidateOrderStatus(Checkout checkout){
        String status = checkout.getStatus();
        String cartId = checkout.getCartId();

        if("APROVADO".equals(status)){
            throw new CheckoutException(CheckoutError.PAYMENT_ALREADY_APPROVED,cartId);
        }

        if(BLOCKED_STATUS.contains(status)){
            throw new CheckoutException(CheckoutError.PAYMENT_IN_PROGRESS,cartId);
        }

        throw new CheckoutException(CheckoutError.INVALID_CART_STATE,cartId);
    }

    private boolean IsValidProduct(JsonNode product){
        return product.isObject()
                && !product.path("slug").asText().isBlank()
                && !product.path("name").asText().isBlank()
                && product.path("price").isNumber()
                && product.path("price").decimalValue().signum() > 0;
    }

    private void VerifyPaymentMethod(JsonNode cart,String payment){
        JsonNode methods = cart.path("paymentMethods");

        if(!methods.isArray()){
            throw new CheckoutException(CheckoutError.INVALID_CART_STATE);
        }

        for(JsonNode method : methods){
            if(method.path("name").asText().equals(payment)){
                return;
            }
        }

        throw new CheckoutException(CheckoutError.INVALID_PAYMENT_METHOD);
    }

    public JsonNode ProcessCheckout(JsonNode body){
        String cartId = body.path("cartId").asText();

        if(cartId.isBlank()){
            throw new CheckoutException(CheckoutError.INVALID_REQUEST);
        }

        CheckoutValidator.ValidateCartId(cartId);

        String token = redisConnect.AcquireLock(cartId);

        if(token == null){
            throw new CheckoutException(CheckoutError.PAYMENT_IN_PROGRESS,cartId);
        }

        try{
            return transactionTemplate.execute(status -> ExecuteCheckout(body));
        }finally{
            redisConnect.ReleaseLock(cartId,token);
        }
    }

    @Transactional
    public JsonNode ExecuteCheckout(JsonNode body){
        String cartId = body.path("cartId").asText();
        String payment = body.path("paymentMethod").path("name").asText();

        VerifyCart(cartId,payment);

        JsonNode cart = redisConnect.GetInformation(cartId);
        JsonNode products = cart.path("products");

        BigDecimal amount = BigDecimal.ZERO;
        ArrayNode purchasedProducts = mapper.createArrayNode();
        List<Checkout.Product> checkoutProducts = new ArrayList<>();

        for(JsonNode product : products){
            String slug = product.path("slug").asText();
            String name = product.path("name").asText();
            BigDecimal price = product.path("price").decimalValue();

            amount = amount.add(price);

            checkoutProducts.add(new Checkout.Product(slug,name,price));

            ObjectNode purchasedProduct = mapper.createObjectNode();
            purchasedProduct.put("slug",slug);
            purchasedProduct.put("name",name);
            purchasedProduct.put("price",price);

            purchasedProducts.add(purchasedProduct);
        }

        CheckoutValidator.ValidateAmount(amount,cartId);
        CheckoutValidator.ValidateCurrency("BRL",cartId);

        ObjectNode paymentData = mapper.createObjectNode();

        paymentData.put("method",payment);
        paymentData.put("amount",amount);
        paymentData.put("currency","BRL");

        String lastDigits = null;

        if("Pix".equals(payment)){
            CheckoutValidator.ValidatePix(
                    body.path("paymentMethod").hasNonNull("card"),cartId
            );

            paymentData.put("pixCode","PIX-"+UUID.randomUUID());
        }else{
            JsonNode card = body.path("paymentMethod").path("card");

            String number = card.path("number").asText();
            String expiry = card.path("expiry").asText();
            String cvv = card.path("cvv").asText();
            String holderName = card.path("holderName").asText();

            CheckoutValidator.ValidateCard(
                    number,expiry,cvv,holderName,cartId
            );

            CheckoutValidator.ValidatePaymentDeclined(number,cartId);

            lastDigits = number.substring(number.length()-4);
            paymentData.put("lastDigits",lastDigits);
        }

        Instant paidAt = Instant.now();

        Checkout.Payment checkoutPayment = new Checkout.Payment(
                payment,lastDigits,amount,"BRL"
        );

        Checkout checkout = new Checkout(
                cartId,"APROVADO",paidAt,checkoutPayment,checkoutProducts
        );

        checkoutRepository.saveAndFlush(checkout);

        ObjectNode event = mapper.createObjectNode();

        event.put("eventType","PAYMENT_APPROVED");
        event.put("cartId",cartId);
        event.put("status","APROVADO");
        event.put("amount",amount);
        event.put("currency","BRL");
        event.put("paymentMethod",payment);
        event.put("paidAt",paidAt.toString());

        OutboxEvent outboxEvent = new OutboxEvent(
                UUID.randomUUID(),
                "PAYMENT_APPROVED",
                cartId,
                event.toString(),
                Instant.now()
        );

        outboxEventRepository.save(outboxEvent);

        ObjectNode response = mapper.createObjectNode();

        response.put("cartId",cartId);
        response.put("status","APROVADO");
        response.put("paidAt",paidAt.toString());
        response.set("payment",paymentData);
        response.set("products",purchasedProducts);

        return response;
    }
}

package com.teste.pagamento.Messaging;

import com.teste.pagamento.Config.RabbitMQConfig;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

@Component
public class PaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate){
        this.rabbitTemplate = rabbitTemplate;
    }

    public void PublishPaymentApproved(JsonNode event){
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY,
                event.toString()
        );
    }
}
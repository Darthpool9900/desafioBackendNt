package com.teste.pagamento.Config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "checkout.exchange";
    public static final String QUEUE = "checkout.payment.approved";
    public static final String ROUTING_KEY = "payment.approved";

    @Bean
    public DirectExchange checkoutExchange(){
        return new DirectExchange(EXCHANGE,true,false);
    }

    @Bean
    public Queue paymentApprovedQueue(){
        return new Queue(QUEUE,true);
    }

    @Bean
    public Binding paymentApprovedBinding(
            Queue paymentApprovedQueue,
            DirectExchange checkoutExchange
    ){
        return BindingBuilder.bind(paymentApprovedQueue)
                .to(checkoutExchange)
                .with(ROUTING_KEY);
    }
}
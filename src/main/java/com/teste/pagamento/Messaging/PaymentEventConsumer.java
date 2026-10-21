
package com.teste.pagamento.Messaging;

import com.teste.pagamento.Config.RabbitMQConfig;
import com.teste.pagamento.repositories.ProcessedEventRepository;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class PaymentEventConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final TransactionTemplate transactionTemplate;

    public PaymentEventConsumer(ProcessedEventRepository processedEventRepository,TransactionTemplate transactionTemplate){
        this.processedEventRepository = processedEventRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void ConsumePaymentApproved(Message message){
        String messageId = message.getMessageProperties().getMessageId();

        if(messageId == null){
            throw new IllegalArgumentException("Evento recebido sem messageId.");
        }

        UUID eventId = UUID.fromString(messageId);

        Boolean processed = transactionTemplate.execute(status -> {
            int inserted = processedEventRepository.InsertIfNotExists(eventId);

            if(inserted == 0){
                return false;
            }

            String payload = new String(
                    message.getBody(),
                    StandardCharsets.UTF_8
            );

            System.out.println("Processando pagamento: "+payload);

            return true;
        });

        if(Boolean.TRUE.equals(processed)){
            System.out.println("Evento processado: "+eventId);
        }else{
            System.out.println("Evento duplicado ignorado: "+eventId);
        }
    }
}

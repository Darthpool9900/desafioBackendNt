
package com.teste.pagamento.Messaging;

import com.teste.pagamento.Config.RabbitMQConfig;
import com.teste.pagamento.Models.OutboxEvent;
import com.teste.pagamento.repositories.OutboxEventRepository;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxPublisherService {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisherService(OutboxEventRepository outboxEventRepository,RabbitTemplate rabbitTemplate){
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    public void PublishPendingEvents(){
        System.out.println("Verificando eventos pendentes no Outbox...");

        List<OutboxEvent> events = outboxEventRepository.FindPendingEvents(
                PageRequest.of(0,50)
        );

        for(OutboxEvent event : events){
            try{
                PublishEvent(event);
            }catch(Exception e){
                System.err.println(
                        "Erro ao publicar evento "+event.getId()+": "+e.getMessage()
                );
            }
        }
    }

    private void PublishEvent(OutboxEvent event) throws Exception{
        MessageProperties properties = new MessageProperties();

        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId(event.getId().toString());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);

        Message message = new Message(
                event.getPayload().getBytes(StandardCharsets.UTF_8),
                properties
        );

        CorrelationData correlationData = new CorrelationData(
                event.getId().toString()
        );

        rabbitTemplate.send(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY,
                message,
                correlationData
        );

        CorrelationData.Confirm confirm = correlationData.getFuture()
                .get(10,TimeUnit.SECONDS);

        if(!confirm.ack()){
            throw new IllegalStateException(
                    "RabbitMQ rejeitou o evento: "+confirm.reason()
            );
        }

        if(correlationData.getReturned() != null){
            throw new IllegalStateException(
                    "Evento não foi encaminhado para uma fila."
            );
        }

        event.setPublished(true);
        outboxEventRepository.saveAndFlush(event);

        System.out.println(
                "Evento publicado com sucesso: "+event.getId()
        );
    }
}

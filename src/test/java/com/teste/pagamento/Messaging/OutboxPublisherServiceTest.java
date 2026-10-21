package com.teste.pagamento.Messaging;

import com.teste.pagamento.Config.RabbitMQConfig;
import com.teste.pagamento.Models.OutboxEvent;
import com.teste.pagamento.repositories.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxPublisherServiceTest {
    OutboxEventRepository repository = mock(OutboxEventRepository.class);
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    OutboxPublisherService service = new OutboxPublisherService(repository,rabbit);
    OutboxEvent event(){return new OutboxEvent(UUID.randomUUID(),"PAYMENT_APPROVED","cart-1","{\"status\":\"APROVADO\"}",Instant.now());}

    @Test void publishesAndMarksAsPublishedAfterAck(){
        OutboxEvent e = event();
        when(repository.FindPendingEvents(any(Pageable.class))).thenReturn(List.of(e));
        doAnswer(i -> {
            CorrelationData correlation = i.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true,null));
            return null;
        }).when(rabbit).send(eq(RabbitMQConfig.EXCHANGE),eq(RabbitMQConfig.ROUTING_KEY),any(Message.class),any(CorrelationData.class));
        service.PublishPendingEvents();
        assertTrue(e.isPublished());
        verify(repository).saveAndFlush(e);
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq(RabbitMQConfig.EXCHANGE),eq(RabbitMQConfig.ROUTING_KEY),captor.capture(),any(CorrelationData.class));
        assertEquals(e.getId().toString(),captor.getValue().getMessageProperties().getMessageId());
    }
    @Test void nackDoesNotMarkAsPublished(){
        OutboxEvent e = event();
        when(repository.FindPendingEvents(any(Pageable.class))).thenReturn(List.of(e));
        doAnswer(i -> {
            CorrelationData correlation = i.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false,"rejected"));
            return null;
        }).when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        service.PublishPendingEvents();
        assertFalse(e.isPublished());
        verify(repository,never()).saveAndFlush(any(OutboxEvent.class));
    }
    @Test void noPendingEventsSendsNothing(){
        when(repository.FindPendingEvents(any(Pageable.class))).thenReturn(List.of());
        service.PublishPendingEvents();
        verifyNoInteractions(rabbit);
    }
}

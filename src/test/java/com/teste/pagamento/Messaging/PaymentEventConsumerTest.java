package com.teste.pagamento.Messaging;

import com.teste.pagamento.repositories.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentEventConsumerTest {
    ProcessedEventRepository repository = mock(ProcessedEventRepository.class);
    TransactionTemplate transactions = mock(TransactionTemplate.class);
    PaymentEventConsumer consumer = new PaymentEventConsumer(repository,transactions);
    UUID id = UUID.randomUUID();
    Message message(String messageId){
        MessageProperties props = new MessageProperties();
        props.setMessageId(messageId);
        return new Message("{\"eventType\":\"PAYMENT_APPROVED\"}".getBytes(StandardCharsets.UTF_8),props);
    }
    @BeforeEach void setup(){
        when(transactions.execute(any())).thenAnswer(i -> ((org.springframework.transaction.support.TransactionCallback<?>)i.getArgument(0)).doInTransaction(mock(org.springframework.transaction.TransactionStatus.class)));
    }
    @Test void processesNewEvent(){
        when(repository.InsertIfNotExists(id)).thenReturn(1);
        assertDoesNotThrow(() -> consumer.ConsumePaymentApproved(message(id.toString())));
        verify(repository).InsertIfNotExists(id);
    }
    @Test void ignoresDuplicateEvent(){
        when(repository.InsertIfNotExists(id)).thenReturn(0);
        assertDoesNotThrow(() -> consumer.ConsumePaymentApproved(message(id.toString())));
        verify(repository).InsertIfNotExists(id);
    }
    @Test void rejectsMissingMessageId(){
        assertThrows(IllegalArgumentException.class,() -> consumer.ConsumePaymentApproved(message(null)));
        verifyNoInteractions(repository);
    }
    @Test void propagatesPersistenceFailure(){
        when(repository.InsertIfNotExists(id)).thenThrow(new IllegalStateException("DB unavailable"));
        assertThrows(IllegalStateException.class,() -> consumer.ConsumePaymentApproved(message(id.toString())));
    }
}

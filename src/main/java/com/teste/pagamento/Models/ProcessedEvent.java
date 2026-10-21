
package com.teste.pagamento.Models;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "processed_at",nullable = false)
    private Instant processedAt;

    protected ProcessedEvent(){}

    public ProcessedEvent(UUID eventId){
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }

    public UUID getEventId(){
        return eventId;
    }

    public Instant getProcessedAt(){
        return processedAt;
    }
}

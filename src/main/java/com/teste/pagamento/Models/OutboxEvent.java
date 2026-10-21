
package com.teste.pagamento.Models;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String cartId;

    @Column(nullable = false,columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean published = false;

    protected OutboxEvent(){
    }

    public OutboxEvent(UUID id,String eventType,String cartId,String payload,Instant createdAt){
        this.id = id;
        this.eventType = eventType;
        this.cartId = cartId;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public UUID getId(){
        return id;
    }

    public String getPayload(){
        return payload;
    }

    public boolean isPublished(){
        return published;
    }

    public void setPublished(boolean published){
        this.published = published;
    }
}

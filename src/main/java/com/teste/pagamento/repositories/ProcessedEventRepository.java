
package com.teste.pagamento.repositories;

import com.teste.pagamento.Models.ProcessedEvent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent,UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO processed_events (event_id,processed_at)
            VALUES (:eventId,CURRENT_TIMESTAMP)
            ON CONFLICT (event_id) DO NOTHING
            """,nativeQuery = true)
    int InsertIfNotExists(@Param("eventId") UUID eventId);
}

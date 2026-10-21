
package com.teste.pagamento.repositories;

import com.teste.pagamento.Models.OutboxEvent;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent,UUID> {

    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE e.published = false
            ORDER BY e.createdAt ASC
            """)
    List<OutboxEvent> FindPendingEvents(Pageable pageable);
}

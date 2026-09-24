package com.cafeorbe.streaming.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvento, Long> {

    /** Eventos sin publicar, en orden. SKIP LOCKED (-2) permite varias instancias sin publicar dos veces el mismo lote. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select o from OutboxEvento o where o.publicadoEn is null order by o.id asc")
    List<OutboxEvento> pendientes(Pageable pagina);

    long countByPublicadoEnIsNull();
}

package com.cafeorbe.streaming.service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TransmisionRepository extends JpaRepository<Transmision, UUID> {

    /** Transmisiones activas cuyo emisor falta desde ese instante o antes. */
    List<Transmision> findByActivaTrueAndEmisorAusenteDesdeLessThanEqual(Instant limite);
}

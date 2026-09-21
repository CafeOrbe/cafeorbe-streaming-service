package com.cafeorbe.streaming.service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransmisionRepository extends JpaRepository<Transmision, UUID> {
}

package com.sparsh.sentinel.ingest.transaction;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    Optional<TransactionEntity> findByExternalRef(String externalRef);

    List<TransactionEntity> findByCustomerIdOrderByBookedAtDesc(String customerId, Pageable page);
}

package com.parcelflow.repository;

import com.parcelflow.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderCode(String orderCode);

    long countByCreatedAtGreaterThanEqual(java.time.LocalDateTime start);
}

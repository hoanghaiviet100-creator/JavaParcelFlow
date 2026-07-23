package com.parcelflow.repository;

import com.parcelflow.domain.DeliveryAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, Long> {

    List<DeliveryAssignment> findByShipperIdOrderByAssignedAtDesc(Long shipperId);

    long countByStatusIn(java.util.Collection<com.parcelflow.common.enums.DeliveryAssignmentStatus> statuses);
}

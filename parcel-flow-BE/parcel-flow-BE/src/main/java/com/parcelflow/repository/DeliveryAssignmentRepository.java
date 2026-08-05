package com.parcelflow.repository;

import com.parcelflow.domain.DeliveryAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, Long> {

    List<DeliveryAssignment> findByShipperIdOrderByAssignedAtDesc(Long shipperId);

    long countByStatusIn(java.util.Collection<com.parcelflow.common.enums.DeliveryAssignmentStatus> statuses);

    /** Live assignments for one parcel — used to stop a parcel being handed to two couriers. */
    List<DeliveryAssignment> findByParcelIdAndStatusIn(
            Long parcelId, java.util.Collection<com.parcelflow.common.enums.DeliveryAssignmentStatus> statuses);

    /** How much a courier is already carrying, for the dispatcher's picker. */
    long countByShipperIdAndStatusIn(
            Long shipperId, java.util.Collection<com.parcelflow.common.enums.DeliveryAssignmentStatus> statuses);
}

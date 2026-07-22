package com.parcelflow.repository;

import com.parcelflow.common.enums.PartyType;
import com.parcelflow.domain.OrderParty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderPartyRepository extends JpaRepository<OrderParty, Long> {
    List<OrderParty> findByOrderId(Long orderId);
    Optional<OrderParty> findByOrderIdAndPartyType(Long orderId, PartyType partyType);
}

package com.parcelflow.repository;

import com.parcelflow.domain.ShipperZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShipperZoneRepository extends JpaRepository<ShipperZone, Long> {
}

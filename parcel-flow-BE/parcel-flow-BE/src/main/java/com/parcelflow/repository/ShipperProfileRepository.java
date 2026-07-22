package com.parcelflow.repository;

import com.parcelflow.domain.ShipperProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShipperProfileRepository extends JpaRepository<ShipperProfile, Long> {
}

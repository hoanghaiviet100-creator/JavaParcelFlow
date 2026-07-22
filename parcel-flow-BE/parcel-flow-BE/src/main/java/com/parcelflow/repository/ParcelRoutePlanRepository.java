package com.parcelflow.repository;

import com.parcelflow.domain.ParcelRoutePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParcelRoutePlanRepository extends JpaRepository<ParcelRoutePlan, Long> {
}

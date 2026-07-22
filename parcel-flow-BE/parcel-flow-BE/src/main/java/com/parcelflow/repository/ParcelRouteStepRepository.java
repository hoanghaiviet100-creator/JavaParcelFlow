package com.parcelflow.repository;

import com.parcelflow.domain.ParcelRouteStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParcelRouteStepRepository extends JpaRepository<ParcelRouteStep, Long> {

    List<ParcelRouteStep> findByParcelRoutePlanIdOrderBySequenceNoAsc(Long parcelRoutePlanId);
}

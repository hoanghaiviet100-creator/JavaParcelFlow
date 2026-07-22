package com.parcelflow.repository;

import com.parcelflow.domain.HubServiceArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HubServiceAreaRepository extends JpaRepository<HubServiceArea, Long> {
}

package com.parcelflow.repository;

import com.parcelflow.domain.HubScan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HubScanRepository extends JpaRepository<HubScan, Long> {
}

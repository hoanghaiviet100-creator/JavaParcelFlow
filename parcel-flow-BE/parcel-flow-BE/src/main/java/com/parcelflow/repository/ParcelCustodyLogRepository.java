package com.parcelflow.repository;

import com.parcelflow.domain.ParcelCustodyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParcelCustodyLogRepository extends JpaRepository<ParcelCustodyLog, Long> {
}

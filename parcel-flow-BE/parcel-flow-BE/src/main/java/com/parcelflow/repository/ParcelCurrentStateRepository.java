package com.parcelflow.repository;

import com.parcelflow.domain.ParcelCurrentState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParcelCurrentStateRepository extends JpaRepository<ParcelCurrentState, Long> {
}

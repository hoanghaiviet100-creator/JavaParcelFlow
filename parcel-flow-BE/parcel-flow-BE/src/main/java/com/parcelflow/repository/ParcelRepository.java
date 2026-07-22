package com.parcelflow.repository;

import com.parcelflow.domain.Parcel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParcelRepository extends JpaRepository<Parcel, Long> {
    List<Parcel> findByOrderId(Long orderId);

    Optional<Parcel> findByParcelCode(String parcelCode);
}

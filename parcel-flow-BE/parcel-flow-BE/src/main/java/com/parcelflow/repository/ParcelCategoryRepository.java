package com.parcelflow.repository;

import com.parcelflow.domain.ParcelCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParcelCategoryRepository extends JpaRepository<ParcelCategory, Long> {
}

package com.employee.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.employee.entity.EmpOnboardingStatusView;

@Repository
public interface EmpOnboardingStatusViewRepository extends JpaRepository<EmpOnboardingStatusView, Integer> {

    @Query("SELECT e FROM EmpOnboardingStatusView e WHERE e.category_id = :categoryId")
    List<EmpOnboardingStatusView> findByCategoryId(@Param("categoryId") Integer categoryId);
}
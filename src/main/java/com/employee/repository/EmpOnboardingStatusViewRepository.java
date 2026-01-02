package com.employee.repository;
 
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
 
import com.employee.entity.EmpOnboardingStatusView;
 
@Repository
public interface EmpOnboardingStatusViewRepository extends JpaRepository<EmpOnboardingStatusView, Integer>{
 
}
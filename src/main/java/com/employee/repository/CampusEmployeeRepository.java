package com.employee.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.employee.entity.CampusEmployee;

@Repository
public interface CampusEmployeeRepository extends JpaRepository<CampusEmployee, Integer> {
    // Add custom query methods if needed
}

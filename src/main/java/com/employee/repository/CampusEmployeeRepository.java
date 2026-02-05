package com.employee.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.employee.entity.CampusEmployee;

@Repository
public interface CampusEmployeeRepository extends JpaRepository<CampusEmployee, Integer> {
    // Find all campus mappings for a specific employee
    @org.springframework.data.jpa.repository.Query("SELECT ce FROM CampusEmployee ce WHERE ce.empId.emp_id = :empId")
    java.util.List<CampusEmployee> findByEmpId(@org.springframework.data.repository.query.Param("empId") Integer empId);
}

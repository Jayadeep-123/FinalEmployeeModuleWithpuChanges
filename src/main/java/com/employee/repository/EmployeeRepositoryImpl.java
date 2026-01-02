package com.employee.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.employee.dto.AdvancedEmployeeSearchRequestDTO;
import com.employee.dto.EmployeeSearchRequestDTO;
import com.employee.dto.EmployeeSearchResponseDTO;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Repository
public class EmployeeRepositoryImpl implements EmployeeRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<EmployeeSearchResponseDTO> searchEmployeesDynamic(EmployeeSearchRequestDTO searchRequest, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        
        // Create criteria query for DTO projection
        CriteriaQuery<EmployeeSearchResponseDTO> query = cb.createQuery(EmployeeSearchResponseDTO.class);
        Root<com.employee.entity.Employee> e = query.from(com.employee.entity.Employee.class);
        
        // Joins
        Join<?, ?> d = e.join("department", JoinType.INNER);
        Join<?, ?> c = e.join("campus_id", JoinType.INNER);
        Join<?, ?> city = c.join("city", JoinType.INNER);
        Join<?, ?> moh = e.join("modeOfHiring_id", JoinType.LEFT);
        
        // Build SELECT clause (DTO projection)
        query.select(cb.construct(
            EmployeeSearchResponseDTO.class,
            e.get("emp_id"),
            cb.concat(
                cb.concat(
                    cb.coalesce(e.get("first_name"), cb.literal("")),
                    cb.literal(" ")
                ),
                cb.coalesce(e.get("last_name"), cb.literal(""))
            ),
            e.get("payRollId"),
            cb.coalesce(d.get("department_name"), cb.literal("N/A")),
            cb.coalesce(moh.get("mode_of_hiring_name"), cb.literal("N/A")),
            e.get("tempPayrollId")
        ));
        
        // Build WHERE clause dynamically
        List<Predicate> predicates = new ArrayList<>();
        
        // Always required: payrollId and is_active = 1
        predicates.add(cb.equal(e.get("payRollId"), searchRequest.getPayrollId()));
        predicates.add(cb.equal(e.get("is_active"), 1));
        
        // Optional: cityId
        if (searchRequest.getCityId() != null) {
            predicates.add(cb.equal(city.get("cityId"), searchRequest.getCityId()));
        }
        
        // Optional: employeeTypeId
        Join<?, ?> et = null;
        if (searchRequest.getEmployeeTypeId() != null) {
            et = e.join("employee_type_id", JoinType.INNER);
            predicates.add(cb.equal(et.get("emp_type_id"), searchRequest.getEmployeeTypeId()));
        }
        
        query.where(predicates.toArray(new Predicate[0]));
        
        // Execute query with pagination
        TypedQuery<EmployeeSearchResponseDTO> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        
        List<EmployeeSearchResponseDTO> results = typedQuery.getResultList();
        
        // Get total count for pagination
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<com.employee.entity.Employee> countRoot = countQuery.from(com.employee.entity.Employee.class);
        Join<?, ?> countC = countRoot.join("campus_id", JoinType.INNER);
        Join<?, ?> countCity = countC.join("city", JoinType.INNER);
        
        List<Predicate> countPredicates = new ArrayList<>();
        countPredicates.add(cb.equal(countRoot.get("payRollId"), searchRequest.getPayrollId()));
        countPredicates.add(cb.equal(countRoot.get("is_active"), 1));
        
        if (searchRequest.getCityId() != null) {
            countPredicates.add(cb.equal(countCity.get("cityId"), searchRequest.getCityId()));
        }
        
        if (searchRequest.getEmployeeTypeId() != null) {
            Join<?, ?> countEt = countRoot.join("employee_type_id", JoinType.INNER);
            countPredicates.add(cb.equal(countEt.get("emp_type_id"), searchRequest.getEmployeeTypeId()));
        }
        
        countQuery.select(cb.count(countRoot));
        countQuery.where(countPredicates.toArray(new Predicate[0]));
        Long total = entityManager.createQuery(countQuery).getSingleResult();
        
        return new org.springframework.data.domain.PageImpl<>(results, pageable, total);
    }

    @Override
    public Page<EmployeeSearchResponseDTO> searchEmployeesAdvancedDynamic(AdvancedEmployeeSearchRequestDTO searchRequest, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        
        // Create criteria query for DTO projection
        CriteriaQuery<EmployeeSearchResponseDTO> query = cb.createQuery(EmployeeSearchResponseDTO.class);
        Root<com.employee.entity.Employee> e = query.from(com.employee.entity.Employee.class);
        
        // Required joins
        Join<?, ?> d = e.join("department", JoinType.INNER);
        Join<?, ?> c = e.join("campus_id", JoinType.INNER);
        Join<?, ?> moh = e.join("modeOfHiring_id", JoinType.LEFT);
        
        // Optional joins (will be added conditionally)
        Join<?, ?> city = null;
        Join<?, ?> s = null;
        Join<?, ?> et = null;
        
        // Build SELECT clause (DTO projection)
        query.select(cb.construct(
            EmployeeSearchResponseDTO.class,
            e.get("emp_id"),
            cb.concat(
                cb.concat(
                    cb.coalesce(e.get("first_name"), cb.literal("")),
                    cb.literal(" ")
                ),
                cb.coalesce(e.get("last_name"), cb.literal(""))
            ),
            e.get("payRollId"),
            cb.coalesce(d.get("department_name"), cb.literal("N/A")),
            cb.coalesce(moh.get("mode_of_hiring_name"), cb.literal("N/A")),
            e.get("tempPayrollId")
        ));
        
        // Build WHERE clause dynamically
        List<Predicate> predicates = new ArrayList<>();
        
        // Always required: payrollId and is_active = 1
        predicates.add(cb.equal(e.get("payRollId"), searchRequest.getPayrollId()));
        predicates.add(cb.equal(e.get("is_active"), 1));
        
        // Optional: stateId
        if (searchRequest.getStateId() != null) {
            s = c.join("state", JoinType.INNER);
            predicates.add(cb.equal(s.get("stateId"), searchRequest.getStateId()));
        }
        
        // Optional: cityId
        if (searchRequest.getCityId() != null) {
            city = c.join("city", JoinType.INNER);
            predicates.add(cb.equal(city.get("cityId"), searchRequest.getCityId()));
        }
        
        // Optional: campusId
        if (searchRequest.getCampusId() != null) {
            predicates.add(cb.equal(c.get("campusId"), searchRequest.getCampusId()));
        }
        
        // Optional: departmentId
        if (searchRequest.getDepartmentId() != null) {
            predicates.add(cb.equal(d.get("department_id"), searchRequest.getDepartmentId()));
        }
        
        // Optional: employeeTypeId
        if (searchRequest.getEmployeeTypeId() != null) {
            et = e.join("employee_type_id", JoinType.INNER);
            predicates.add(cb.equal(et.get("emp_type_id"), searchRequest.getEmployeeTypeId()));
        }
        
        query.where(predicates.toArray(new Predicate[0]));
        
        // Execute query with pagination
        TypedQuery<EmployeeSearchResponseDTO> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        
        List<EmployeeSearchResponseDTO> results = typedQuery.getResultList();
        
        // Get total count for pagination
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<com.employee.entity.Employee> countRoot = countQuery.from(com.employee.entity.Employee.class);
        Join<?, ?> countD = countRoot.join("department", JoinType.INNER);
        Join<?, ?> countC = countRoot.join("campus_id", JoinType.INNER);
        
        List<Predicate> countPredicates = new ArrayList<>();
        countPredicates.add(cb.equal(countRoot.get("payRollId"), searchRequest.getPayrollId()));
        countPredicates.add(cb.equal(countRoot.get("is_active"), 1));
        
        if (searchRequest.getStateId() != null) {
            Join<?, ?> countS = countC.join("state", JoinType.INNER);
            countPredicates.add(cb.equal(countS.get("stateId"), searchRequest.getStateId()));
        }
        
        if (searchRequest.getCityId() != null) {
            Join<?, ?> countCity = countC.join("city", JoinType.INNER);
            countPredicates.add(cb.equal(countCity.get("cityId"), searchRequest.getCityId()));
        }
        
        if (searchRequest.getCampusId() != null) {
            countPredicates.add(cb.equal(countC.get("campusId"), searchRequest.getCampusId()));
        }
        
        if (searchRequest.getDepartmentId() != null) {
            countPredicates.add(cb.equal(countD.get("department_id"), searchRequest.getDepartmentId()));
        }
        
        if (searchRequest.getEmployeeTypeId() != null) {
            Join<?, ?> countEt = countRoot.join("employee_type_id", JoinType.INNER);
            countPredicates.add(cb.equal(countEt.get("emp_type_id"), searchRequest.getEmployeeTypeId()));
        }
        
        countQuery.select(cb.count(countRoot));
        countQuery.where(countPredicates.toArray(new Predicate[0]));
        Long total = entityManager.createQuery(countQuery).getSingleResult();
        
        return new org.springframework.data.domain.PageImpl<>(results, pageable, total);
    }
}


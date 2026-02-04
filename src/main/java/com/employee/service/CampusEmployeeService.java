package com.employee.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.employee.dto.CampusEmployeeDTO;
import com.employee.entity.Campus;
import com.employee.entity.CampusEmployee;
import com.employee.entity.Employee;
import com.employee.exception.ResourceNotFoundException;
import com.employee.repository.CampusEmployeeRepository;
import com.employee.repository.CampusRepository;
import com.employee.repository.EmployeeRepository;

@Service
public class CampusEmployeeService {

    private final CampusEmployeeRepository campusEmployeeRepository;
    private final EmployeeRepository employeeRepository;
    private final CampusRepository campusRepository;

    public CampusEmployeeService(CampusEmployeeRepository campusEmployeeRepository,
            EmployeeRepository employeeRepository,
            CampusRepository campusRepository) {
        this.campusEmployeeRepository = campusEmployeeRepository;
        this.employeeRepository = employeeRepository;
        this.campusRepository = campusRepository;
    }

    @Transactional
    public List<CampusEmployeeDTO> assignCampusesToEmployee(List<CampusEmployeeDTO> campusEmployeeDTOs) {
        if (campusEmployeeDTOs == null || campusEmployeeDTOs.isEmpty()) {
            throw new IllegalArgumentException("Campus assignment list cannot be empty");
        }

        List<CampusEmployeeDTO> savedDTOs = new ArrayList<>();

        for (CampusEmployeeDTO dto : campusEmployeeDTOs) {
            // Validate Employee
            Employee employee = employeeRepository.findById(dto.getEmpId())
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + dto.getEmpId()));

            // Validate Campus
            Campus campus = campusRepository.findById(dto.getCmpsId())
                    .orElseThrow(() -> new ResourceNotFoundException("Campus not found with ID: " + dto.getCmpsId()));

            CampusEmployee entity = new CampusEmployee();
            // If updating existing mapping
            if (dto.getCmpsEmployeeId() != null && dto.getCmpsEmployeeId() > 0) {
                entity = campusEmployeeRepository.findById(dto.getCmpsEmployeeId())
                        .orElse(new CampusEmployee());
            }

            entity.setEmpId(employee);
            entity.setCmpsId(campus);
            entity.setRoleId(dto.getRoleId());
            entity.setAttendanceStatus(dto.getAttendanceStatus());
            entity.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : 1);

            // Audit fields
            if (entity.getCmpsEmployeeId() == null) {
                entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : 1);
                // createdDate is handled by DB default or @PrePersist if JPA, but DB has
                // default CURRENT_TIMESTAMP
            } else {
                entity.setUpdatedBy(dto.getUpdatedBy());
                entity.setUpdatedDate(LocalDateTime.now());
            }

            CampusEmployee savedEntity = campusEmployeeRepository.save(entity);
            savedDTOs.add(mapToDTO(savedEntity));
        }

        return savedDTOs;
    }

    private CampusEmployeeDTO mapToDTO(CampusEmployee entity) {
        return new CampusEmployeeDTO(
                entity.getCmpsEmployeeId(),
                entity.getEmpId().getEmp_id(),
                entity.getCmpsId().getCampusId(),
                entity.getRoleId(),
                entity.getAttendanceStatus(),
                entity.getIsActive(),
                entity.getCreatedBy(),
                entity.getUpdatedBy());
    }
}

package com.employee.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import com.employee.repository.SharedEmployeeRepository;

@Service
public class CampusEmployeeService {

    private final CampusEmployeeRepository campusEmployeeRepository;
    private final EmployeeRepository employeeRepository;
    private final CampusRepository campusRepository;
    private final SharedEmployeeRepository sharedEmployeeRepository;

    public CampusEmployeeService(CampusEmployeeRepository campusEmployeeRepository,
            EmployeeRepository employeeRepository,
            CampusRepository campusRepository,
            SharedEmployeeRepository sharedEmployeeRepository) {
        this.campusEmployeeRepository = campusEmployeeRepository;
        this.employeeRepository = employeeRepository;
        this.campusRepository = campusRepository;
        this.sharedEmployeeRepository = sharedEmployeeRepository;
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
            } else {
                entity.setUpdatedBy(dto.getUpdatedBy());
                entity.setUpdatedDate(LocalDateTime.now());
            }

            CampusEmployee savedEntity = campusEmployeeRepository.save(entity);
            savedDTOs.add(mapToDTO(savedEntity));
        }

        return savedDTOs;
    }

    // Helper method to convert Entity to DTO
    private com.employee.dto.CampusEmployeeDTO convertToDTO(CampusEmployee entity) {
        return new com.employee.dto.CampusEmployeeDTO(
                entity.getCmpsEmployeeId(),
                entity.getEmpId().getEmp_id(),
                entity.getCmpsId().getCampusId(),
                entity.getRoleId(),
                entity.getAttendanceStatus(),
                entity.getIsActive(),
                entity.getCreatedBy(),
                entity.getUpdatedBy());
    }

    /**
     * Get Employee Location Details (State, City, Campus)
     * Logic:
     * 1. Check sce_cmps_emp table (CampusEmployee).
     * 2. If nothing found, Check sce_shared_employee table (SharedEmployee).
     * 3. If nothing found, Check Employee table (Return primary campus).
     */
    public com.employee.dto.EmployeeLocationDTO getEmployeeLocation(Integer empId) {
        com.employee.dto.EmployeeLocationDTO locationDTO = new com.employee.dto.EmployeeLocationDTO();
        List<com.employee.dto.CampusInfoDTO> campusInfoList = new java.util.ArrayList<>();
        Set<Integer> addedCampusIds = new HashSet<>();

        // Initialize State/City from Primary Campus if available
        com.employee.entity.Employee employee = employeeRepository.findById(empId).orElse(null);
        if (employee != null && employee.getCampus_id() != null) {
            Campus primaryCampus = employee.getCampus_id();

            if (primaryCampus.getState() != null) {
                locationDTO.setStateId(primaryCampus.getState().getStateId());
                locationDTO.setStateName(primaryCampus.getState().getStateName());
            }
            if (primaryCampus.getCity() != null) {
                locationDTO.setCityId(primaryCampus.getCity().getCityId());
                locationDTO.setCityName(primaryCampus.getCity().getCityName());
            }

            // Always add Primary Campus to the list first
            addCampusToDTO(primaryCampus, campusInfoList, locationDTO);
            addedCampusIds.add(primaryCampus.getCampusId());
        }

        // Step 1: Check CampusEmployee table (New implementation for non-shared
        // multi-campus)
        List<CampusEmployee> multiCampuses = campusEmployeeRepository.findByEmpId(empId);

        if (multiCampuses != null && !multiCampuses.isEmpty()) {
            // Case A: Found in CampusEmployee
            for (CampusEmployee ce : multiCampuses) {
                if (ce.getCmpsId() != null && !addedCampusIds.contains(ce.getCmpsId().getCampusId())) {
                    addCampusToDTO(ce.getCmpsId(), campusInfoList, locationDTO);
                    addedCampusIds.add(ce.getCmpsId().getCampusId());
                }
            }
        } else {
            // Step 2: Check SharedEmployee table (Legacy/Other shared implementation)
            List<com.employee.entity.SharedEmployee> sharedEmployees = sharedEmployeeRepository
                    .findActiveByEmpId(empId);

            if (sharedEmployees != null && !sharedEmployees.isEmpty()) {
                // Case B: Found in SharedEmployee
                for (com.employee.entity.SharedEmployee se : sharedEmployees) {
                    if (se.getCmpsId() != null && !addedCampusIds.contains(se.getCmpsId().getCampusId())) {
                        addCampusToDTO(se.getCmpsId(), campusInfoList, locationDTO);
                        addedCampusIds.add(se.getCmpsId().getCampusId());
                    }
                }
            }
        }

        locationDTO.setCampuses(campusInfoList);
        return locationDTO;
    }

    private void addCampusToDTO(Campus campus, List<com.employee.dto.CampusInfoDTO> list,
            com.employee.dto.EmployeeLocationDTO locationDTO) {
        if (campus != null) {
            list.add(new com.employee.dto.CampusInfoDTO(campus.getCampusId(), campus.getCampusName()));

            // Set State/City from the first campus encountered
            if (locationDTO.getStateId() == null) {
                if (campus.getState() != null) {
                    locationDTO.setStateId(campus.getState().getStateId());
                    locationDTO.setStateName(campus.getState().getStateName());
                }
                if (campus.getCity() != null) {
                    locationDTO.setCityId(campus.getCity().getCityId());
                    locationDTO.setCityName(campus.getCity().getCityName());
                }
            }
        }
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

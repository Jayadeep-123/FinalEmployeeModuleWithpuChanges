package com.employee.service;

import java.time.LocalDateTime;
import java.util.Optional;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.employee.dto.EmpRecentSearchDTO;
import com.employee.entity.EmpRecentSearch;
import com.employee.repository.EmpRecentSearchRepository;
import com.employee.repository.EmployeeRepository;

import jakarta.transaction.Transactional;

@Service
public class EmpRecentSearchService {

    @Autowired
    private EmpRecentSearchRepository empRecentSearchRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Transactional
    public void saveRecentSearch(EmpRecentSearchDTO dto) {
        LocalDateTime now = LocalDateTime.now();

        // Cleanse "string" values from frontend/Swagger defaults
        dto.setEmpName(cleanseString(dto.getEmpName()));
        dto.setPayrollId(cleanseString(dto.getPayrollId()));
        dto.setTempPayrollId(cleanseString(dto.getTempPayrollId()));
        dto.setDepartmentName(cleanseString(dto.getDepartmentName()));
        dto.setJoinType(cleanseString(dto.getJoinType()));
        dto.setLevelName(cleanseString(dto.getLevelName()));
        dto.setPhotoPath(cleanseString(dto.getPhotoPath()));

        if (dto.getLogInEmpId() != null && dto.getEmpId() != null) {
            // 1. Check if this user has EVER searched for this employee before (Across
            // sessions)
            Optional<EmpRecentSearch> existingRecord = empRecentSearchRepository
                    .findTopByLogInEmployee_EmpIdAndEmployee_EmpIdOrderByLogInDesc(dto.getLogInEmpId(), dto.getEmpId());

            if (existingRecord.isPresent()) {
                // Refresh existing record
                EmpRecentSearch entity = existingRecord.get();
                entity.setLogIn(now);
                entity.setLogOut(null); // Clear logout to make it the "active" search
                entity.setUpdatedBy(dto.getCreatedBy());
                entity.setUpdatedDate(now);

                // Refresh other fields as well in case they changed (Department, Level, etc.)
                entity.setDepartmentName(dto.getDepartmentName());
                entity.setLevelName(dto.getLevelName());
                entity.setJoinType(dto.getJoinType());
                entity.setPhotoPath(dto.getPhotoPath());

                empRecentSearchRepository.save(entity);
                return;
            }
        }

        // 3. Create and save new search record
        EmpRecentSearch entity = new EmpRecentSearch();

        if (dto.getLogInEmpId() != null) {
            entity.setLogInEmployee(employeeRepository.getReferenceById(dto.getLogInEmpId()));
        }

        if (dto.getEmpId() != null) {
            entity.setEmployee(employeeRepository.getReferenceById(dto.getEmpId()));
        }

        entity.setEmpName(dto.getEmpName());
        entity.setPayrollId(dto.getPayrollId());
        entity.setTempPayrollId(dto.getTempPayrollId());
        entity.setDepartmentName(dto.getDepartmentName());
        entity.setJoinType(dto.getJoinType());
        entity.setLevelName(dto.getLevelName());
        entity.setLogIn(now);
        entity.setLogOut(null); // Active session
        entity.setPhotoPath(dto.getPhotoPath());
        entity.setCreatedBy(dto.getCreatedBy());
        entity.setCreatedDate(now);

        empRecentSearchRepository.save(entity);
    }

    private String cleanseString(String value) {
        if (value == null || "string".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }

    @Transactional
    public void updateFinalLogout(Integer loginEmpId) {
        LocalDateTime now = LocalDateTime.now();
        empRecentSearchRepository.updateLogOutTimeForPendingRecords(loginEmpId, now);
    }

    public List<EmpRecentSearchDTO> getRecentSearches(Integer loginEmpId) {
        List<EmpRecentSearch> entities = empRecentSearchRepository
                .findByLogInEmployee_EmpIdOrderByLogInDesc(loginEmpId);
        return entities.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private EmpRecentSearchDTO mapToDTO(EmpRecentSearch entity) {
        EmpRecentSearchDTO dto = new EmpRecentSearchDTO();
        dto.setEmpRecentSearchId(entity.getEmpRecentSearchId());
        dto.setLogInEmpId(entity.getLogInEmployee() != null ? entity.getLogInEmployee().getEmp_id() : null);
        dto.setEmpId(entity.getEmployee() != null ? entity.getEmployee().getEmp_id() : null);
        dto.setEmpName(entity.getEmpName());
        dto.setPayrollId(entity.getPayrollId());
        dto.setTempPayrollId(entity.getTempPayrollId());
        dto.setDepartmentName(entity.getDepartmentName());
        dto.setJoinType(entity.getJoinType());
        dto.setLevelName(entity.getLevelName());
        dto.setLogIn(entity.getLogIn());
        dto.setLogOut(entity.getLogOut());
        dto.setPhotoPath(entity.getPhotoPath());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedDate(entity.getUpdatedDate());
        return dto;
    }
}

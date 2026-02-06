package com.employee.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.employee.dto.BulkManagerMappingDTO;
import com.employee.dto.BulkUnmappingDTO;
import com.employee.dto.CampusDetailDTO;
import com.employee.dto.CampusMappingDTO;
import com.employee.dto.EmployeeBatchCampusDTO;
import com.employee.dto.EmployeeCampusAddressDTO;
import com.employee.dto.ManagerMappingDTO;
import com.employee.dto.UnmappingDTO;
import com.employee.entity.Building;
import com.employee.entity.BuildingAddress;
import com.employee.entity.Campus;
import com.employee.entity.City;
import com.employee.entity.Department;
import com.employee.entity.Designation;
import com.employee.entity.Employee;
import com.employee.entity.SharedEmployee;
import com.employee.entity.Subject;
import com.employee.exception.ResourceNotFoundException;
import com.employee.repository.BuildingAddressRepository;
import com.employee.repository.BuildingRepository;
import com.employee.repository.CampusRepository;
import com.employee.repository.CityRepository;
import com.employee.repository.DepartmentRepository;
import com.employee.repository.DesignationRepository;
import com.employee.repository.EmployeeRepository;
import com.employee.repository.SharedEmployeeRepository;
import com.employee.repository.SubjectRepository;
import com.employee.repository.RoleRepository;

/**
 * Service for Manager Mapping functionality.
 * Handles the hierarchy: City → Campus → Department → Designation → Employees
 * and updates work starting date for selected employees.
 */
@Service
public class ManagerMappingService {

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private DesignationRepository designationRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SharedEmployeeRepository sharedEmployeeRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private BuildingAddressRepository buildingAddressRepository;

    @Autowired
    private RoleRepository roleRepository;

    /**
     * Maps employee based on payrollId and updates their details.
     * 
     * Flow:
     * 1. Validate City
     * 2. Validate Campus (must belong to City)
     * 3. Validate Department exists and is active (master table - independent)
     * 4. Validate Designation exists and is active
     * 5. Find Employee by payrollId
     * 6. Validate Employee is active
     * 7. Validate Manager (managerId) exists and is active (if provided)
     * 8. Validate Reporting Manager (reportingManagerId) exists and is active (if
     * provided)
     * 9. Update employee: campus, department, designation, manager, reporting
     * manager, work starting date, remarks
     * 
     * @param mappingDTO The manager mapping request DTO
     * @return The same ManagerMappingDTO that was passed in
     */
    @Transactional
    public ManagerMappingDTO mapEmployeesAndUpdateWorkDate(ManagerMappingDTO mappingDTO) {
        // Validate required fields
        validateMappingDTO(mappingDTO);

        // Step 1: Validate City exists
        cityRepository.findById(mappingDTO.getCityId())
                .orElseThrow(() -> new ResourceNotFoundException("City not found with ID: " + mappingDTO.getCityId()));

        // Step 2: Check if using multiple campuses or single campus
        // Always use campusMappings array - required field
        List<CampusMappingDTO> campusMappingsList = mappingDTO.getCampusMappings();
        if (campusMappingsList == null || campusMappingsList.isEmpty()) {
            throw new IllegalArgumentException("campusMappings array is required and cannot be empty");
        }

        boolean useMultipleCampuses = campusMappingsList.size() > 1;

        // Step 4: Find Employee by payrollId
        Employee employee = findEmployeeByPayrollId(mappingDTO.getPayrollId());

        // Step 5: Validate Employee is active
        if (employee.getIs_active() != 1) {
            throw new ResourceNotFoundException(
                    "Employee with payrollId " + mappingDTO.getPayrollId() + " is not active");
        }

        // Step 6: Get employee's existing campus
        Campus employeeCampus = employee.getCampus_id();
        // Removed validation to allow first-time mapping

        // Step 7: Handle single campus or multiple campuses
        // ALWAYS create/update SharedEmployee records regardless of list size
        Designation designation;

        // Step 3: Process campus mappings and validate
        Department department = null;

        // Use the first campus as the primary campus for the Employee table
        CampusMappingDTO firstMapping = campusMappingsList.get(0);
        Campus requestedPrimaryCampus = campusRepository
                .findByCampusIdAndIsActive(firstMapping.getCampusId(), 1)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active Campus not found with ID: " + firstMapping.getCampusId()));

        // Only update the local helper variable if employee doesn't have a campus
        if (employeeCampus == null) {
            employeeCampus = requestedPrimaryCampus;
        }

        // Verify primary department/designation for Employee table
        Department primaryDepartment = departmentRepository
                .findByIdAndIsActive(firstMapping.getDepartmentId(), 1)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active Department not found with ID: " + firstMapping.getDepartmentId()));
        department = primaryDepartment;

        Designation primaryDesignation = designationRepository
                .findByIdAndIsActive(firstMapping.getDesignationId(), 1)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active Designation not found with ID: " + firstMapping.getDesignationId()));
        designation = primaryDesignation;

        // Loop through ALL mappings (whether 1 or many) and update SharedEmployee table
        for (CampusMappingDTO campusMapping : campusMappingsList) {
            // Validate Department exists and is active
            // (Re-validating loop items to ensure data integrity for SharedEmployee)
            Department campusDepartment = departmentRepository
                    .findByIdAndIsActive(campusMapping.getDepartmentId(), 1)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Active Department not found with ID: " + campusMapping.getDepartmentId()));

            // Validate Campus is active and exists in the City
            Campus campus = campusRepository.findByCampusIdAndIsActive(campusMapping.getCampusId(), 1)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Active Campus not found with ID: " + campusMapping.getCampusId()));

            if (campus.getCity() == null || campus.getCity().getCityId() != mappingDTO.getCityId()) {
                throw new ResourceNotFoundException(
                        String.format("Campus with ID %d is not assigned to City with ID %d",
                                campusMapping.getCampusId(), mappingDTO.getCityId()));
            }

            // Validate Designation exists, is active, and belongs to the Department
            Designation campusDesignation = designationRepository
                    .findByIdAndIsActive(campusMapping.getDesignationId(), 1)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Active Designation not found with ID: " + campusMapping.getDesignationId()));

            if (campusDesignation.getDepartment() == null
                    || campusDesignation.getDepartment().getDepartment_id() != campusMapping.getDepartmentId()) {
                throw new ResourceNotFoundException(
                        String.format("Designation with ID %d does not belong to Department with ID %d",
                                campusMapping.getDesignationId(), campusMapping.getDepartmentId()));
            }

            // Create or update SharedEmployee record for ALL campuses
            saveOrUpdateSharedEmployee(employee, campus, campusDesignation, campusMapping.getSubjectId(),
                    mappingDTO.getUpdatedBy());
        }

        // Step 8: Assign Manager - only if provided
        // Treat 0 as null
        Employee manager = null;
        Integer managerIdValue = mappingDTO.getManagerId();
        if (managerIdValue != null && managerIdValue != 0) {
            // Manager ID provided - validate it
            final Integer managerId = managerIdValue;
            manager = employeeRepository.findById(managerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found with ID: " + managerId));

            if (manager.getIs_active() != 1) {
                throw new ResourceNotFoundException("Manager with ID " + managerId + " is not active");
            }

            // Validate manager is from the same CITY as employee (primary campus city)
            if (manager.getCampus_id() == null || manager.getCampus_id().getCity() == null
                    || manager.getCampus_id().getCity().getCityId() != employeeCampus.getCity().getCityId()) {
                throw new ResourceNotFoundException(
                        String.format(
                                "Manager with ID %d is not from the same CITY as employee. Employee city ID: %d, Manager city ID: %s",
                                managerId, employeeCampus.getCity().getCityId(),
                                (manager.getCampus_id() != null && manager.getCampus_id().getCity() != null)
                                        ? String.valueOf(manager.getCampus_id().getCity().getCityId())
                                        : "null"));
            }
        }

        // Step 9: Assign Reporting Manager - only if provided
        // Treat 0 as null
        Employee reportingManager = null;
        Integer reportingManagerIdValue = mappingDTO.getReportingManagerId();
        if (reportingManagerIdValue != null && reportingManagerIdValue != 0) {
            // Reporting Manager ID provided - validate it
            final Integer reportingManagerId = reportingManagerIdValue;
            reportingManager = employeeRepository.findById(reportingManagerId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Reporting Manager not found with ID: " + reportingManagerId));

            if (reportingManager.getIs_active() != 1) {
                throw new ResourceNotFoundException(
                        "Reporting Manager with ID " + reportingManagerId + " is not active");
            }

            // Validate reporting manager is from the same CITY as employee (primary campus
            // city)
            if (reportingManager.getCampus_id() == null || reportingManager.getCampus_id().getCity() == null
                    || reportingManager.getCampus_id().getCity().getCityId() != employeeCampus.getCity().getCityId()) {
                throw new ResourceNotFoundException(
                        String.format(
                                "Reporting Manager with ID %d is not from the same CITY as employee. Employee city ID: %d, Reporting Manager city ID: %s",
                                reportingManagerId, employeeCampus.getCity().getCityId(),
                                (reportingManager.getCampus_id() != null
                                        && reportingManager.getCampus_id().getCity() != null)
                                                ? String.valueOf(reportingManager.getCampus_id().getCity().getCityId())
                                                : "null"));
            }
        }

        // Step 8: Update employee fields

        // Only update primary campus/dept/designation if employee didn't have a campus
        if (employee.getCampus_id() == null) {
            employee.setCampus_id(employeeCampus);
            employee.setDepartment(department);
            employee.setDesignation(designation);
        }

        // Update manager_id if provided
        if (manager != null) {
            employee.setEmployee_manager_id(manager);
        }

        // Update reporting_manager_id if provided
        if (reportingManager != null) {
            employee.setEmployee_reporting_id(reportingManager);
        }
        // Update contract_start_date with workStartingDate
        employee.setContract_start_date(mappingDTO.getWorkStartingDate());
        employee.setUpdated_by(mappingDTO.getUpdatedBy() != null ? mappingDTO.getUpdatedBy() : 1);
        employee.setUpdated_date(LocalDateTime.now());

        // Update remarks: if value exists and is not empty, set it; otherwise set to
        // null
        // Treat null, empty string, whitespace-only, or string "null" as null
        String remarkValue = mappingDTO.getRemark();
        if (remarkValue != null && !remarkValue.trim().isEmpty() && !remarkValue.trim().equalsIgnoreCase("null")) {
            employee.setRemarks(remarkValue.trim());
        } else {
            employee.setRemarks(null);
        }

        employeeRepository.save(employee);

        return mappingDTO; // Return the request DTO
    }

    /**
     * Maps multiple employees based on payrollIds and updates their details with
     * the same values.
     * 
     * Flow:
     * 1. Validate City
     * 2. Validate Campus (must belong to City)
     * 3. Validate Department exists and is active (master table - independent)
     * 4. Validate Designation exists and is active
     * 5. Validate Manager (managerId) exists and is active (if provided)
     * 6. Validate Reporting Manager (reportingManagerId) exists and is active (if
     * provided)
     * 7. For each payrollId:
     * - Find Employee by payrollId
     * - Validate Employee is active
     * - Update employee: campus, department, designation, manager, reporting
     * manager, work starting date, remarks
     * 
     * @param bulkMappingDTO The bulk manager mapping request DTO
     * @return The same BulkManagerMappingDTO that was passed in
     */
    @Transactional
    public BulkManagerMappingDTO mapMultipleEmployeesAndUpdateWorkDate(BulkManagerMappingDTO bulkMappingDTO) {
        // Validate required fields
        validateBulkMappingDTO(bulkMappingDTO);

        // Step 1: Validate City exists
        cityRepository.findById(bulkMappingDTO.getCityId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("City not found with ID: " + bulkMappingDTO.getCityId()));

        // Step 2: Always use campusMappings array - required field
        List<CampusMappingDTO> campusMappings = bulkMappingDTO.getCampusMappings();
        if (campusMappings == null || campusMappings.isEmpty()) {
            throw new IllegalArgumentException("campusMappings array is required and cannot be empty");
        }

        // Step 3: Pre-validate all campuses, departments, and designations
        Department department = null;
        for (CampusMappingDTO campusMapping : campusMappings) {
            // Validate Department exists and is active
            Department campusDepartment = departmentRepository.findByIdAndIsActive(campusMapping.getDepartmentId(), 1)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Active Department not found with ID: " + campusMapping.getDepartmentId()));

            // Validate Campus is active and exists in the City
            Campus campus = campusRepository.findByCampusIdAndIsActive(campusMapping.getCampusId(), 1)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Active Campus not found with ID: " + campusMapping.getCampusId()));

            if (campus.getCity() == null || campus.getCity().getCityId() != bulkMappingDTO.getCityId()) {
                throw new ResourceNotFoundException(
                        String.format("Campus with ID %d is not assigned to City with ID %d",
                                campusMapping.getCampusId(), bulkMappingDTO.getCityId()));
            }

            // Validate Designation exists, is active, and belongs to the Department
            Designation designation = designationRepository.findByIdAndIsActive(campusMapping.getDesignationId(), 1)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Active Designation not found with ID: " + campusMapping.getDesignationId()));

            if (designation.getDepartment() == null
                    || designation.getDepartment().getDepartment_id() != campusMapping.getDepartmentId()) {
                throw new ResourceNotFoundException(
                        String.format("Designation with ID %d does not belong to Department with ID %d",
                                campusMapping.getDesignationId(), campusMapping.getDepartmentId()));
            }

            // Use first department for employee table (primary department)
            if (department == null) {
                department = campusDepartment;
            }
        }

        // Step 5: Assign Manager - only if provided (will validate campus match per
        // employee)
        // Treat 0 as null
        Employee manager = null;
        Integer managerIdValue = bulkMappingDTO.getManagerId();
        if (managerIdValue != null && managerIdValue != 0) {
            // Manager ID provided - validate it
            final Integer managerId = managerIdValue; // Make it final for lambda
            manager = employeeRepository.findById(managerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found with ID: " + managerId));

            if (manager.getIs_active() != 1) {
                throw new ResourceNotFoundException("Manager with ID " + managerId + " is not active");
            }
        }
        // If managerId is not provided (null or 0), manager remains null - no
        // auto-assignment

        // Step 6: Assign Reporting Manager - only if provided (will validate campus
        // match per employee)
        // Treat 0 as null
        Employee reportingManager = null;
        Integer reportingManagerIdValue = bulkMappingDTO.getReportingManagerId();
        if (reportingManagerIdValue != null && reportingManagerIdValue != 0) {
            // Reporting Manager ID provided - validate it
            final Integer reportingManagerId = reportingManagerIdValue; // Make it final for lambda
            reportingManager = employeeRepository.findById(reportingManagerId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Reporting Manager not found with ID: " + reportingManagerId));

            if (reportingManager.getIs_active() != 1) {
                throw new ResourceNotFoundException(
                        "Reporting Manager with ID " + reportingManagerId + " is not active");
            }
        }
        // If reportingManagerId is not provided (null or 0), reportingManager remains
        // null - no auto-assignment

        // Step 7: Process each payrollId
        List<String> processedPayrollIds = new ArrayList<>();
        List<String> failedPayrollIds = new ArrayList<>();

        for (String payrollId : bulkMappingDTO.getPayrollIds()) {
            try {
                // Find Employee by payrollId
                Employee employee = findEmployeeByPayrollId(payrollId);

                // Validate Employee is active
                if (employee.getIs_active() != 1) {
                    failedPayrollIds.add(payrollId + " (not active)");
                    continue;
                }

                // Get employee's existing campus (do not change it)
                Campus employeeCampus = employee.getCampus_id();
                // Removed validation to allow first-time mapping

                // For bulk operations, make sure manager/reporting manager is not the current
                // employee
                Employee employeeManager = manager;
                Employee employeeReportingManager = reportingManager;

                if (employeeManager != null && employeeManager.getEmp_id() == employee.getEmp_id()) {
                    employeeManager = null;
                }

                if (employeeReportingManager != null && employeeReportingManager.getEmp_id() == employee.getEmp_id()) {
                    employeeReportingManager = null;
                }

                // Validate manager is from the same city as employee (if provided)
                if (employeeManager != null) {
                    // Check employeeCampus null safety
                    Integer empCityId = (employeeCampus != null && employeeCampus.getCity() != null)
                            ? employeeCampus.getCity().getCityId()
                            : null;
                    if (empCityId == null) {
                        // If employee has no campus/city, we assume it's a new mapping and will inherit
                        // new campus city
                        // Use Bulk DTO City ID for validation
                        empCityId = bulkMappingDTO.getCityId();
                    }

                    if (employeeManager.getCampus_id() == null || employeeManager.getCampus_id().getCity() == null
                            || employeeManager.getCampus_id().getCity().getCityId() != empCityId) {
                        failedPayrollIds.add(payrollId + " (manager not from same city)");
                        continue;
                    }
                }

                // Validate reporting manager is from the same city as employee (if provided)
                if (employeeReportingManager != null) {
                    // Check employeeCampus null safety
                    Integer empCityId = (employeeCampus != null && employeeCampus.getCity() != null)
                            ? employeeCampus.getCity().getCityId()
                            : null;
                    if (empCityId == null) {
                        // If employee has no campus/city, we assume it's a new mapping and will inherit
                        // new campus city
                        // Use Bulk DTO City ID for validation
                        empCityId = bulkMappingDTO.getCityId();
                    }

                    if (employeeReportingManager.getCampus_id() == null
                            || employeeReportingManager.getCampus_id().getCity() == null
                            || employeeReportingManager.getCampus_id().getCity().getCityId() != empCityId) {
                        failedPayrollIds.add(payrollId + " (reporting manager not from same city)");
                        continue;
                    }
                }

                // Process each campus mapping for this employee
                // Only store in SharedEmployee table if multiple campuses are selected
                boolean isMultipleCampuses = campusMappings.size() > 1;

                // Validate campus matching for bulk operations
                if (employeeCampus == null) {
                    // Assign first campus if null
                    CampusMappingDTO firstMapping = campusMappings.get(0);
                    Campus requestedPrimaryCampus = campusRepository
                            .findByCampusIdAndIsActive(firstMapping.getCampusId(), 1)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Active Campus not found with ID: " + firstMapping.getCampusId()));
                    employeeCampus = requestedPrimaryCampus;
                }

                // (No else override)

                for (CampusMappingDTO campusMapping : campusMappings) {
                    Campus campus = campusRepository.findByCampusIdAndIsActive(campusMapping.getCampusId(), 1)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Active Campus not found with ID: " + campusMapping.getCampusId()));

                    Designation designation = designationRepository
                            .findByIdAndIsActive(campusMapping.getDesignationId(), 1)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Active Designation not found with ID: " + campusMapping.getDesignationId()));

                    // Validate designation belongs to department (already validated in
                    // pre-validation step, but double-check)
                    if (designation.getDepartment() == null
                            || designation.getDepartment().getDepartment_id() != campusMapping.getDepartmentId()) {
                        throw new ResourceNotFoundException(
                                String.format("Designation with ID %d does not belong to Department with ID %d",
                                        campusMapping.getDesignationId(), campusMapping.getDepartmentId()));
                    }

                    // Always create or update SharedEmployee record regardless of list size
                    saveOrUpdateSharedEmployee(employee, campus, designation, campusMapping.getSubjectId(),
                            bulkMappingDTO.getUpdatedBy());
                }

                // Update employee fields

                // Only update primary fields if employee didn't have a campus
                if (employee.getCampus_id() == null) {
                    employee.setCampus_id(employeeCampus);

                    // Use first department from campusMappings (primary department)
                    if (!campusMappings.isEmpty()) {
                        Department primaryDepartment = departmentRepository
                                .findByIdAndIsActive(campusMappings.get(0).getDepartmentId(), 1)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                        "Active Department not found with ID: "
                                                + campusMappings.get(0).getDepartmentId()));
                        employee.setDepartment(primaryDepartment);
                    }
                    // Set primary designation from first campus mapping (for backward
                    // compatibility)
                    if (!campusMappings.isEmpty()) {
                        Designation primaryDesignation = designationRepository
                                .findByIdAndIsActive(campusMappings.get(0).getDesignationId(), 1)
                                .orElseThrow(
                                        () -> new ResourceNotFoundException("Active Designation not found with ID: "
                                                + campusMappings.get(0).getDesignationId()));
                        employee.setDesignation(primaryDesignation);
                    }
                }

                // Update manager_id if provided
                if (employeeManager != null) {
                    employee.setEmployee_manager_id(employeeManager);
                }

                // Update reporting_manager_id if provided
                if (employeeReportingManager != null) {
                    employee.setEmployee_reporting_id(employeeReportingManager);
                }
                // Update contract_start_date with workStartingDate
                employee.setContract_start_date(bulkMappingDTO.getWorkStartingDate());
                employee.setUpdated_by(bulkMappingDTO.getUpdatedBy() != null ? bulkMappingDTO.getUpdatedBy() : 1);
                employee.setUpdated_date(LocalDateTime.now());

                // Update remarks: if value exists and is not empty, set it; otherwise set to
                // null
                // Treat null, empty string, whitespace-only, or string "null" as null
                String remarkValue = bulkMappingDTO.getRemark();
                if (remarkValue != null && !remarkValue.trim().isEmpty()
                        && !remarkValue.trim().equalsIgnoreCase("null")) {
                    employee.setRemarks(remarkValue.trim());
                } else {
                    employee.setRemarks(null);
                }

                employeeRepository.save(employee);
                processedPayrollIds.add(payrollId);

            } catch (ResourceNotFoundException e) {
                failedPayrollIds.add(payrollId + " (not found)");
            } catch (Exception e) {
                failedPayrollIds.add(payrollId + " (" + e.getMessage() + ")");
            }
        }

        return bulkMappingDTO; // Return the request DTO
    }

    /**
     * Finds employee by payrollId (checks both tempPayrollId and payRollId).
     * 
     * @param payrollId Payroll ID (can be tempPayrollId or payRollId)
     * @return Employee found by payrollId
     * @throws ResourceNotFoundException if employee not found
     */
    private Employee findEmployeeByPayrollId(String payrollId) {
        if (payrollId == null || payrollId.trim().isEmpty()) {
            throw new IllegalArgumentException("payrollId is required");
        }

        // Try to find by tempPayrollId first
        Optional<Employee> employeeByTemp = employeeRepository.findByTempPayrollId(payrollId);
        if (employeeByTemp.isPresent()) {
            return employeeByTemp.get();
        }

        // Try to find by payRollId
        Optional<Employee> employeeByPayroll = employeeRepository.findByPayRollId(payrollId);
        if (employeeByPayroll.isPresent()) {
            return employeeByPayroll.get();
        }

        throw new ResourceNotFoundException("Employee not found with payrollId: " + payrollId);
    }

    /**
     * Creates or updates a SharedEmployee record for an employee working in
     * multiple campuses.
     * 
     * @param employee    The employee
     * @param campus      The campus
     * @param designation The designation
     * @param subjectId   Optional subject ID (can be null)
     * @param updatedBy   User ID performing the update
     * @return The created or updated SharedEmployee
     */
    private SharedEmployee saveOrUpdateSharedEmployee(Employee employee, Campus campus, Designation designation,
            Integer subjectId, Integer updatedBy) {
        // Check if SharedEmployee record already exists for this employee and campus
        Optional<SharedEmployee> existing = sharedEmployeeRepository.findByEmpIdAndCampusId(
                employee.getEmp_id(), campus.getCampusId());

        SharedEmployee sharedEmployee;
        if (existing.isPresent()) {
            // Update existing record
            sharedEmployee = existing.get();
            sharedEmployee.setIsActive(1); // Reactivate if it was deactivated
        } else {
            // Create new record
            sharedEmployee = new SharedEmployee();
            sharedEmployee.setEmpId(employee);
            sharedEmployee.setCmpsId(campus);
            sharedEmployee.setCreatedBy(updatedBy != null ? updatedBy : 1);
            sharedEmployee.setCreatedDate(LocalDateTime.now());
        }

        // Update designation
        sharedEmployee.setDesignationId(designation);

        // Update subject if provided
        if (subjectId != null && subjectId > 0) {
            Subject subject = subjectRepository.findById(subjectId)
                    .orElseThrow(() -> new ResourceNotFoundException("Subject not found with ID: " + subjectId));
            sharedEmployee.setSubjectId(subject);
        } else {
            sharedEmployee.setSubjectId(null);
        }

        // Update audit fields
        sharedEmployee.setUpdatedBy(updatedBy != null ? updatedBy : 1);
        sharedEmployee.setUpdatedDate(LocalDateTime.now());

        return sharedEmployeeRepository.save(sharedEmployee);
    }

    /**
     * Validates the ManagerMappingDTO for required fields.
     * 
     * @param mappingDTO The DTO to validate
     * @throws IllegalArgumentException if any required field is null or invalid
     */
    private void validateMappingDTO(ManagerMappingDTO mappingDTO) {
        if (mappingDTO == null) {
            throw new IllegalArgumentException("ManagerMappingDTO cannot be null");
        }
        if (mappingDTO.getCityId() == null) {
            throw new IllegalArgumentException("cityId is required");
        }
        if (mappingDTO.getPayrollId() == null || mappingDTO.getPayrollId().trim().isEmpty()) {
            throw new IllegalArgumentException("payrollId is required");
        }
        if (mappingDTO.getWorkStartingDate() == null) {
            throw new IllegalArgumentException("workStartingDate is required");
        }

        // Validate campusMappings array - always required
        List<CampusMappingDTO> campusMappingsForValidation = mappingDTO.getCampusMappings();
        if (campusMappingsForValidation == null || campusMappingsForValidation.isEmpty()) {
            throw new IllegalArgumentException("campusMappings array is required and cannot be empty");
        }

        // Validate each campus mapping
        for (CampusMappingDTO campusMapping : campusMappingsForValidation) {
            if (campusMapping.getCampusId() == null || campusMapping.getCampusId() <= 0) {
                throw new IllegalArgumentException(
                        "Valid campusId (greater than 0) is required in each campusMapping object");
            }
            if (campusMapping.getDepartmentId() == null || campusMapping.getDepartmentId() <= 0) {
                throw new IllegalArgumentException(
                        "Valid departmentId (greater than 0) is required in each campusMapping object");
            }
            if (campusMapping.getDesignationId() == null || campusMapping.getDesignationId() <= 0) {
                throw new IllegalArgumentException(
                        "Valid designationId (greater than 0) is required in each campusMapping object");
            }
        }
    }

    /**
     * Validates the BulkManagerMappingDTO for required fields.
     * 
     * @param bulkMappingDTO The DTO to validate
     * @throws IllegalArgumentException if any required field is null or invalid
     */
    private void validateBulkMappingDTO(BulkManagerMappingDTO bulkMappingDTO) {
        if (bulkMappingDTO == null) {
            throw new IllegalArgumentException("BulkManagerMappingDTO cannot be null");
        }
        if (bulkMappingDTO.getCityId() == null) {
            throw new IllegalArgumentException("cityId is required");
        }

        // Validate campusMappings array - always required
        List<CampusMappingDTO> campusMappingsForValidation = bulkMappingDTO.getCampusMappings();
        if (campusMappingsForValidation == null || campusMappingsForValidation.isEmpty()) {
            throw new IllegalArgumentException("campusMappings array is required and cannot be empty");
        }

        // Validate each campus mapping
        for (CampusMappingDTO campusMapping : campusMappingsForValidation) {
            if (campusMapping.getCampusId() == null || campusMapping.getCampusId() <= 0) {
                throw new IllegalArgumentException(
                        "Valid campusId (greater than 0) is required in each campusMapping object");
            }
            if (campusMapping.getDepartmentId() == null || campusMapping.getDepartmentId() <= 0) {
                throw new IllegalArgumentException(
                        "Valid departmentId (greater than 0) is required in each campusMapping object");
            }
            if (campusMapping.getDesignationId() == null || campusMapping.getDesignationId() <= 0) {
                throw new IllegalArgumentException(
                        "Valid designationId (greater than 0) is required in each campusMapping object");
            }
        }
        if (bulkMappingDTO.getPayrollIds() == null || bulkMappingDTO.getPayrollIds().isEmpty()) {
            throw new IllegalArgumentException("payrollIds list is required and cannot be empty");
        }
        // Validate that all payrollIds in the list are not null or empty
        for (String payrollId : bulkMappingDTO.getPayrollIds()) {
            if (payrollId == null || payrollId.trim().isEmpty()) {
                throw new IllegalArgumentException("payrollIds list cannot contain null or empty values");
            }
        }
        if (bulkMappingDTO.getWorkStartingDate() == null) {
            throw new IllegalArgumentException("workStartingDate is required");
        }
    }

    /**
     * Unmaps manager and/or reporting manager from a single employee.
     * Does not require department or designation - but requires city, campus, and
     * last date of working.
     * 
     * Flow:
     * 1. Validate City exists
     * 2. Validate Campus (must belong to City)
     * 3. Find Employee by payrollId
     * 4. Validate Employee is active
     * 5. Update campus
     * 6. Set manager_id to null if provided managerId matches current manager
     * 7. Set reporting_manager_id to null if provided reportingManagerId matches
     * current reporting manager
     * 8. Update last date of working (contract end date)
     * 9. Update remarks (set to null if empty)
     * 10. Update employee
     * 
     * @param unmappingDTO The unmapping request DTO
     * @return The same UnmappingDTO that was passed in
     */
    @Transactional
    public UnmappingDTO unmapEmployee(UnmappingDTO unmappingDTO) {
        // Validate required fields
        if (unmappingDTO == null) {
            throw new IllegalArgumentException("UnmappingDTO cannot be null");
        }
        if (unmappingDTO.getCityId() == null) {
            throw new IllegalArgumentException("cityId is required");
        }
        if (unmappingDTO.getCampusIds() == null || unmappingDTO.getCampusIds().isEmpty()) {
            throw new IllegalArgumentException("campusIds array is required and cannot be empty");
        }
        if (unmappingDTO.getPayrollId() == null || unmappingDTO.getPayrollId().trim().isEmpty()) {
            throw new IllegalArgumentException("payrollId is required");
        }
        if (unmappingDTO.getLastDate() == null) {
            throw new IllegalArgumentException("lastDate is required");
        }

        // Step 1: Validate City exists
        cityRepository.findById(unmappingDTO.getCityId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("City not found with ID: " + unmappingDTO.getCityId()));

        // Step 2: Always use campusIds array - required field
        List<Integer> campusIdsList = unmappingDTO.getCampusIds();
        boolean useMultipleCampuses = campusIdsList.size() > 1;

        // Step 3: Validate all campuses exist and belong to the City
        for (Integer campusId : campusIdsList) {
            Campus campus = campusRepository.findByCampusIdAndIsActive(campusId, 1)
                    .orElseThrow(() -> new ResourceNotFoundException("Active Campus not found with ID: " + campusId));

            if (campus.getCity() == null || campus.getCity().getCityId() != unmappingDTO.getCityId()) {
                throw new ResourceNotFoundException(
                        String.format("Campus with ID %d is not assigned to City with ID %d",
                                campusId, unmappingDTO.getCityId()));
            }
        }

        // Step 4: Find Employee by payrollId
        Employee employee = findEmployeeByPayrollId(unmappingDTO.getPayrollId());

        // Step 5: Validate Employee is active
        if (employee.getIs_active() != 1) {
            throw new ResourceNotFoundException(
                    "Employee with payrollId " + unmappingDTO.getPayrollId() + " is not active");
        }

        // Step 6: Get employee's existing campus (do not change it)
        Campus employeeCampus = employee.getCampus_id();
        if (employeeCampus == null) {
            throw new ResourceNotFoundException("Employee with payrollId " + unmappingDTO.getPayrollId()
                    + " does not have a campus assigned. Cannot perform unmapping.");
        }

        // Step 7: Deactivate SharedEmployee records
        // ALWAYS check/deactivate SharedEmployee records regardless of multiple
        // campuses flag
        if (campusIdsList != null && !campusIdsList.isEmpty()) {
            for (Integer campusId : campusIdsList) {
                // Find all shared employee records for this employee and campus (active or
                // inactive)
                List<SharedEmployee> sharedEmployees = sharedEmployeeRepository
                        .findAllByEmpIdAndCampusId(employee.getEmp_id(), campusId);

                for (SharedEmployee sharedEmployee : sharedEmployees) {
                    if (sharedEmployee.getIsActive() == 1) {
                        sharedEmployee.setIsActive(0);
                        sharedEmployee
                                .setUpdatedBy(unmappingDTO.getUpdatedBy() != null ? unmappingDTO.getUpdatedBy() : 1);
                        sharedEmployee.setUpdatedDate(LocalDateTime.now());
                        sharedEmployeeRepository.save(sharedEmployee);
                    }
                }
            }
        }

        // Step 8: Unmap manager
        // 0 means unmap regardless of who the current manager is
        // > 0 means check for match and unmap
        // null means do nothing
        Integer managerIdValue = unmappingDTO.getManagerId();
        if (managerIdValue != null) {
            if (managerIdValue == 0) {
                // Clear manager regardless of who it is
                employee.setEmployee_manager_id(null);
            } else if (employee.getEmployee_manager_id() != null &&
                    employee.getEmployee_manager_id().getEmp_id() == managerIdValue) {
                // Validate and clear specific manager
                employee.setEmployee_manager_id(null);
            } else if (employee.getEmployee_manager_id() == null) {
                // If it was already null, no error for 0, but error for specific ID (safety)
                if (managerIdValue > 0) {
                    throw new ResourceNotFoundException("Employee does not have a manager assigned to unmap");
                }
            } else {
                // Specific ID mismatch
                throw new ResourceNotFoundException(
                        String.format("Employee's current manager ID (%d) does not match the provided managerId (%d)",
                                employee.getEmployee_manager_id().getEmp_id(), managerIdValue));
            }
        }
        // If managerId is null or 0, don't touch the manager field

        // Step 9: Unmap reporting manager
        // 0 means unmap regardless of who the current reporting manager is
        // > 0 means check for match and unmap
        // null means do nothing
        Integer reportingManagerIdValue = unmappingDTO.getReportingManagerId();
        if (reportingManagerIdValue != null) {
            if (reportingManagerIdValue == 0) {
                // Clear reporting manager regardless of who it is
                employee.setEmployee_reporting_id(null);
            } else if (employee.getEmployee_reporting_id() != null &&
                    employee.getEmployee_reporting_id().getEmp_id() == reportingManagerIdValue) {
                // Validate and clear specific manager
                employee.setEmployee_reporting_id(null);
            } else if (employee.getEmployee_reporting_id() == null) {
                // If it was already null, no error for 0, but error for specific ID (safety)
                if (reportingManagerIdValue > 0) {
                    throw new ResourceNotFoundException("Employee does not have a reporting manager assigned to unmap");
                }
            } else {
                // Specific ID mismatch
                throw new ResourceNotFoundException(
                        String.format(
                                "Employee's current reporting manager ID (%d) does not match the provided reportingManagerId (%d)",
                                employee.getEmployee_reporting_id().getEmp_id(), reportingManagerIdValue));
            }
        }
        // If reportingManagerId is null or 0, don't touch the reporting manager field

        // Step 10: Update last date of working (contract end date)
        employee.setContract_end_date(unmappingDTO.getLastDate());

        // Step 11: Update remarks: if value exists and is not empty, set it; otherwise
        // set to null
        // Treat null, empty string, whitespace-only, or string "null" as null
        String remarkValue = unmappingDTO.getRemark();
        if (remarkValue != null && !remarkValue.trim().isEmpty() && !remarkValue.trim().equalsIgnoreCase("null")) {
            employee.setRemarks(remarkValue.trim());
        } else {
            employee.setRemarks(null);
        }

        // Step 12: Update audit fields
        employee.setUpdated_by(unmappingDTO.getUpdatedBy() != null ? unmappingDTO.getUpdatedBy() : 1);
        employee.setUpdated_date(LocalDateTime.now());

        employeeRepository.save(employee);

        return unmappingDTO;
    }

    /**
     * Unmaps manager and/or reporting manager from multiple employees in bulk.
     * Does not require department or designation - but requires city, campus, and
     * last date of working.
     * 
     * Flow:
     * 1. Validate City exists
     * 2. Validate Campus (must belong to City)
     * 3. For each payrollId in the list:
     * - Find Employee by payrollId
     * - Validate Employee is active
     * - Update campus
     * - Set manager_id to null if provided managerId matches current manager
     * - Set reporting_manager_id to null if provided reportingManagerId matches
     * current reporting manager
     * - Update last date of working (contract end date)
     * - Update remarks (set to null if empty)
     * - Update employee
     * 
     * @param bulkUnmappingDTO The bulk unmapping request DTO
     * @return The same BulkUnmappingDTO that was passed in
     */
    @Transactional
    public BulkUnmappingDTO unmapMultipleEmployees(BulkUnmappingDTO bulkUnmappingDTO) {
        // Validate required fields
        if (bulkUnmappingDTO == null) {
            throw new IllegalArgumentException("BulkUnmappingDTO cannot be null");
        }
        if (bulkUnmappingDTO.getCityId() == null) {
            throw new IllegalArgumentException("cityId is required");
        }
        if (bulkUnmappingDTO.getCampusIds() == null || bulkUnmappingDTO.getCampusIds().isEmpty()) {
            throw new IllegalArgumentException("campusIds array is required and cannot be empty");
        }
        if (bulkUnmappingDTO.getPayrollIds() == null || bulkUnmappingDTO.getPayrollIds().isEmpty()) {
            throw new IllegalArgumentException("payrollIds list is required and cannot be empty");
        }
        // Validate that all payrollIds in the list are not null or empty
        for (String payrollId : bulkUnmappingDTO.getPayrollIds()) {
            if (payrollId == null || payrollId.trim().isEmpty()) {
                throw new IllegalArgumentException("payrollIds list cannot contain null or empty values");
            }
        }
        if (bulkUnmappingDTO.getLastDate() == null) {
            throw new IllegalArgumentException("lastDate is required");
        }

        // Step 1: Validate City exists
        cityRepository.findById(bulkUnmappingDTO.getCityId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("City not found with ID: " + bulkUnmappingDTO.getCityId()));

        // Step 2: Always use campusIds array - required field
        List<Integer> campusIdsToUnmap = bulkUnmappingDTO.getCampusIds();
        boolean useMultipleCampuses = campusIdsToUnmap.size() > 1;

        // Step 3: Validate all campuses exist and belong to the City
        for (Integer campusId : campusIdsToUnmap) {
            Campus campus = campusRepository.findByCampusIdAndIsActive(campusId, 1)
                    .orElseThrow(() -> new ResourceNotFoundException("Active Campus not found with ID: " + campusId));

            if (campus.getCity() == null || campus.getCity().getCityId() != bulkUnmappingDTO.getCityId()) {
                throw new ResourceNotFoundException(
                        String.format("Campus with ID %d is not assigned to City with ID %d",
                                campusId, bulkUnmappingDTO.getCityId()));
            }
        }

        // Step 3: Process each payrollId
        List<String> processedPayrollIds = new ArrayList<>();
        List<String> failedPayrollIds = new ArrayList<>();

        for (String payrollId : bulkUnmappingDTO.getPayrollIds()) {
            try {
                // Find Employee by payrollId
                Employee employee = findEmployeeByPayrollId(payrollId);

                // Validate Employee is active
                if (employee.getIs_active() != 1) {
                    failedPayrollIds.add(payrollId + " (not active)");
                    continue;
                }

                // Get employee's existing campus (do not change it)
                Campus employeeCampus = employee.getCampus_id();
                if (employeeCampus == null) {
                    failedPayrollIds.add(payrollId + " (no campus assigned)");
                    continue;
                }

                // Deactivate SharedEmployee records for the specified campuses
                // Only deactivate if multiple campuses are selected (single campus doesn't use
                // SharedEmployee table)
                if (useMultipleCampuses) {
                    for (Integer campusId : campusIdsToUnmap) {
                        List<SharedEmployee> sharedEmployees = sharedEmployeeRepository
                                .findAllByEmpIdAndCampusId(employee.getEmp_id(), campusId);
                        for (SharedEmployee sharedEmployee : sharedEmployees) {
                            sharedEmployee.setIsActive(0);
                            sharedEmployee.setUpdatedBy(
                                    bulkUnmappingDTO.getUpdatedBy() != null ? bulkUnmappingDTO.getUpdatedBy() : 1);
                            sharedEmployee.setUpdatedDate(LocalDateTime.now());
                            sharedEmployeeRepository.save(sharedEmployee);
                        }
                    }
                }

                // Unmap manager
                // 0 means unmap regardless of current ID
                // > 0 means check match for safety
                Integer managerIdValue = bulkUnmappingDTO.getManagerId();
                if (managerIdValue != null) {
                    if (managerIdValue == 0) {
                        employee.setEmployee_manager_id(null);
                    } else if (employee.getEmployee_manager_id() != null &&
                            employee.getEmployee_manager_id().getEmp_id() == managerIdValue) {
                        employee.setEmployee_manager_id(null);
                    } else if (employee.getEmployee_manager_id() == null) {
                        if (managerIdValue > 0) {
                            failedPayrollIds.add(payrollId + " (no manager assigned to unmap)");
                            continue;
                        }
                    } else {
                        failedPayrollIds.add(payrollId + " (manager ID mismatch: current=" +
                                employee.getEmployee_manager_id().getEmp_id() +
                                ", provided=" + managerIdValue + ")");
                        continue;
                    }
                }
                // If managerId is null or 0, don't touch the manager field

                // Unmap reporting manager
                // 0 means unmap regardless of current ID
                // > 0 means check match for safety
                Integer reportingManagerIdValue = bulkUnmappingDTO.getReportingManagerId();
                if (reportingManagerIdValue != null) {
                    if (reportingManagerIdValue == 0) {
                        employee.setEmployee_reporting_id(null);
                    } else if (employee.getEmployee_reporting_id() != null &&
                            employee.getEmployee_reporting_id().getEmp_id() == reportingManagerIdValue) {
                        employee.setEmployee_reporting_id(null);
                    } else if (employee.getEmployee_reporting_id() == null) {
                        if (reportingManagerIdValue > 0) {
                            failedPayrollIds.add(payrollId + " (no reporting manager assigned to unmap)");
                            continue;
                        }
                    } else {
                        failedPayrollIds.add(payrollId + " (reporting manager ID mismatch: current=" +
                                employee.getEmployee_reporting_id().getEmp_id() +
                                ", provided=" + reportingManagerIdValue + ")");
                        continue;
                    }
                }
                // If reportingManagerId is null or 0, don't touch the reporting manager field

                // Update last date of working (contract end date)
                employee.setContract_end_date(bulkUnmappingDTO.getLastDate());

                // Update remarks: if value exists and is not empty, set it; otherwise set to
                // null
                // Treat null, empty string, whitespace-only, or string "null" as null
                String remarkValue = bulkUnmappingDTO.getRemark();
                if (remarkValue != null && !remarkValue.trim().isEmpty()
                        && !remarkValue.trim().equalsIgnoreCase("null")) {
                    employee.setRemarks(remarkValue.trim());
                } else {
                    employee.setRemarks(null);
                }

                // Update audit fields
                employee.setUpdated_by(bulkUnmappingDTO.getUpdatedBy() != null ? bulkUnmappingDTO.getUpdatedBy() : 1);
                employee.setUpdated_date(LocalDateTime.now());

                employeeRepository.save(employee);
                processedPayrollIds.add(payrollId);

            } catch (ResourceNotFoundException e) {
                failedPayrollIds.add(payrollId + " (not found)");
            } catch (Exception e) {
                failedPayrollIds.add(payrollId + " (" + e.getMessage() + ")");
            }
        }

        return bulkUnmappingDTO;
    }

    /**
     * BATCH METHOD: Process multiple employees at once
     */
    public List<EmployeeBatchCampusDTO> getMultipleCampusAddresses(List<String> payrollIds) {
        if (payrollIds == null || payrollIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Fetch all employees in the list at once (handles both temp and permanent IDs)
        List<Employee> employees = employeeRepository.findAllByPayRollIdInOrTempPayrollIdIn(payrollIds);

        return employees.stream().map(emp -> {
            // Process to get flat list of campuses
            List<EmployeeCampusAddressDTO> flatList = processSingleEmployee(emp, payrollIds, false);

            EmployeeBatchCampusDTO batchDTO = new EmployeeBatchCampusDTO();

            // PRIMARY CAMPUS INFO (Top-Level Fields)
            if (emp.getCampus_id() != null) {
                try {
                    EmployeeCampusAddressDTO primaryDTO = createDTOForCampus(emp, emp.getCampus_id(), payrollIds,
                            false, null);
                    batchDTO.setCampusId(primaryDTO.getCampusId());
                    batchDTO.setCampusName(primaryDTO.getCampusName());
                    batchDTO.setCityId(primaryDTO.getCityId());
                    batchDTO.setCity(primaryDTO.getCity());
                    batchDTO.setFullAddress(primaryDTO.getFullAddress());
                    batchDTO.setBuildingMobileNo(primaryDTO.getBuildingMobileNo());
                } catch (Exception e) {
                    // Fallback or log if primary campus fails
                    batchDTO.setFullAddress("Error fetching primary campus: " + e.getMessage());
                }
            } else {
                batchDTO.setFullAddress("Address: Campus not assigned");
            }

            // 1. Employee Basic Info
            String displayId = emp.getPayRollId();
            if (displayId == null || (payrollIds != null && !payrollIds.contains(displayId))) {
                displayId = emp.getTempPayrollId();
            }
            batchDTO.setPayrollId(displayId != null ? displayId : emp.getPayRollId());

            String fullName = emp.getFirst_name();
            if (emp.getLast_name() != null) {
                fullName += " " + emp.getLast_name();
            }
            batchDTO.setEmployeeName(fullName);

            // 2. Manager Info
            if (emp.getEmployee_manager_id() != null) {
                Employee manager = emp.getEmployee_manager_id();
                batchDTO.setManagerId(manager.getEmp_id());
                String mgrName = manager.getFirst_name();
                if (manager.getLast_name() != null) {
                    mgrName += " " + manager.getLast_name();
                }
                batchDTO.setManagerName(mgrName);
            }

            // 3. Reporting Manager Info
            if (emp.getEmployee_reporting_id() != null) {
                Employee rManager = emp.getEmployee_reporting_id();
                batchDTO.setReportingManagerId(rManager.getEmp_id());
                String rMgrName = rManager.getFirst_name();
                if (rManager.getLast_name() != null) {
                    rMgrName += " " + rManager.getLast_name();
                }
                batchDTO.setReportingManagerName(rMgrName);
            }

            // 4. Department Info
            if (emp.getDepartment() != null) {
                batchDTO.setDepartmentId(emp.getDepartment().getDepartment_id());
                batchDTO.setDepartmentName(emp.getDepartment().getDepartment_name());
            }

            // 5. Designation Info
            if (emp.getDesignation() != null) {
                batchDTO.setDesignationId(emp.getDesignation().getDesignation_id());
                batchDTO.setDesignationName(emp.getDesignation().getDesignation_name());
            }

            // Populate Role Info DIRECTLY (since flatList won't have it due to
            // includeFullDetails=false)
            try {
                System.out.println("==================================================");
                System.out.println("DEBUG: Fetching Role for EmpID: " + emp.getEmp_id());
                List<String> roles = employeeRepository.findRoleNameByEmpId(emp.getEmp_id());
                System.out.println("DEBUG RESULT: " + (roles != null ? roles.toString() : "NULL LIST"));
                System.out.println("==================================================");

                if (roles != null && !roles.isEmpty()) {
                    batchDTO.setRole(roles.get(0));
                }
            } catch (Exception e) {
                System.err.println("Error fetching role for emp " + emp.getEmp_id() + ": " + e.getMessage());
            }

            // 6. Map to CampusDetailDTO
            List<CampusDetailDTO> details = flatList.stream()
                    .filter(flat -> flat.getCampusId() != null)
                    .map(flat -> new CampusDetailDTO(
                            flat.getCampusId(),
                            flat.getCampusName(),
                            flat.getCityId(),
                            flat.getCity(),
                            flat.getFullAddress(),
                            flat.getBuildingMobileNo(),
                            flat.getSubjectId(),
                            flat.getSubjectName(),
                            flat.getDesignationId(),
                            flat.getDesignationName(),
                            flat.getRoleId(),
                            flat.getRoleName()))
                    .collect(Collectors.toList());

            batchDTO.setCampusDetails(details);

            // 7. Set Employee Type and Hide campusDetails if Not Shared
            if (details.size() > 1) {
                batchDTO.setEmployeeType("Shared");
                batchDTO.setCampusDetails(details);
            } else {
                batchDTO.setEmployeeType("Not Shared");
                batchDTO.setCampusDetails(null); // Hide redundant details for single campus
            }

            return batchDTO;
        }).collect(Collectors.toList());
    }

    /**
     * SINGLE METHOD: Process one employee (Existing logic)
     */
    public EmployeeCampusAddressDTO getCampusAddress(String payrollId) {
        Employee emp = findEmployeeByPayrollId(payrollId);
        List<EmployeeCampusAddressDTO> results = processSingleEmployee(emp, Collections.singletonList(payrollId), true);
        return results.isEmpty() ? new EmployeeCampusAddressDTO() : results.get(0);
    }

    /**
     * CORE LOGIC: Converts Employee Entity to DTO
     */
    /**
     * CORE LOGIC: Converts Employee Entity to List of DTOs (Handling Multiple
     * Campuses)
     */
    private List<EmployeeCampusAddressDTO> processSingleEmployee(Employee emp, List<String> inputIds,
            boolean includeFullDetails) {
        List<EmployeeCampusAddressDTO> dtoList = new ArrayList<>();

        // 1. Check CampusEmployee table (New Multi-Campus)
        List<com.employee.entity.CampusEmployee> campusEmployees = campusEmployeeRepository
                .findByEmpId(emp.getEmp_id());
        if (campusEmployees != null && !campusEmployees.isEmpty()) {
            for (com.employee.entity.CampusEmployee ce : campusEmployees) {
                if (ce.getCmpsId() != null) {
                    dtoList.add(createDTOForCampus(emp, ce.getCmpsId(), inputIds, includeFullDetails, ce));
                }
            }
            return dtoList;
        }

        // 2. Check SharedEmployee table (Legacy/Other Multi-Campus)
        List<SharedEmployee> sharedEmployees = sharedEmployeeRepository.findActiveByEmpId(emp.getEmp_id());
        if (sharedEmployees != null && !sharedEmployees.isEmpty()) {
            for (SharedEmployee se : sharedEmployees) {
                if (se.getCmpsId() != null) {
                    dtoList.add(createDTOForCampus(emp, se.getCmpsId(), inputIds, includeFullDetails, se));
                }
            }
            return dtoList;
        }

        // 3. Fallback to Primary Campus
        if (emp.getCampus_id() != null) {
            dtoList.add(createDTOForCampus(emp, emp.getCampus_id(), inputIds, includeFullDetails, null));
        } else {
            // No campus assigned case
            EmployeeCampusAddressDTO dto = createBaseDTO(emp, inputIds, includeFullDetails);
            dto.setFullAddress("Address: Campus not assigned");
            dtoList.add(dto);
        }

        return dtoList;
    }

    private EmployeeCampusAddressDTO createDTOForCampus(Employee emp, Campus campus, List<String> inputIds,
            boolean includeFullDetails, Object mappingEntity) {
        EmployeeCampusAddressDTO dto = createBaseDTO(emp, inputIds, includeFullDetails);

        try {
            Integer campusId = campus.getCampusId();
            dto.setCampusId(campusId);
            dto.setCampusName(campus.getCampusName());

            // Extract details from mapping entity (SharedEmployee or CampusEmployee)
            if (mappingEntity instanceof SharedEmployee) {
                SharedEmployee se = (SharedEmployee) mappingEntity;
                if (se.getSubjectId() != null) {
                    dto.setSubjectId(se.getSubjectId().getSubject_id());
                    dto.setSubjectName(se.getSubjectId().getSubject_name());
                }
                if (se.getDesignationId() != null) {
                    dto.setDesignationId(se.getDesignationId().getDesignation_id());
                    dto.setDesignationName(se.getDesignationId().getDesignation_name());
                }
                // SharedEmployee doesn't have roleId directly, fetch global role
                try {
                    List<Integer> info = employeeRepository.findRoleIdByEmpId(emp.getEmp_id());
                    if (info != null && !info.isEmpty()) {
                        dto.setRoleId(info.get(0));
                    }
                    List<String> roleNames = employeeRepository.findRoleNameByEmpId(emp.getEmp_id());
                    if (roleNames != null && !roleNames.isEmpty()) {
                        dto.setRoleName(roleNames.get(0));
                    }
                } catch (Exception e) {
                    // Ignore
                }

            } else if (mappingEntity instanceof com.employee.entity.CampusEmployee) {
                com.employee.entity.CampusEmployee ce = (com.employee.entity.CampusEmployee) mappingEntity;
                if (ce.getRoleId() != null) {
                    dto.setRoleId(ce.getRoleId());
                    // Fetch Role Name if possible
                    try {
                        roleRepository.findById(ce.getRoleId()).ifPresent(role -> {
                            dto.setRoleName(role.getRoleName());
                        });
                    } catch (Exception e) {
                        // Ignore
                    }
                }
                // CampusEmployee doesn't have Subject/Designation, fall back to Employee's
                // primary
                if (emp.getDesignation() != null) {
                    dto.setDesignationId(emp.getDesignation().getDesignation_id());
                    dto.setDesignationName(emp.getDesignation().getDesignation_name());
                }
            } else {
                // Fallback for primary campus (null mappingEntity)
                if (emp.getDesignation() != null) {
                    dto.setDesignationId(emp.getDesignation().getDesignation_id());
                    dto.setDesignationName(emp.getDesignation().getDesignation_name());
                }
                // Fetch global role
                try {
                    List<Integer> info = employeeRepository.findRoleIdByEmpId(emp.getEmp_id());
                    if (info != null && !info.isEmpty()) {
                        dto.setRoleId(info.get(0));
                    }
                    List<String> roleNames = employeeRepository.findRoleNameByEmpId(emp.getEmp_id());
                    if (roleNames != null && !roleNames.isEmpty()) {
                        dto.setRoleName(roleNames.get(0));
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }

            // Populate City from Campus
            if (campus.getCity() != null) {
                dto.setCity(campus.getCity().getCityName());
                dto.setCityId(campus.getCity().getCityId());
            } else {
                // Try fetching if lazy loaded or null
                City result = campusRepository.findByCampusId(campusId);
                if (result != null) {
                    dto.setCity(result.getCityName());
                    dto.setCityId(result.getCityId());
                }
            }

            // Fetch buildings
            List<Building> buildings = buildingRepository.findBuildingsByCampusId(campusId);
            if (buildings == null || buildings.isEmpty()) {
                dto.setFullAddress("Address: No buildings found for campus");
                return dto;
            }

            // Pick main building logic
            Building selectedBuilding = buildings.stream()
                    .filter(b -> b.getIsMainBuilding() == 1)
                    .findFirst()
                    .orElse(buildings.get(0));

            // Fetch address
            BuildingAddress buildingAddress = buildingAddressRepository.findByBuildingIdAndAddressType(
                    selectedBuilding.getBuildingId(), "address");

            if (buildingAddress == null) {
                buildingAddress = buildingAddressRepository.findAddressByBuildingId(selectedBuilding.getBuildingId());
            }

            if (buildingAddress == null) {
                dto.setFullAddress("Address: No address record found for building");
                return dto;
            }

            // Build Address String
            StringJoiner sj = new StringJoiner(", ");
            if (buildingAddress.getPlot_no() != null)
                sj.add(buildingAddress.getPlot_no());
            if (buildingAddress.getArea() != null)
                sj.add(buildingAddress.getArea());
            if (buildingAddress.getStreet() != null)
                sj.add(buildingAddress.getStreet());
            if (buildingAddress.getLandmark() != null)
                sj.add(buildingAddress.getLandmark());
            if (buildingAddress.getPin_code() != null)
                sj.add(String.valueOf(buildingAddress.getPin_code()));

            dto.setFullAddress(sj.toString());
            dto.setBuildingMobileNo(buildingAddress.getMobile_no());

        } catch (Exception e) {
            dto.setFullAddress("Address Error: " + e.getMessage());
        }

        return dto;
    }

    private EmployeeCampusAddressDTO createBaseDTO(Employee emp, List<String> inputIds, boolean includeFullDetails) {
        EmployeeCampusAddressDTO dto = new EmployeeCampusAddressDTO();

        // 1. Core ID and Basic Info
        String displayId = emp.getPayRollId();
        if (displayId == null || (inputIds != null && !inputIds.contains(displayId))) {
            displayId = emp.getTempPayrollId();
        }
        dto.setPayrollId(displayId != null ? displayId : emp.getPayRollId());

        // Populate Employee Name (Always useful)
        String fullName = emp.getFirst_name();
        if (emp.getLast_name() != null) {
            fullName += " " + emp.getLast_name();
        }
        dto.setEmployeeName(fullName);

        if (includeFullDetails) {
            // 2. Populate Employee Mobile and Manager Info
            dto.setEmployeeMobileNo(
                    emp.getPrimary_mobile_no() != 0 ? String.valueOf(emp.getPrimary_mobile_no()) : null);

            if (emp.getEmployee_manager_id() != null) {
                dto.setManagerId(emp.getEmployee_manager_id().getEmp_id());
            }

            if (emp.getEmployee_reporting_id() != null) {
                dto.setReportingManagerId(emp.getEmployee_reporting_id().getEmp_id());
            }

            // Additional manager text info
            mapManagerInfo(emp, dto);

            // Populate Department Info
            if (emp.getDepartment() != null) {
                dto.setDepartmentId(emp.getDepartment().getDepartment_id());
                dto.setDepartmentName(emp.getDepartment().getDepartment_name());
            }

            // Populate Designation Info
            if (emp.getDesignation() != null) {
                dto.setDesignationId(emp.getDesignation().getDesignation_id());
                dto.setDesignationName(emp.getDesignation().getDesignation_name());
            }

            // Populate Role Info from View
            try {
                System.out.println("==================================================");
                System.out
                        .println("DEBUG CHECKING ROLE FOR: " + emp.getFirst_name() + " (ID: " + emp.getEmp_id() + ")");
                List<String> roles = employeeRepository.findRoleNameByEmpId(emp.getEmp_id());
                System.out.println("DEBUG RESULT: " + (roles != null ? roles.toString() : "NULL LIST"));
                System.out.println("==================================================");

                if (roles != null && !roles.isEmpty()) {
                    dto.setRole(roles.get(0));
                }
            } catch (Exception e) {
                System.err.println("DEBUG ERROR: " + e.getMessage());
                e.printStackTrace();
            }

            // Populate Employee Type Info
            if (emp.getEmployee_type_id() != null) {
                dto.setEmployeeTypeId(emp.getEmployee_type_id().getEmp_type_id());
                dto.setEmployeeTypeName(emp.getEmployee_type_id().getEmp_type());
            }
        }

        return dto;
    }

    private void mapManagerInfo(Employee emp, EmployeeCampusAddressDTO dto) {
        // Direct Manager
        if (emp.getEmployee_manager_id() != null) {
            Employee manager = emp.getEmployee_manager_id();
            dto.setManagerId(manager.getEmp_id());

            String name = manager.getFirst_name();
            if (manager.getLast_name() != null) {
                name += " " + manager.getLast_name();
            }
            dto.setManagerName(name);

            long mob = manager.getPrimary_mobile_no();
            dto.setManagerMobileNo(mob != 0 ? String.valueOf(mob) : null);
        }

        // Reporting Manager
        if (emp.getEmployee_reporting_id() != null) {
            Employee reportingManager = emp.getEmployee_reporting_id();
            dto.setReportingManagerId(reportingManager.getEmp_id());

            String rName = reportingManager.getFirst_name();
            if (reportingManager.getLast_name() != null) {
                rName += " " + reportingManager.getLast_name();
            }
            dto.setReportingManagerName(rName);

            long rMob = reportingManager.getPrimary_mobile_no();
            dto.setReportingManagerMobileNo(rMob != 0 ? String.valueOf(rMob) : null);
        }
    }

    /**
     * Updates the name of a campus.
     */
    public Campus updateCampusName(Integer campusId, String campusName) {
        Campus campus = campusRepository.findById(campusId)
                .orElseThrow(() -> new ResourceNotFoundException("Campus not found with ID: " + campusId));
        campus.setCampusName(campusName);
        return campusRepository.save(campus);
    }

    @Autowired
    private com.employee.repository.CampusEmployeeRepository campusEmployeeRepository;

    /**
     * Saves a list of CampusEmployee records.
     * 
     * @param dtos The list of DTOs containing data to save
     * @return The list of saved DTOs
     */
    @Transactional
    public List<com.employee.dto.CampusEmployeeDTO> saveCampusEmployees(List<com.employee.dto.CampusEmployeeDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            throw new IllegalArgumentException("Input list cannot be empty");
        }

        List<com.employee.dto.CampusEmployeeDTO> savedDtos = new ArrayList<>();

        for (com.employee.dto.CampusEmployeeDTO dto : dtos) {
            if (dto.getPayrollId() == null || dto.getPayrollId().trim().isEmpty()) {
                throw new IllegalArgumentException("Payroll ID is required");
            }
            if (dto.getCmpsId() == null) {
                throw new IllegalArgumentException("cmpsId is required");
            }

            Employee employee = employeeRepository.findByPayrollId(dto.getPayrollId())
                    .orElseGet(() -> employeeRepository.findByTempPayrollId(dto.getPayrollId())
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Employee not found with Payroll ID: " + dto.getPayrollId())));

            Campus campus = campusRepository.findById(dto.getCmpsId())
                    .orElseThrow(() -> new ResourceNotFoundException("Campus not found with ID: " + dto.getCmpsId()));

            com.employee.entity.CampusEmployee entity = new com.employee.entity.CampusEmployee();
            entity.setEmpId(employee);
            entity.setCmpsId(campus);
            entity.setRoleId(dto.getRoleId());
            entity.setAttendanceStatus(dto.getAttendanceStatus());
            entity.setIsActive(1);
            entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : 1);
            entity.setUpdatedBy(dto.getUpdatedBy());

            // createdDate and updatedDate are handled by DB defaults or JPA listeners

            campusEmployeeRepository.save(entity);

            savedDtos.add(dto);
        }
        return savedDtos;
    }
}
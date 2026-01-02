
package com.employee.service;
 
import java.text.SimpleDateFormat;
 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
 
import com.employee.dto.CentralOfficeChecklistDTO;
import com.employee.dto.RejectBackToDODTO;
import com.employee.entity.Campus;
import com.employee.entity.City;
import com.employee.entity.Employee;
import com.employee.entity.EmployeeCheckListStatus;
import com.employee.entity.EmpSalaryInfo;
import com.employee.entity.Organization;
import com.employee.exception.ResourceNotFoundException;
import com.employee.repository.CampusRepository;
import com.employee.repository.CityRepository;
import com.employee.repository.EmpAppCheckListDetlRepository;
import com.employee.repository.EmpSalaryInfoRepository;
import com.employee.repository.EmployeeCheckListStatusRepository;
import com.employee.repository.EmployeeRepository;
import com.employee.repository.OrganizationRepository;
 
/**
 * Service for Central Office Level operations
 * Handles employee rejection and sending back to DO (Demand Officer)
 */
@Service
@Transactional
public class CentralOfficeLevelService {
 
    private static final Logger logger = LoggerFactory.getLogger(CentralOfficeLevelService.class);
 
    @Autowired
    private EmployeeRepository employeeRepository;
   
    @Autowired
    private EmpAppCheckListDetlRepository empAppCheckListDetlRepository;
   
    @Autowired
    private EmployeeCheckListStatusRepository employeeCheckListStatusRepository;
   
    @Autowired
    private CampusRepository campusrepository;
   
    @Autowired
    private CityRepository cityrepository;
   
    @Autowired
    private OrganizationRepository organizationrepository;
   
    @Autowired
    private EmpSalaryInfoRepository empSalaryInfoRepository;
    
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
 
    public CentralOfficeChecklistDTO updateChecklist(CentralOfficeChecklistDTO checklistDTO) {
        // Validation: Check if tempPayrollId is provided
        if (checklistDTO.getTempPayrollId() == null || checklistDTO.getTempPayrollId().trim().isEmpty()) {
            throw new ResourceNotFoundException("tempPayrollId is required. Please provide a valid temp_payroll_id.");
        }
 
        // Validation: Check if checkListIds is provided
        if (checklistDTO.getCheckListIds() == null || checklistDTO.getCheckListIds().trim().isEmpty()) {
            throw new ResourceNotFoundException(
                    "checkListIds is required. Please provide checklist IDs (comma-separated string).");
        }
 
        logger.info("Updating checklist for temp_payroll_id: {}", checklistDTO.getTempPayrollId());
 
        // Step 1: Validate tempPayrollId exists in Employee table
        validateTempPayrollId(checklistDTO.getTempPayrollId());
 
        // Step 2: Find employee by temp_payroll_id
        Employee employee = employeeRepository.findByTempPayrollId(checklistDTO.getTempPayrollId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with temp_payroll_id: " + checklistDTO.getTempPayrollId()));
 
        Integer empId = employee.getEmp_id();
        logger.info("Found employee with emp_id: {} for temp_payroll_id: {}", empId, checklistDTO.getTempPayrollId());
 
        // Step 3: Always update status to "Confirm" (ID: 4) when updating checklist
        Integer oldStatusId = employee.getEmp_check_list_status_id() != null
                ? employee.getEmp_check_list_status_id().getEmp_app_status_id()
                : null;
        String oldStatusName = employee.getEmp_check_list_status_id() != null
                ? employee.getEmp_check_list_status_id().getCheck_app_status_name()
                : "null";
 
        EmployeeCheckListStatus confirmStatus = employeeCheckListStatusRepository
                .findByCheck_app_status_name("Confirm").orElseThrow(() -> new ResourceNotFoundException(
                        "EmployeeCheckListStatus with name 'Confirm' not found"));
       
        employee.setEmp_check_list_status_id(confirmStatus);
        logger.info("Updated employee (emp_id: {}) status from '{}' (ID: {}) to 'Confirm' (ID: {})",
                empId, oldStatusName, oldStatusId, confirmStatus.getEmp_app_status_id());
       
        // Clear remarks when confirming
        employee.setRemarks(null);
        logger.info("Cleared remarks for employee (emp_id: {}) when confirming", empId);
 
        // Step 4: Validate checklist IDs before saving
        validateCheckListIds(checklistDTO.getCheckListIds());
 
        // Step 5: Update emp_app_check_list_detl_id in Employee table
        employee.setEmp_app_check_list_detl_id(checklistDTO.getCheckListIds());
 
        // Step 6: Update notice_period AND Generate Permanent ID (if provided)
        if (checklistDTO.getNoticePeriod() != null && !checklistDTO.getNoticePeriod().trim().isEmpty()) {
            employee.setNotice_period(checklistDTO.getNoticePeriod().trim());
            logger.info("Updated notice period for employee (emp_id: {}): {}", empId, checklistDTO.getNoticePeriod());
 
            // Check if a permanent ID already exists. If not, generate one.
            if (employee.getPayRollId() == null || employee.getPayRollId().trim().isEmpty()) {
               
                // --- NOTE: We removed the try-catch block here to let errors show ---
               
                // This method now saves to BOTH tables
                String permanentId = generateAndSetPermanentId(employee);
                logger.info("Successfully generated and set permanent payroll ID: {} for emp_id: {}", permanentId, empId);
               
                // 1. Set Username (it's the permanent payroll ID)
                employee.setUser_name(permanentId);
               
                // 2. Get data for password
                String firstName = employee.getFirst_name();
                java.util.Date dateOfJoin = employee.getDate_of_join();
 
                // 3. Safety checks to prevent errors
                if (firstName == null || firstName.length() < 3) {
                    logger.error("CRITICAL: Cannot generate password for emp_id: {}. First name is missing or shorter than 3 letters.", empId);
                    throw new RuntimeException("Cannot generate password. Employee first name is missing or < 3 letters.");
                }
                if (dateOfJoin == null) {
                    logger.error("CRITICAL: Cannot generate password for emp_id: {}. Date of join is missing.", empId);
                    throw new RuntimeException("Cannot generate password. Employee date of join is missing.");
                }
 
                // 4. Create password (First 3 letters of first_name + date_of_join)
                String namePart = firstName.substring(0, 3);
                String finalNamePart = namePart.substring(0, 1).toUpperCase() + namePart.substring(1);
 
                SimpleDateFormat sdf = new SimpleDateFormat("ddMMyyyy");
                String dateOfJoinString = sdf.format(dateOfJoin);
               
                String plainTextPassword = finalNamePart + dateOfJoinString;
               
                // 5. Set PLAIN TEXT Password
                employee.setPassword(plainTextPassword);
               
                logger.info("Successfully generated default username ({}) and PLAIN TEXT password for emp_id: {}", permanentId, empId);
 
            } else {
                logger.warn("Employee (emp_id: {}) already has a permanent payroll ID ({}). Skipping generation.", empId, employee.getPayRollId());
            }
           
        } else {
            logger.warn("Notice period is null or empty. Permanent ID will not be generated on this update.");
        }
 
        // Step 7: Save all changes to the Employee table
        employeeRepository.save(employee);
 
        logger.info("Successfully updated checklist for employee (emp_id: {}, temp_payroll_id: '{}')", empId,
                checklistDTO.getTempPayrollId());
 
        return checklistDTO;
    }
 
    /**
     * Validate checklist IDs
     */
    private void validateCheckListIds(String checkListIds) {
        if (checkListIds == null || checkListIds.trim().isEmpty()) {
            return;
        }
 
        String[] idArray = checkListIds.split(",");
 
        for (String idStr : idArray) {
            idStr = idStr.trim();
 
            if (idStr.isEmpty()) {
                continue;
            }
 
            try {
                Integer checklistId = Integer.parseInt(idStr);
 
                // Using exists check to avoid loading entity (prevents check_list_id column error)
                if (!empAppCheckListDetlRepository.existsByIdAndIsActive(checklistId, 1)) {
                    throw new ResourceNotFoundException("Checklist ID " + checklistId
                            + " not found or inactive. Provided IDs: " + checkListIds);
                }
 
                logger.debug("Validated checklist ID: {} exists and is active", checklistId);
 
            } catch (NumberFormatException e) {
                throw new ResourceNotFoundException("Invalid checklist ID format: '" + idStr
                        + "'. Provided IDs: " + checkListIds);
            }
        }
 
        logger.info("✅ All checklist IDs validated successfully: {}", checkListIds);
    }
 
    /**
     * Validate tempPayrollId
     */
    private void validateTempPayrollId(String tempPayrollId) {
        if (tempPayrollId == null || tempPayrollId.trim().isEmpty()) {
            throw new ResourceNotFoundException("tempPayrollId is required.");
        }
 
        employeeRepository.findByTempPayrollId(tempPayrollId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with temp_payroll_id: " + tempPayrollId));
 
        logger.info("✅ Validated temp_payroll_id exists: {}", tempPayrollId);
    }
 
    /**
     * Generates a permanent payroll ID and saves it to BOTH the Employee
     * and the EmpSalaryInfo tables.
     *
     * @param employee The employee object to update (must be fully loaded).
     * @return The generated permanent payroll ID.
     * @throws ResourceNotFoundException if related Campus, City, Organization, or EmpSalaryInfo is not found.
     */
    private String generateAndSetPermanentId(Employee employee) {
        logger.info("Attempting to generate permanent payroll ID for emp_id: {}", employee.getEmp_id());
       
        // --- START OF FIX ---
        // 1. Get Campus object directly from the employee
        //    We no longer call employee.getCmps_id()
        Campus campusdata = employee.getCampus_id();
 
        // 1a. Add a null check
        if (campusdata == null) {
            logger.error("CRITICAL: Cannot generate permanent ID for emp_id: {}. Campus ID (campus_id) is missing.", employee.getEmp_id());
            throw new ResourceNotFoundException("Cannot generate permanent ID: The Campus ID (campus_id) is missing for employee: " + employee.getEmp_id());
        }
        // --- END OF FIX ---
 
        // 2. Find City or throw
        int city_id = campusdata.getCity().getCityId();
        City citydata = cityrepository.findById(city_id)
            .orElseThrow(() -> new ResourceNotFoundException("City not found for city_id: " + city_id));
 
        // 3. Find Organization or throw (READ-ONLY - just for validation and getting payrollCode)
        Integer org_id_wrapper = employee.getOrg_id();

        if (org_id_wrapper == null) {
            logger.error("CRITICAL: Cannot generate permanent ID for emp_id: {}. Organization ID (org_id) is missing.", employee.getEmp_id());
            throw new ResourceNotFoundException("Cannot generate permanent ID: The Organization ID (org_id) is missing for employee: " + employee.getEmp_id());
        }
       
        int org_id = org_id_wrapper.intValue();
       
        // Read organization to validate it exists (READ-ONLY - no updates to master table)
        Organization org_data = organizationrepository.findById(org_id)
            .orElseThrow(() -> new ResourceNotFoundException("Organization not found for org_id: " + org_id));
        
        // Validate organization is active (optional check)
        if (org_data.getIsActive() != 1) {
            logger.warn("Organization (org_id: {}) is not active, but proceeding with ID generation", org_id);
        }

        // --- All data is present, proceed with logic ---
       
        String payrole_city_code = citydata.getPayroll_city_code();
        long pay_role_code = org_data.getPayrollCode();
        
        // Build prefix pattern: city_code + org_code (e.g., "55" + "400" = "55400")
        String prefix = payrole_city_code + pay_role_code;
       
        // --- FIND MAX EXISTING PAYROLL ID (instead of reading from organization table) ---
        String maxExistingPayrollId = employeeRepository.findMaxPayrollIdByKey(prefix + "%");
        long new_max_number = 1; // Default to 1 if no existing IDs found
        
        if (maxExistingPayrollId != null && maxExistingPayrollId.startsWith(prefix)) {
            try {
                // Extract the number part after the prefix
                String numberPart = maxExistingPayrollId.substring(prefix.length());
                long current_max_number = Long.parseLong(numberPart);
                new_max_number = current_max_number + 1; // Increment by 1
                logger.info("Found max existing payroll ID: {}. Incrementing to: {}", maxExistingPayrollId, new_max_number);
            } catch (NumberFormatException e) {
                logger.warn("Could not parse number part from max payroll ID: {}. Starting from 1.", maxExistingPayrollId);
                new_max_number = 1;
            }
        } else {
            logger.info("No existing payroll IDs found with prefix: {}. Starting from 1.", prefix);
        }
       
        logger.info("Generating new payroll ID for org_id {}: prefix={}, next_number={}", org_id, prefix, new_max_number);
       
        // Use the NEW incremented number to build the ID
        String permanentId = prefix + new_max_number;
       
        // --- SAVE TO EMPLOYEE TABLE ---
        employee.setPayRollId(permanentId);
        logger.info("Set PayRollId on Employee entity for emp_id: {}", employee.getEmp_id());
 
 
 
        // --- SAVE TO EMP_SALARY_INFO TABLE ---
        // Update payroll_id in EmpSalaryInfo table using native SQL
        updateEmpSalaryInfoPayrollId(employee.getEmp_id(), permanentId);

        // NOTE: Organization table is a master table (read-only). We don't update it.
        // The payroll number sequence is maintained by querying existing permanent payroll IDs.
        logger.info("Generated permanent payroll ID: {} for emp_id: {} (organization table not updated - master table)", permanentId, employee.getEmp_id());

        return permanentId;
    }
    
    /**
     * Update EmpSalaryInfo.payroll_id using native SQL in a completely isolated transaction
     * Uses TransactionTemplate to ensure complete isolation from main transaction
     * If this fails due to trigger issues, it will NOT affect the main transaction
     */
    private void updateEmpSalaryInfoPayrollId(Integer empId, String payrollId) {
        // Create a new transaction template with REQUIRES_NEW propagation
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // Set read-only to false (default) and isolation level
        transactionTemplate.setReadOnly(false);
        
        try {
            // Execute in separate transaction - any exception here will only rollback this transaction
            transactionTemplate.executeWithoutResult(status -> {
                try {
                    // Use native SQL UPDATE to update only payroll_id field
                    int updatedRows = empSalaryInfoRepository.updatePayrollIdOnly(empId, payrollId);
                    
                    if (updatedRows > 0) {
                        logger.info("Successfully updated payroll_id to '{}' in EmpSalaryInfo for emp_id: {}", payrollId, empId);
                    } else {
                        logger.warn("No rows updated when setting payroll_id in EmpSalaryInfo for emp_id: {}. Record may not exist or be inactive.", empId);
                    }
                } catch (Exception e) {
                    // Log the error - this will cause the separate transaction to rollback
                    logger.error("Database error updating payroll_id in EmpSalaryInfo for emp_id: {} (separate transaction). " +
                                "Error: {}. This separate transaction will rollback, but main transaction continues.", empId, e.getMessage());
                    // Rethrow to trigger rollback of ONLY this separate transaction
                    throw new RuntimeException("EmpSalaryInfo update failed", e);
                }
            });
        } catch (org.springframework.transaction.TransactionException e) {
            // Catch transaction-level exceptions
            logger.error("Transaction error updating EmpSalaryInfo.payroll_id for emp_id: {} (separate transaction). " +
                        "Main transaction (Employee.payrollId) is NOT affected. Error: {}", empId, e.getMessage());
        } catch (org.springframework.dao.DataAccessException e) {
            // Catch data access exceptions (JDBC, Hibernate, etc.)
            logger.error("Data access error updating EmpSalaryInfo.payroll_id for emp_id: {} (separate transaction). " +
                        "Main transaction (Employee.payrollId) is NOT affected. Error: {}", empId, e.getMessage());
        } catch (Exception e) {
            // Catch any other exception - main transaction is NOT affected
            logger.error("Unexpected error updating EmpSalaryInfo.payroll_id for emp_id: {} (separate transaction). " +
                        "Main transaction (Employee.payrollId) is NOT affected. Error: {}", empId, e.getMessage());
        }
    }
    
    /**
     * Reject and send employee back to DO (Demand Officer)
     * This method is called when Central Office rejects an employee application
     *
     * Flow:
     * 1. Validate temp_payroll_id exists in Employee table
     * 2. Find employee by temp_payroll_id
     * 3. Validate that current status is "Pending at CO" (required)
     * 4. Update status to "Back to DO"
     * 5. Update remarks (if remarks already exist, update them; if not, set new remarks)
     *
     * @param rejectDTO DTO containing tempPayrollId and remarks
     * @return Updated DTO with the saved data
     * @throws ResourceNotFoundException if employee not found or status is not "Pending at CO"
     */
    public RejectBackToDODTO rejectBackToDO(RejectBackToDODTO rejectDTO) {
        // Validation: Check if tempPayrollId is provided
        if (rejectDTO.getTempPayrollId() == null || rejectDTO.getTempPayrollId().trim().isEmpty()) {
            throw new ResourceNotFoundException("tempPayrollId is required. Please provide a valid temp_payroll_id.");
        }
       
        // Validation: Check if remarks is provided
        if (rejectDTO.getRemarks() == null || rejectDTO.getRemarks().trim().isEmpty()) {
            throw new ResourceNotFoundException("remarks is required. Please provide a reason for rejecting and sending back to DO.");
        }
       
        // Validation: Check remarks length (max 250 characters)
        if (rejectDTO.getRemarks().length() > 250) {
            throw new IllegalArgumentException("remarks cannot exceed 250 characters. Current length: " + rejectDTO.getRemarks().length());
        }
       
        logger.info("Rejecting employee and sending back to DO - temp_payroll_id: {}, remarks: {}",
                rejectDTO.getTempPayrollId(), rejectDTO.getRemarks());
       
        // Step 1: Validate tempPayrollId exists in Employee table
        validateTempPayrollId(rejectDTO.getTempPayrollId());
       
        // Step 2: Find employee by temp_payroll_id
        Employee employee = employeeRepository.findByTempPayrollId(rejectDTO.getTempPayrollId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with temp_payroll_id: " + rejectDTO.getTempPayrollId()));
       
        Integer empId = employee.getEmp_id();
        logger.info("Found employee with emp_id: {} for temp_payroll_id: {}", empId, rejectDTO.getTempPayrollId());
       
        // Step 3: Validate that current status is "Pending at CO" - this method only works for "Pending at CO" status
        if (employee.getEmp_check_list_status_id() == null) {
            throw new ResourceNotFoundException(
                    "Cannot reject employee: Employee (emp_id: " + empId +
                    ", temp_payroll_id: '" + rejectDTO.getTempPayrollId() +
                    "') does not have a status set. This method only works when employee status is 'Pending at CO'.");
        }
       
        String currentStatusName = employee.getEmp_check_list_status_id().getCheck_app_status_name();
        if (!"Pending at CO".equals(currentStatusName)) {
            throw new ResourceNotFoundException(
                    "Cannot reject employee: Current employee status is '" + currentStatusName +
                    "' (emp_id: " + empId + ", temp_payroll_id: '" + rejectDTO.getTempPayrollId() +
                    "'). This method only works when employee status is 'Pending at CO'.");
        }
       
        logger.info("Employee (emp_id: {}) current status is 'Pending at CO', proceeding with reject back to DO", empId);
       
        // Step 4: Update status to "Back to DO"
        EmployeeCheckListStatus backToDOStatus = employeeCheckListStatusRepository.findByCheck_app_status_name("Back to DO")
                .orElseThrow(() -> new ResourceNotFoundException("EmployeeCheckListStatus with name 'Back to DO' not found"));
        employee.setEmp_check_list_status_id(backToDOStatus);
        logger.info("Updated employee (emp_id: {}) status from 'Pending at CO' to 'Back to DO' (ID: {})",
                empId, backToDOStatus.getEmp_app_status_id());
       
        // Step 5: Update remarks (if remarks already exist, update them; if not, set new remarks)
        String existingRemarks = employee.getRemarks();
        if (existingRemarks != null && !existingRemarks.trim().isEmpty()) {
            // Update existing remarks (append or replace based on business logic - here we're replacing)
            employee.setRemarks(rejectDTO.getRemarks().trim());
            logger.info("Updated existing remarks for employee (emp_id: {}). Previous remarks: '{}', New remarks: '{}'",
                    empId, existingRemarks, rejectDTO.getRemarks());
        } else {
            // Set new remarks
            employee.setRemarks(rejectDTO.getRemarks().trim());
            logger.info("Set new remarks for employee (emp_id: {}): {}", empId, rejectDTO.getRemarks());
        }
       
        // Save employee updates (status and remarks)
        employeeRepository.save(employee);
       
        logger.info("Successfully rejected employee (emp_id: {}, temp_payroll_id: '{}') and sent back to DO with remarks",
                empId, rejectDTO.getTempPayrollId());
       
        // Return the DTO with saved data
        return rejectDTO;
    }
}
 
 
 
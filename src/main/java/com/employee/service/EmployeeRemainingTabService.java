package com.employee.service;
 
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
 
import com.employee.dto.AgreementInfoDTO;
import com.employee.dto.BankInfoDTO;
import com.employee.dto.CategoryInfoDTO;
import com.employee.dto.DocumentDTO;
import com.employee.dto.ForwardToDivisionalOfficeResponseDTO;
import com.employee.dto.QualificationDTO;
import com.employee.entity.BankDetails;
import com.employee.entity.EmpChequeDetails;
import com.employee.entity.EmpDocuments;
import com.employee.entity.EmpPaymentType;
import com.employee.entity.CostCenter;
import com.employee.entity.EmpGrade;
import com.employee.entity.EmpPfDetails;
import com.employee.entity.EmpQualification;
import com.employee.entity.EmpSalaryInfo;
import com.employee.entity.EmpStructure;
import com.employee.entity.Employee;
import com.employee.entity.EmployeeCheckListStatus;
import com.employee.entity.OrgBank;
import com.employee.entity.OrgBankBranch;
import com.employee.entity.Orientation;
import com.employee.exception.ResourceNotFoundException;
import com.employee.repository.BankDetailsRepository;
import com.employee.repository.DepartmentRepository;
import com.employee.repository.DesignationRepository;
import com.employee.repository.EmpChequeDetailsRepository;
import com.employee.repository.EmpDocTypeRepository;
import com.employee.repository.EmpDocumentsRepository;
import com.employee.repository.CostCenterRepository;
import com.employee.repository.EmpGradeRepository;
import com.employee.repository.EmpPaymentTypeRepository;
import com.employee.repository.EmpPfDetailsRepository;
import com.employee.repository.EmpQualificationRepository;
import com.employee.repository.EmpSalaryInfoRepository;
import com.employee.repository.EmpStructureRepository;
import com.employee.repository.EmployeeCheckListStatusRepository;
import com.employee.repository.EmployeeRepository;
import com.employee.repository.EmployeeTypeRepository;
import com.employee.repository.OrgBankBranchRepository;
import com.employee.repository.OrgBankRepository;
import com.employee.repository.QualificationDegreeRepository;
import com.employee.repository.QualificationRepository;
import com.employee.repository.SubjectRepository;
import com.employee.repository.OrientationRepository;
import com.employee.repository.OrganizationRepository;
import com.employee.entity.Organization;
 
/**
 * Service for handling remaining employee onboarding tabs (5 APIs). Contains:
 * Qualification, Documents, Category Info, Bank Info, Agreement Info
 *
 * This service is completely independent and does not use
 * EmployeeEntityPreparationService. All helper methods are implemented directly
 * within this service.
 */
@Service
@Transactional
public class EmployeeRemainingTabService {
 
    private static final Logger logger = LoggerFactory.getLogger(EmployeeRemainingTabService.class);
 
    @Autowired
    private EmployeeRepository employeeRepository;
 
    @Autowired
    private QualificationRepository qualificationRepository;
 
    @Autowired
    private EmployeeCheckListStatusRepository employeeCheckListStatusRepository;
 
    @Autowired
    private EmpQualificationRepository empQualificationRepository;
 
    @Autowired
    private QualificationDegreeRepository qualificationDegreeRepository;
 
    @Autowired
    private EmpDocumentsRepository empDocumentsRepository;
 
    @Autowired
    private EmpDocTypeRepository empDocTypeRepository;
 
    @Autowired
    private DepartmentRepository departmentRepository;
 
    @Autowired
    private DesignationRepository designationRepository;
 
    @Autowired
    private EmployeeTypeRepository employeeTypeRepository;
 
    @Autowired
    private BankDetailsRepository bankDetailsRepository;
 
    @Autowired
    private EmpPaymentTypeRepository empPaymentTypeRepository;
 
    @Autowired
    private OrgBankRepository orgBankRepository;
 
    @Autowired
    private OrgBankBranchRepository orgBankBranchRepository;
 
    @Autowired
    private EmpChequeDetailsRepository empChequeDetailsRepository;
 
    @Autowired
    private SubjectRepository subjectRepository;
 
    @Autowired
    private com.employee.repository.EmpSubjectRepository empSubjectRepository;
 
    @Autowired
    private OrientationRepository orientationRepository;
 
    @Autowired
    private EmpSalaryInfoRepository empSalaryInfoRepository;
 
    @Autowired
    private EmpPfDetailsRepository empPfDetailsRepository;
 
    @Autowired
    private EmpStructureRepository empStructureRepository;
 
    @Autowired
    private EmpGradeRepository empGradeRepository;
 
    @Autowired
    private CostCenterRepository costCenterRepository;

    @Autowired
    private OrganizationRepository organizationRepository;
 
// ============================================================================
// API METHODS (5 APIs)
// ============================================================================
 
    /**
     * API 5: Save Qualification (Tab 5)
     *
     * @param tempPayrollId Temp Payroll ID
     * @param qualification Qualification DTO
     * @return Saved QualificationDTO object
     */
    public QualificationDTO saveQualification(String tempPayrollId, QualificationDTO qualification) {
        logger.info("Saving Qualification for tempPayrollId: {}", tempPayrollId);
 
        try {
// Step 1: Validate DTO data BEFORE any database operations
            validateQualification(qualification);
        } catch (Exception e) {
            logger.error("❌ ERROR: Qualification validation failed. NO data saved. Error: {}", e.getMessage(), e);
            throw e;
        }
 
        try {
// Step 2: Find employee (read-only operation)
            Employee employee = findEmployeeByTempPayrollId(tempPayrollId);
 
// Step 3: Prepare entities in memory (NO database writes yet)
            Integer createdBy = qualification.getCreatedBy();
            Integer updatedBy = qualification.getUpdatedBy();
            List<EmpQualification> qualificationEntities = prepareQualificationEntities(qualification, employee,
                    createdBy);
 
// Step 4: Save to database ONLY after all validations pass
            updateOrCreateQualificationEntities(qualificationEntities, employee, updatedBy);
 
// Note: qualification_id is now set from BasicInfoDTO.qualificationId (not from qualification tab's isHighest)
// Removed updateHighestQualification call - qualification_id should be set when BasicInfo is saved
 
            logger.info("✅ Saved {} qualification records for emp_id: {} (tempPayrollId: {})",
                    qualificationEntities.size(), employee.getEmp_id(), tempPayrollId);
// Return the saved DTO object
            return qualification;
 
        } catch (Exception e) {
            logger.error("❌ ERROR: Qualification save failed. Error: {}", e.getMessage(), e);
            throw e;
        }
    }
 
    /**
     * API 6: Save Documents (Tab 6)
     *
     * @param tempPayrollId Temp Payroll ID
     * @param documents     Document DTO
     * @return Saved DocumentDTO object
     */
    public DocumentDTO saveDocuments(String tempPayrollId, DocumentDTO documents) {
        logger.info("Saving Documents for tempPayrollId: {}", tempPayrollId);
 
        try {
// Step 1: Validate DTO data BEFORE any database operations
            validateDocuments(documents);
        } catch (Exception e) {
            logger.error("❌ ERROR: Documents validation failed. NO data saved. Error: {}", e.getMessage(), e);
            throw e;
        }
 
        try {
// Step 2: Find employee (read-only operation)
            Employee employee = findEmployeeByTempPayrollId(tempPayrollId);
 
// Step 3: Prepare entities in memory (NO database writes yet)
            Integer createdBy = documents.getCreatedBy();
            Integer updatedBy = documents.getUpdatedBy();
            List<EmpDocuments> documentEntities = prepareDocumentEntities(documents, employee, createdBy);
 
// Step 4: Save to database ONLY after all validations pass
            updateOrCreateDocumentEntities(documentEntities, employee, updatedBy);
 
            logger.info("✅ Saved {} document records for emp_id: {} (tempPayrollId: {})", documentEntities.size(),
                    employee.getEmp_id(), tempPayrollId);
// Return the saved DTO object
            return documents;
 
        } catch (Exception e) {
            logger.error("❌ ERROR: Documents save failed. Error: {}", e.getMessage(), e);
            throw e;
        }
    }
 
    /**
     * API 7: Save Category Info (Tab 7) Updates Employee table with
     * category-related fields
     *
     * @param tempPayrollId Temp Payroll ID
     * @param categoryInfo  Category Info DTO
     * @return Saved CategoryInfoDTO object
     */
    public CategoryInfoDTO saveCategoryInfo(String tempPayrollId, CategoryInfoDTO categoryInfo) {
        logger.info("Saving Category Info for tempPayrollId: {}", tempPayrollId);
 
        try {
// Step 1: Validate DTO data BEFORE any database operations
            validateCategoryInfo(categoryInfo);
        } catch (Exception e) {
            logger.error("❌ ERROR: Category Info validation failed. NO data saved. Error: {}", e.getMessage(), e);
            throw e;
        }
 
        try {
// Step 2: Find employee (read-only operation)
            Employee employee = findEmployeeByTempPayrollId(tempPayrollId);
 
// Step 3: Prepare updates in memory (NO database writes yet)
            Integer createdBy = categoryInfo.getCreatedBy();
            Integer updatedBy = categoryInfo.getUpdatedBy();
            prepareCategoryInfoUpdates(categoryInfo, employee, updatedBy);
 
// Step 4: Save to database ONLY after all validations pass
            employeeRepository.save(employee);
 
// Step 5: Save or update EmpSubject if subjectId and agreedPeriodsPerWeek are provided
            saveOrUpdateEmpSubject(employee, categoryInfo, createdBy, updatedBy);
 
            logger.info("✅ Updated category info for emp_id: {} (tempPayrollId: {})", employee.getEmp_id(),
                    tempPayrollId);
// Return the saved DTO object
            return categoryInfo;
 
        } catch (Exception e) {
            logger.error("❌ ERROR: Category Info save failed. Error: {}", e.getMessage(), e);
            throw e;
        }
    }
 
    /**
     * API 8: Save Bank Info (Tab 8)
     *
     * @param tempPayrollId Temp Payroll ID
     * @param bankInfo      Bank Info DTO
     * @return Saved BankInfoDTO object
     */
    public BankInfoDTO saveBankInfo(String tempPayrollId, BankInfoDTO bankInfo) {
        logger.info("Saving Bank Info for tempPayrollId: {}", tempPayrollId);
 
        try {
// Step 1: Validate DTO data BEFORE any database operations
            validateBankInfo(bankInfo);
        } catch (Exception e) {
            logger.error("❌ ERROR: Bank Info validation failed. NO data saved. Error: {}", e.getMessage(), e);
            throw e;
        }
 
        try {
// Step 2: Find employee (read-only operation)
            Employee employee = findEmployeeByTempPayrollId(tempPayrollId);
 
// Step 3: Prepare entities in memory (NO database writes yet)
            Integer createdBy = bankInfo.getCreatedBy();
            Integer updatedBy = bankInfo.getUpdatedBy();
            List<BankDetails> bankEntities = prepareBankEntities(bankInfo, employee, createdBy);
 
// Step 4: Save to database ONLY after all validations pass
            updateOrCreateBankEntities(bankEntities, employee, updatedBy);
 
            logger.info("✅ Saved {} bank account records for emp_id: {} (tempPayrollId: {})", bankEntities.size(),
                    employee.getEmp_id(), tempPayrollId);
// Return the saved DTO object
            return bankInfo;
 
        } catch (Exception e) {
            logger.error("❌ ERROR: Bank Info save failed. Error: {}", e.getMessage(), e);
            throw e;
        }
    }
 
    /**
     * API 9: Save Agreement Info (Tab 9) When agreement is submitted, change
     * employee status from "Incompleted" to "Pending at DO" (if category is NOT "school")
     *
     * Business Logic:
     * - If category is "school": Status remains unchanged (keeps current status)
     * - If category is "college" or any other: Status changes from "Incompleted" to "Pending at DO"
     *
     * @param tempPayrollId Temp Payroll ID
     * @param agreementInfo Agreement Info DTO
     * @return Saved AgreementInfoDTO object
     */
    public AgreementInfoDTO saveAgreementInfo(String tempPayrollId, AgreementInfoDTO agreementInfo) {
        logger.info("Saving Agreement Info for tempPayrollId: {}", tempPayrollId);
 
        try {
// Step 1: Validate DTO data BEFORE any database operations
            validateAgreementInfo(agreementInfo);
        } catch (Exception e) {
            logger.error("❌ ERROR: Agreement Info validation failed. NO data saved. Error: {}", e.getMessage(), e);
            throw e;
        }
 
        try {
// Step 2: Find employee (read-only operation)
            Employee employee = findEmployeeByTempPayrollId(tempPayrollId);
 
// Step 3: Prepare updates in memory (NO database writes yet)
            Integer createdBy = agreementInfo.getCreatedBy();
            Integer updatedBy = agreementInfo.getUpdatedBy();
            prepareAgreementInfoUpdates(agreementInfo, employee, updatedBy);
           
// Step 3.5: Change employee status from "Incompleted" to "Pending at DO" when agreement is submitted
// BUT: If category is "school", do NOT change the status (keep current status)
// Category is passed by user in the request (case-insensitive comparison)
            String categoryName = null;
            if (agreementInfo.getCategory() != null && !agreementInfo.getCategory().trim().isEmpty()) {
                categoryName = agreementInfo.getCategory().trim();
            }
           
            if (categoryName != null && "school".equalsIgnoreCase(categoryName)) {
                logger.info("User provided category is '{}' (school) - status will NOT be changed. Current status: {}",
                        categoryName,
                        employee.getEmp_check_list_status_id() != null ?
                        employee.getEmp_check_list_status_id().getCheck_app_status_name() : "null");
            } else {
                // Category is "college" or any other (or null) - change status to "Pending at DO"
                changeStatusToPendingAtDO(employee);
                logger.info("User provided category is '{}' - status changed to 'Pending at DO'",
                        categoryName != null ? categoryName : "null/not provided");
            }
 
// Step 4: Save to database ONLY after all validations pass
            employeeRepository.save(employee);
            saveAgreementChequeDetails(agreementInfo, employee, createdBy, updatedBy);
 
            // Log message based on whether status was changed
            if (categoryName != null && "school".equalsIgnoreCase(categoryName)) {
                logger.info(
                        "✅ Saved agreement info for emp_id: {} (tempPayrollId: {}). Status NOT changed (user provided category is '{}')",
                        employee.getEmp_id(), tempPayrollId, categoryName);
            } else {
                logger.info(
                        "✅ Saved agreement info and changed status to 'Pending at DO' for emp_id: {} (tempPayrollId: {}). User provided category: '{}'",
                        employee.getEmp_id(), tempPayrollId, categoryName != null ? categoryName : "null/not provided");
            }
// Return the saved DTO object
            return agreementInfo;
 
        } catch (Exception e) {
            logger.error("❌ ERROR: Agreement Info save failed. Error: {}", e.getMessage(), e);
            throw e;
        }
    }
 
    /**
     * Forward Employee to Divisional Office (from Remaining Tabs)
     * Forwards employee to Divisional Office by updating status to "Pending at DO"
     * Also saves/updates salary information if provided in the request
     *
     * IMPORTANT: This method ONLY works when employee current status is "Incompleted" or "Back to Campus"
     * This allows forwarding to DO after campus submission or after being sent back to campus
     * If employee has any other status, this method will throw an error
     *
     * Flow:
     * 1. Find employee by temp_payroll_id
     * 2. Validate that current status is "Incompleted" or "Back to Campus" (required)
     * 3. Save/Update salary information if provided in request DTO
     * 4. Update org_id if provided
     * 5. Update app status to "Pending at DO" (when forwarding to Divisional Office)
     * 6. Clear remarks
     *
     * @param requestDTO ForwardToDivisionalOfficeResponseDTO containing tempPayrollId and salary details
     * @return ForwardToDivisionalOfficeResponseDTO with employee and status information
     * @throws ResourceNotFoundException if employee status is not "Incompleted" or "Back to Campus"
     */
    public ForwardToDivisionalOfficeResponseDTO forwardEmployeeToDivisionalOffice(ForwardToDivisionalOfficeResponseDTO requestDTO) {
        // Validation: Check if tempPayrollId is provided
        if (requestDTO.getTempPayrollId() == null || requestDTO.getTempPayrollId().trim().isEmpty()) {
            throw new ResourceNotFoundException("tempPayrollId is required. Please provide a valid temp_payroll_id.");
        }
       
        String tempPayrollId = requestDTO.getTempPayrollId();
        logger.info("Forwarding employee to Divisional Office - temp_payroll_id: {}", tempPayrollId);
 
        // Step 1: Find employee by temp_payroll_id
        Employee employee = employeeRepository.findByTempPayrollId(tempPayrollId.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with temp_payroll_id: '" + tempPayrollId +
                        "'. Please verify the temp_payroll_id is correct and the employee exists in the system."));
 
        Integer empId = employee.getEmp_id();
        logger.info("Found employee - temp_payroll_id: {}, emp_id: {}",
                employee.getTempPayrollId(), empId);
       
        // Additional validation: Check if employee is active
        if (employee.getIs_active() != 1) {
            throw new ResourceNotFoundException(
                    "Employee with temp_payroll_id: '" + tempPayrollId +
                    "' is not active. emp_id: " + empId);
        }
       
        // Validation: Check if current status is "Incompleted" or "Back to Campus" - forward to DO works for both statuses
        // This allows forwarding to DO after campus submission or after being sent back to campus
        if (employee.getEmp_check_list_status_id() == null) {
            throw new ResourceNotFoundException(
                    "Cannot forward employee to Divisional Office: Employee (emp_id: " + empId +
                    ", temp_payroll_id: '" + tempPayrollId +
                    "') does not have a status set. This method only works when employee status is 'Incompleted' or 'Back to Campus'.");
        }
       
        String currentStatusName = employee.getEmp_check_list_status_id().getCheck_app_status_name();
        if (!"Incompleted".equals(currentStatusName) && !"Back to Campus".equals(currentStatusName)) {
            throw new ResourceNotFoundException(
                    "Cannot forward employee to Divisional Office: Current employee status is '" + currentStatusName +
                    "' (emp_id: " + empId + ", temp_payroll_id: '" + tempPayrollId +
                    "'). This method only works when employee status is 'Incompleted' or 'Back to Campus'.");
        }
       
        logger.info("Employee (emp_id: {}) current status is '{}', proceeding with forward to Divisional Office", empId, currentStatusName);
 
        // Step 3: Save/Update salary information if provided in request DTO
        if (requestDTO.getMonthlyTakeHome() != null || requestDTO.getYearlyCtc() != null || requestDTO.getEmpStructureId() != null) {
            // Get emp_payment_type_id from BankDetails table (where emp_id matches)
            EmpPaymentType empPaymentType = null;
            List<BankDetails> bankDetailsList = bankDetailsRepository.findByEmpId_Emp_id(empId);
           
            if (bankDetailsList != null && !bankDetailsList.isEmpty()) {
                logger.info("Found {} BankDetails record(s) for emp_id: {}", bankDetailsList.size(), empId);
                // Get payment type from bank details - prefer salary account if available
                for (BankDetails bankDetail : bankDetailsList) {
                    if (bankDetail.getEmpPaymentType() != null) {
                        Integer paymentTypeId = bankDetail.getEmpPaymentType().getEmp_payment_type_id();
                        empPaymentType = empPaymentTypeRepository.findById(paymentTypeId).orElse(null);
                       
                        if (empPaymentType != null) {
                            // Prefer salary account if available
                            if ("SALARY".equalsIgnoreCase(bankDetail.getAccType())) {
                                logger.info("Using emp_payment_type_id from SALARY account type");
                                break;
                            }
                        }
                    }
                }
            }
           
            // Check if EmpSalaryInfo already exists for this emp_id, if yes update, else create new
            Optional<EmpSalaryInfo> existingSalaryInfoOpt = empSalaryInfoRepository.findByEmpIdAndIsActive(empId, 1);
            EmpSalaryInfo empSalaryInfo;
           
            if (existingSalaryInfoOpt.isPresent()) {
                empSalaryInfo = existingSalaryInfoOpt.get();
                logger.info("Found existing EmpSalaryInfo record (emp_sal_info_id: {}) for emp_id: {}, updating",
                        empSalaryInfo.getEmpSalInfoId(), empId);
            } else {
                empSalaryInfo = new EmpSalaryInfo();
                empSalaryInfo.setEmpId(employee);
                logger.info("No existing EmpSalaryInfo found for emp_id: {}, creating new record", empId);
            }
           
            // Set temp_payroll_id
            if (employee.getTempPayrollId() != null && !employee.getTempPayrollId().trim().isEmpty()) {
                empSalaryInfo.setTempPayrollId(employee.getTempPayrollId());
            }
         // Keep existing payroll_id if updating
            if (empSalaryInfo.getPayrollId() == null) {
                empSalaryInfo.setPayrollId(null);
            }
           
            // Set emp_payment_type_id from BankDetails
            empSalaryInfo.setEmpPaymentType(empPaymentType);
           
            // Set salary information from request DTO
            if (requestDTO.getMonthlyTakeHome() != null) {
                empSalaryInfo.setMonthlyTakeHomeFromDouble(requestDTO.getMonthlyTakeHome());
            }
            if (requestDTO.getCtcWords() != null) {
                empSalaryInfo.setCtcWordsFromString(requestDTO.getCtcWords());
            }
            if (requestDTO.getYearlyCtc() != null) {
                empSalaryInfo.setYearlyCtcFromDouble(requestDTO.getYearlyCtc());
            }
           
            // Set emp_structure_id
            if (requestDTO.getEmpStructureId() != null) {
                EmpStructure empStructure = empStructureRepository.findByIdAndIsActive(requestDTO.getEmpStructureId(), 1)
                        .orElseThrow(() -> new ResourceNotFoundException("Active EmpStructure not found with ID: " + requestDTO.getEmpStructureId()));
                empSalaryInfo.setEmpStructure(empStructure);
            }
           
            // Set grade_id (optional)
            if (requestDTO.getGradeId() != null) {
                EmpGrade empGrade = empGradeRepository.findByIdAndIsActive(requestDTO.getGradeId(), 1)
                        .orElseThrow(() -> new ResourceNotFoundException("Active EmpGrade not found with ID: " + requestDTO.getGradeId()));
                empSalaryInfo.setGrade(empGrade);
            } else {
                empSalaryInfo.setGrade(null);
            }
           
            // Set cost_center_id (optional)
            if (requestDTO.getCostCenterId() != null) {
                CostCenter costCenter = costCenterRepository.findById(requestDTO.getCostCenterId())
                        .orElseThrow(() -> new ResourceNotFoundException("CostCenter not found with ID: " + requestDTO.getCostCenterId()));
                empSalaryInfo.setCostCenter(costCenter);
            } else {
                empSalaryInfo.setCostCenter(null);
            }
           
            // Set is_pf_eligible
            if (requestDTO.getIsPfEligible() != null) {
                empSalaryInfo.setIsPfEligible(requestDTO.getIsPfEligible() ? 1 : 0);
            } else {
                empSalaryInfo.setIsPfEligible(0);
            }
           
            // Set is_esi_eligible
            if (requestDTO.getIsEsiEligible() != null) {
                empSalaryInfo.setIsEsiEligible(requestDTO.getIsEsiEligible() ? 1 : 0);
            } else {
                empSalaryInfo.setIsEsiEligible(0);
            }
           
            empSalaryInfo.setIsActive(1);
           
            // Set audit fields (CreatedBy/Date for new, UpdatedBy/Date for existing)
            // User passes these in the DTO
            if (empSalaryInfo.getEmpSalInfoId() == null) {
                // New record
                if (requestDTO.getCreatedBy() != null) {
                    empSalaryInfo.setCreatedBy(requestDTO.getCreatedBy());
                }
                if (requestDTO.getCreatedDate() != null) {
                    empSalaryInfo.setCreatedDate(requestDTO.getCreatedDate());
                } else {
                    empSalaryInfo.setCreatedDate(LocalDateTime.now());
                }
            } else {
                // Existing record - update the updated_by/date fields with the passed info
                if (requestDTO.getCreatedBy() != null) {
                    empSalaryInfo.setUpdatedBy(requestDTO.getCreatedBy());
                }
                if (requestDTO.getCreatedDate() != null) {
                    empSalaryInfo.setUpdatedDate(requestDTO.getCreatedDate());
                } else {
                    empSalaryInfo.setUpdatedDate(LocalDateTime.now());
                }
            }
 
            // Save salary info
            empSalaryInfoRepository.save(empSalaryInfo);
            empSalaryInfoRepository.flush();
            logger.info("Saved/Updated salary information for emp_id: {}", empId);
           
            // Save/Update PF/ESI/UAN details if provided
            EmpPfDetails empPfDetails = empPfDetailsRepository.findByEmployeeId(empId).orElse(new EmpPfDetails());
            boolean pfDetailsNeedsSave = false;
           
            if (requestDTO.getPfNo() != null && !requestDTO.getPfNo().trim().isEmpty()) {
                empPfDetails.setPf_no(requestDTO.getPfNo());
                pfDetailsNeedsSave = true;
            }
            if (requestDTO.getPfJoinDate() != null) {
                empPfDetails.setPf_join_date(requestDTO.getPfJoinDate());
                pfDetailsNeedsSave = true;
            }
            if (requestDTO.getEsiNo() != null) {
                empPfDetails.setEsi_no(requestDTO.getEsiNo());
                pfDetailsNeedsSave = true;
            }
            if (requestDTO.getUanNo() != null) {
                empPfDetails.setUan_no(requestDTO.getUanNo());
                pfDetailsNeedsSave = true;
            }
            if (empSalaryInfo.getIsPfEligible() != null && empSalaryInfo.getIsPfEligible() == 1) {
                pfDetailsNeedsSave = true;
            }
            if (empSalaryInfo.getIsEsiEligible() != null && empSalaryInfo.getIsEsiEligible() == 1) {
                pfDetailsNeedsSave = true;
            }
           
            if (pfDetailsNeedsSave) {
                empPfDetails.setEmployee_id(employee);
                empPfDetails.setIs_active(1);
                if (empPfDetails.getEmp_pf_esi_uan_info_id() == 0) {
                    // New record - set created_by and created_date (both are NOT NULL)
                    // Use created_by from request DTO if available, otherwise fallback to employee's created_by
                    if (requestDTO.getCreatedBy() != null) {
                        empPfDetails.setCreated_by(requestDTO.getCreatedBy());
                    } else {
                        empPfDetails.setCreated_by(employee.getCreated_by());
                    }
                    // Set created_date - use from request DTO if available, otherwise use current time
                    if (requestDTO.getCreatedDate() != null) {
                        empPfDetails.setCreated_date(requestDTO.getCreatedDate());
                    } else {
                        empPfDetails.setCreated_date(LocalDateTime.now());
                    }
                } else {
                    // Existing record - set updated_by and updated_date
                    if (requestDTO.getCreatedBy() != null) {
                        empPfDetails.setUpdated_by(requestDTO.getCreatedBy());
                    }
                    empPfDetails.setUpdated_date(LocalDateTime.now());
                }
                empPfDetailsRepository.save(empPfDetails);
                logger.info("Saved/Updated PF/ESI/UAN details for emp_id: {}", empId);
            }
        }
       
        // Step 4: Update org_id if provided
        if (requestDTO.getOrgId() != null) {
            Organization org = organizationRepository.findById(requestDTO.getOrgId())
                    .orElseThrow(() -> new ResourceNotFoundException("Organization not found with ID: " + requestDTO.getOrgId()));
            employee.setOrg_id(org);
            logger.info("Updated org_id (Company) for employee (emp_id: {}): {}", empId, requestDTO.getOrgId());
        }
       
        // Step 5: Update app status to "Pending at DO" when forwarding to Divisional Office
        EmployeeCheckListStatus pendingAtDOStatus = employeeCheckListStatusRepository.findByCheck_app_status_name("Pending at DO")
                .orElseThrow(() -> new ResourceNotFoundException("EmployeeCheckListStatus with name 'Pending at DO' not found"));
        employee.setEmp_check_list_status_id(pendingAtDOStatus);
        logger.info("Updated employee (emp_id: {}) app status to 'Pending at DO' (ID: {}) when forwarding to divisional office", empId, pendingAtDOStatus.getEmp_app_status_id());
       
        // Step 6: Clear remarks when forwarding to divisional office (after rectification)
        employee.setRemarks(null);
        logger.info("Cleared remarks for employee (emp_id: {}) when forwarding to divisional office", empId);
       
        // Save employee updates (org_id, status, and cleared remarks)
        employeeRepository.save(employee);
       
        logger.info("✅ Successfully forwarded employee (emp_id: {}, temp_payroll_id: '{}') to Divisional Office",
                empId, tempPayrollId);
       
        // Create and return response DTO with all the data
        ForwardToDivisionalOfficeResponseDTO response = new ForwardToDivisionalOfficeResponseDTO();
        response.setTempPayrollId(tempPayrollId);
        response.setEmpId(empId);
        response.setPreviousStatus(currentStatusName);
        response.setNewStatus("Pending at DO");
        response.setMessage("Employee successfully forwarded to Divisional Office");
       
        // Populate salary information from request DTO or fetch from database
        Optional<EmpSalaryInfo> empSalaryInfoOpt = empSalaryInfoRepository.findByEmpIdAndIsActive(empId, 1);
        if (empSalaryInfoOpt.isPresent()) {
            EmpSalaryInfo empSalaryInfo = empSalaryInfoOpt.get();
           
            // Set salary information from saved data
            response.setMonthlyTakeHome(empSalaryInfo.getMonthlyTakeHomeAsDouble());
            response.setCtcWords(empSalaryInfo.getCtcWordsAsString());
            response.setYearlyCtc(empSalaryInfo.getYearlyCtcAsDouble());
            response.setEmpStructureId(empSalaryInfo.getEmpStructure() != null ? empSalaryInfo.getEmpStructure().getEmpStructureId() : null);
            response.setGradeId(empSalaryInfo.getGrade() != null ? empSalaryInfo.getGrade().getEmpGradeId() : null);
            response.setCostCenterId(empSalaryInfo.getCostCenter() != null ? empSalaryInfo.getCostCenter().getCostCenterId() : null);
            response.setOrgId(employee.getOrg_id() != null ? employee.getOrg_id().getOrganizationId() : null);
            response.setIsPfEligible(empSalaryInfo.getIsPfEligible() != null && empSalaryInfo.getIsPfEligible() == 1);
            response.setIsEsiEligible(empSalaryInfo.getIsEsiEligible() != null && empSalaryInfo.getIsEsiEligible() == 1);
           
            // Get PF/ESI/UAN details from EmpPfDetails
            Optional<EmpPfDetails> empPfDetailsOpt = empPfDetailsRepository.findByEmployeeId(empId);
            if (empPfDetailsOpt.isPresent()) {
                EmpPfDetails empPfDetails = empPfDetailsOpt.get();
                response.setPfNo(empPfDetails.getPf_no());
                response.setPfJoinDate(empPfDetails.getPf_join_date());
                response.setEsiNo(empPfDetails.getEsi_no());
                response.setUanNo(empPfDetails.getUan_no());
            }
        } else {
            // If no salary info exists, use values from request DTO
            response.setMonthlyTakeHome(requestDTO.getMonthlyTakeHome());
            response.setCtcWords(requestDTO.getCtcWords());
            response.setYearlyCtc(requestDTO.getYearlyCtc());
            response.setEmpStructureId(requestDTO.getEmpStructureId());
            response.setGradeId(requestDTO.getGradeId());
            response.setCostCenterId(requestDTO.getCostCenterId());
            response.setOrgId(requestDTO.getOrgId());
            response.setIsPfEligible(requestDTO.getIsPfEligible());
            response.setIsEsiEligible(requestDTO.getIsEsiEligible());
            response.setPfNo(requestDTO.getPfNo());
            response.setPfJoinDate(requestDTO.getPfJoinDate());
            response.setEsiNo(requestDTO.getEsiNo());
            response.setUanNo(requestDTO.getUanNo());
        }
       
        return response;
    }
 
// ============================================================================
// HELPER METHODS - Employee Operations
// ============================================================================
 
    /**
     * Helper: Find Employee by tempPayrollId
     * Validates that the employee is active (is_active = 1) before allowing updates
     */
    private Employee findEmployeeByTempPayrollId(String tempPayrollId) {
        Employee employee = employeeRepository.findByTempPayrollId(tempPayrollId.trim()).orElseThrow(
                () -> new ResourceNotFoundException("Employee not found with tempPayrollId: " + tempPayrollId));
       
        // Validate that employee is active before allowing updates
        if (employee.getIs_active() != 1) {
            throw new ResourceNotFoundException(
                    "Cannot update employee with tempPayrollId: " + tempPayrollId +
                    ". Employee is inactive (is_active = 0). Only active employees can be updated.");
        }
       
        logger.info("Found active employee with emp_id: {} for tempPayrollId: {}", employee.getEmp_id(), tempPayrollId);
        return employee;
    }
 
// Note: updateHighestQualification method removed
// qualification_id is now set from BasicInfoDTO.qualificationId (not from qualification tab's isHighest)
 
    /**
     * Helper: Change employee status to "Pending at DO"
     */
    private void changeStatusToPendingAtDO(Employee employee) {
        EmployeeCheckListStatus pendingAtDOStatus = employeeCheckListStatusRepository
                .findByCheck_app_status_name("Pending at DO").orElseThrow(() -> new ResourceNotFoundException(
                        "EmployeeCheckListStatus with name 'Pending at DO' not found"));
        employee.setEmp_check_list_status_id(pendingAtDOStatus);
    }
 
// ============================================================================
// HELPER METHODS - Qualification Operations
// ============================================================================
 
    /**
     * Helper: Prepare Qualification entities WITHOUT saving
     */
    private List<EmpQualification> prepareQualificationEntities(QualificationDTO qualification, Employee employee,
            Integer createdBy) {
        List<EmpQualification> qualificationList = new ArrayList<>();
 
        if (qualification == null || qualification.getQualifications() == null
                || qualification.getQualifications().isEmpty()) {
            return qualificationList;
        }
 
        for (QualificationDTO.QualificationDetailsDTO qualDTO : qualification.getQualifications()) {
            if (qualDTO != null) {
                EmpQualification empQual = createQualificationEntity(qualDTO, employee, createdBy);
                qualificationList.add(empQual);
            }
        }
 
        return qualificationList;
    }
 
    /**
     * Helper: Create Qualification entity
     */
    private EmpQualification createQualificationEntity(QualificationDTO.QualificationDetailsDTO qualDTO,
            Employee employee, Integer createdBy) {
        EmpQualification empQual = new EmpQualification();
        empQual.setEmp_id(employee);
        empQual.setPassedout_year(qualDTO.getPassedOutYear());
        empQual.setSpecialization(qualDTO.getSpecialization());
        empQual.setUniversity(qualDTO.getUniversity());
        empQual.setInstitute(qualDTO.getInstitute());
 
        if (qualDTO.getQualificationId() != null) {
            empQual.setQualification_id(qualificationRepository.findById(qualDTO.getQualificationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Qualification not found")));
        }
 
        if (qualDTO.getQualificationDegreeId() != null) {
            empQual.setQualification_degree_id(
                    qualificationDegreeRepository.findById(qualDTO.getQualificationDegreeId())
                            .orElseThrow(() -> new ResourceNotFoundException("QualificationDegree not found")));
        }
 
        // Logic for certificateFile - save as a document if provided and valid
        if (qualDTO.getCertificateFile() != null && !qualDTO.getCertificateFile().trim().isEmpty()
                && !"string".equalsIgnoreCase(qualDTO.getCertificateFile().trim())) {
           
            // Try to find if this document already exists for this employee and path to avoid duplicates
            // For simplicity and matching your "don't post if string" request, we create it here
            EmpDocuments doc = new EmpDocuments();
            doc.setEmp_id(employee);
            doc.setDoc_path(qualDTO.getCertificateFile().trim());
            doc.setIs_verified(0);
            doc.setIs_active(1);
            doc.setCreated_by(createdBy != null ? createdBy : 1);
            doc.setCreated_date(LocalDateTime.now());
           
            // Use qualificationId as docTypeId (Educational Document)
            if (qualDTO.getQualificationId() != null) {
                empDocTypeRepository.findById(qualDTO.getQualificationId()).ifPresent(docType -> {
                    doc.setEmp_doc_type_id(docType);
                    empDocumentsRepository.save(doc);
                    logger.info("Saved certificate for qualification: {} (doc_type_id: {})",
                            docType.getDoc_name(), docType.getDoc_type_id());
                });
            }
        }
 
        empQual.setIs_active(1);
 
// Set created_by only if provided from frontend, otherwise don't set it (entity default will be used)
        if (createdBy != null && createdBy > 0) {
            empQual.setCreated_by(createdBy);
            empQual.setCreated_date(LocalDateTime.now());
        }
// If createdBy is null, don't set created_by - entity default value will be used
 
        return empQual;
    }
 
    /**
     * Helper: Update or create Qualification entities
     */
    private void updateOrCreateQualificationEntities(List<EmpQualification> newQualification, Employee employee,
            Integer updatedBy) {
        int empId = employee.getEmp_id();
 
        List<EmpQualification> existingQualification = empQualificationRepository.findAll().stream().filter(
                qual -> qual.getEmp_id() != null && qual.getEmp_id().getEmp_id() == empId && qual.getIs_active() == 1)
                .collect(Collectors.toList());
 
        int maxSize = Math.max(newQualification.size(), existingQualification.size());
 
        for (int i = 0; i < maxSize; i++) {
            if (i < newQualification.size()) {
                EmpQualification newQual = newQualification.get(i);
                newQual.setEmp_id(employee);
                newQual.setIs_active(1);
 
                if (i < existingQualification.size()) {
                    EmpQualification existing = existingQualification.get(i);
                    updateQualificationFields(existing, newQual);
// Set updated_by and updated_date on update
                    if (updatedBy != null && updatedBy > 0) {
                        existing.setUpdated_by(updatedBy);
                        existing.setUpdated_date(LocalDateTime.now());
                    }
                    empQualificationRepository.save(existing);
                } else {
                    empQualificationRepository.save(newQual);
                }
            } else if (i < existingQualification.size()) {
                existingQualification.get(i).setIs_active(0);
                if (updatedBy != null && updatedBy > 0) {
                    existingQualification.get(i).setUpdated_by(updatedBy);
                    existingQualification.get(i).setUpdated_date(LocalDateTime.now());
                }
                empQualificationRepository.save(existingQualification.get(i));
            }
        }
    }
 
    /**
     * Helper: Update Qualification fields
     */
    private void updateQualificationFields(EmpQualification target, EmpQualification source) {
        target.setQualification_id(source.getQualification_id());
        target.setQualification_degree_id(source.getQualification_degree_id());
        target.setUniversity(source.getUniversity());
        target.setInstitute(source.getInstitute());
        target.setPassedout_year(source.getPassedout_year());
        target.setSpecialization(source.getSpecialization());
        target.setIs_active(source.getIs_active());
    }
 
// ============================================================================
// HELPER METHODS - Document Operations
// ============================================================================
 
    /**
     * Helper: Prepare Document entities WITHOUT saving
     */
    private List<EmpDocuments> prepareDocumentEntities(DocumentDTO documents, Employee employee, Integer createdBy) {
        List<EmpDocuments> documentList = new ArrayList<>();
 
        if (documents == null || documents.getDocuments() == null || documents.getDocuments().isEmpty()) {
            return documentList;
        }
 
        for (DocumentDTO.DocumentDetailsDTO docDTO : documents.getDocuments()) {
            if (docDTO != null) {
                // Only prepare entity if path is NOT null, NOT empty, and NOT "string" (Swagger default)
                if (docDTO.getDocPath() != null && !docDTO.getDocPath().trim().isEmpty()
                        && !"string".equalsIgnoreCase(docDTO.getDocPath().trim())) {
                    EmpDocuments doc = createDocumentEntity(docDTO, employee, createdBy);
                    documentList.add(doc);
                }
            }
        }
 
        return documentList;
    }
 
    /**
     * Helper: Create Document entity
     */
    private EmpDocuments createDocumentEntity(DocumentDTO.DocumentDetailsDTO docDTO, Employee employee,
            Integer createdBy) {
        EmpDocuments doc = new EmpDocuments();
        doc.setEmp_id(employee);
        doc.setDoc_path(docDTO.getDocPath());
        doc.setIs_verified(docDTO.getIsVerified() != null && docDTO.getIsVerified() ? 1 : 0);
        doc.setIs_active(1);
 
        if (docDTO.getDocTypeId() != null) {
            doc.setEmp_doc_type_id(empDocTypeRepository.findById(docDTO.getDocTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("DocumentType not found")));
        } else {
            throw new ResourceNotFoundException("Document Type ID is required (NOT NULL column)");
        }
 
// Set created_by only if provided from frontend, otherwise don't set it (entity default will be used)
        if (createdBy != null && createdBy > 0) {
            doc.setCreated_by(createdBy);
            doc.setCreated_date(LocalDateTime.now());
        }
// If createdBy is null, don't set created_by - entity default value will be used
 
        return doc;
    }
 
    /**
     * Helper: Update or create Document entities
     */
    private void updateOrCreateDocumentEntities(List<EmpDocuments> newDocuments, Employee employee, Integer updatedBy) {
        int empId = employee.getEmp_id();
 
        List<EmpDocuments> existingDocuments = empDocumentsRepository.findAll().stream().filter(
                doc -> doc.getEmp_id() != null && doc.getEmp_id().getEmp_id() == empId && doc.getIs_active() == 1)
                .collect(Collectors.toList());
 
        int maxSize = Math.max(newDocuments.size(), existingDocuments.size());
 
        for (int i = 0; i < maxSize; i++) {
            if (i < newDocuments.size()) {
                EmpDocuments newDoc = newDocuments.get(i);
                newDoc.setEmp_id(employee);
                newDoc.setIs_active(1);
 
                if (i < existingDocuments.size()) {
                    EmpDocuments existing = existingDocuments.get(i);
                    updateDocumentFields(existing, newDoc);
// Set updated_by and updated_date on update
                    if (updatedBy != null && updatedBy > 0) {
                        existing.setUpdated_by(updatedBy);
                        existing.setUpdated_date(LocalDateTime.now());
                    }
                    empDocumentsRepository.save(existing);
                } else {
                    empDocumentsRepository.save(newDoc);
                }
            } else if (i < existingDocuments.size()) {
                existingDocuments.get(i).setIs_active(0);
                if (updatedBy != null && updatedBy > 0) {
                    existingDocuments.get(i).setUpdated_by(updatedBy);
                    existingDocuments.get(i).setUpdated_date(LocalDateTime.now());
                }
                empDocumentsRepository.save(existingDocuments.get(i));
            }
        }
    }
 
    /**
     * Helper: Update Document fields
     */
    private void updateDocumentFields(EmpDocuments target, EmpDocuments source) {
        target.setEmp_doc_type_id(source.getEmp_doc_type_id());
        target.setDoc_path(source.getDoc_path());
        target.setIs_verified(source.getIs_verified());
        target.setIs_active(source.getIs_active());
    }
 
// ============================================================================
// HELPER METHODS - Category Info Operations
// ============================================================================
 
    /**
     * Helper: Prepare Category Info updates WITHOUT saving
     */
    private void prepareCategoryInfoUpdates(CategoryInfoDTO categoryInfo, Employee employee, Integer updatedBy) {
        if (categoryInfo == null)
            return;
 
        if (categoryInfo.getEmployeeTypeId() != null) {
            employee.setEmployee_type_id(employeeTypeRepository.findById(categoryInfo.getEmployeeTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Employee Type not found with ID: " + categoryInfo.getEmployeeTypeId())));
        }
 
        if (categoryInfo.getDepartmentId() != null) {
            employee.setDepartment(departmentRepository.findById(categoryInfo.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Department not found with ID: " + categoryInfo.getDepartmentId())));
        }
 
        if (categoryInfo.getDesignationId() != null) {
            employee.setDesignation(designationRepository.findById(categoryInfo.getDesignationId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Designation not found with ID: " + categoryInfo.getDesignationId())));
        }
 
// Set updated_by and updated_date on Employee table ONLY if status is "Confirm"
        if (updatedBy != null && updatedBy > 0 && employee.getEmp_check_list_status_id() != null
                && "Confirm".equals(employee.getEmp_check_list_status_id().getCheck_app_status_name())) {
            employee.setUpdated_by(updatedBy);
            employee.setUpdated_date(LocalDateTime.now());
        }
    }
 
    /**
     * Helper: Save or update EmpSubject entity
     */
    /**
     * Helper: Save or update EmpSubject entity
     */
    private void saveOrUpdateEmpSubject(Employee employee, CategoryInfoDTO categoryInfo, Integer createdBy,
            Integer updatedBy) {
       
        int empId = employee.getEmp_id();
       
        // Logic:
        // 1. If Employee Type is "Teach" (ID = 1):
        //    - If subjectId is provided, save/update EmpSubject.
        // 2. If Employee Type is "Non-Teach" (ID != 1):
        //    - If there are existing active records, deactivate them (in case user switched from Teach to Non-Teach).
       
        boolean isTeach = false;
        if (employee.getEmployee_type_id() != null && employee.getEmployee_type_id().getEmp_type_id() == 1) {
            isTeach = true;
        }
     // Find existing active EmpSubject records
        List<com.employee.entity.EmpSubject> existingEmpSubjects = empSubjectRepository.findAll().stream()
                .filter(es -> es.getEmp_id() != null && es.getEmp_id().getEmp_id() == empId && es.getIs_active() == 1)
                .collect(Collectors.toList());
 
        if (isTeach) {
            // CASE 1: TEACH
           
            // Only proceed if subjectId is provided
            if (categoryInfo.getSubjectId() == null || categoryInfo.getSubjectId() <= 0) {
                return; // Subject is optional even for Teach, if not provided do nothing
            }
   
            if (categoryInfo.getAgreedPeriodsPerWeek() == null) {
                throw new ResourceNotFoundException(
                        "Agreed Periods Per Week is required (NOT NULL column) when subjectId is provided");
            }
   
            if (!existingEmpSubjects.isEmpty()) {
                // Update first existing record
                com.employee.entity.EmpSubject existing = existingEmpSubjects.get(0);
                existing.setSubject_id(subjectRepository.findById(categoryInfo.getSubjectId()).orElseThrow(
                        () -> new ResourceNotFoundException("Subject not found with ID: " + categoryInfo.getSubjectId())));
                existing.setAgree_no_period(categoryInfo.getAgreedPeriodsPerWeek());
                // Set orientation_id - nullable, can be null
                if (categoryInfo.getOrientationId() != null && categoryInfo.getOrientationId() > 0) {
                    Orientation orientation = orientationRepository.findById(categoryInfo.getOrientationId())
                            .orElseThrow(() -> new ResourceNotFoundException("Orientation not found with ID: " + categoryInfo.getOrientationId()));
                    existing.setOrientation_id(orientation);
                } else {
                    existing.setOrientation_id(null);
                }
                existing.setIs_active(1);
                // Set updated_by and updated_date on update
                if (updatedBy != null && updatedBy > 0) {
                    existing.setUpdated_by(updatedBy);
                    existing.setUpdated_date(LocalDateTime.now());
                }
                empSubjectRepository.save(existing);
                logger.info("Updated existing EmpSubject for employee (emp_id: {})", employee.getEmp_id());
   
                // Mark other existing records as inactive if there are multiple
                if (existingEmpSubjects.size() > 1) {
                    for (int i = 1; i < existingEmpSubjects.size(); i++) {
                        existingEmpSubjects.get(i).setIs_active(0);
                        if (updatedBy != null && updatedBy > 0) {
                            existingEmpSubjects.get(i).setUpdated_by(updatedBy);
                            existingEmpSubjects.get(i).setUpdated_date(LocalDateTime.now());
                        }
                        empSubjectRepository.save(existingEmpSubjects.get(i));
                    }
                    logger.info("Marked {} additional EmpSubject records as inactive for employee (emp_id: {})",
                            existingEmpSubjects.size() - 1, employee.getEmp_id());
                }
            } else {
                // Create new record
                com.employee.entity.EmpSubject empSubject = new com.employee.entity.EmpSubject();
                empSubject.setEmp_id(employee);
                empSubject.setSubject_id(subjectRepository.findById(categoryInfo.getSubjectId()).orElseThrow(
                        () -> new ResourceNotFoundException("Subject not found with ID: " + categoryInfo.getSubjectId())));
                empSubject.setAgree_no_period(categoryInfo.getAgreedPeriodsPerWeek());
                // Set orientation_id - nullable, can be null
                if (categoryInfo.getOrientationId() != null && categoryInfo.getOrientationId() > 0) {
                    Orientation orientation = orientationRepository.findById(categoryInfo.getOrientationId())
                            .orElseThrow(() -> new ResourceNotFoundException("Orientation not found with ID: " + categoryInfo.getOrientationId()));
                    empSubject.setOrientation_id(orientation);
                } else {
                    empSubject.setOrientation_id(null);
                }
                empSubject.setIs_active(1);
   
                // Set created_by only if provided from frontend, otherwise don't set it (entity default will be used)
                if (createdBy != null && createdBy > 0) {
                    empSubject.setCreated_by(createdBy);
                    empSubject.setCreated_date(LocalDateTime.now());
                }
                // If createdBy is null, don't set created_by - entity default value will be used
   
                empSubjectRepository.save(empSubject);
                logger.info("Created new EmpSubject for employee (emp_id: {})", employee.getEmp_id());
            }
        } else {
            // CASE 2: NON-TEACH
            // If employee is NOT Teach (e.g., Non-Teach), ensure no active subject records exist.
            // If they do (e.g. mistakenly posted as Teach before), deactivate them.
           
            if (!existingEmpSubjects.isEmpty()) {
                for (com.employee.entity.EmpSubject existing : existingEmpSubjects) {
                    existing.setIs_active(0);
                    if (updatedBy != null && updatedBy > 0) {
                        existing.setUpdated_by(updatedBy);
                        existing.setUpdated_date(LocalDateTime.now());
                    }
                    empSubjectRepository.save(existing);
                }
                logger.info("Deactivated {} EmpSubject records because employee (emp_id: {}) is Non-Teach (Type ID: {})",
                        existingEmpSubjects.size(), employee.getEmp_id(),
                        (employee.getEmployee_type_id() != null ? employee.getEmployee_type_id().getEmp_type_id() : "null"));
            }
        }
    }
 
// ============================================================================
// HELPER METHODS - Bank Operations
// ============================================================================
 
    /**
     * Helper: Prepare Bank entities WITHOUT saving
     */
    private List<BankDetails> prepareBankEntities(BankInfoDTO bankInfo, Employee employee, Integer createdBy) {
        List<BankDetails> bankList = new ArrayList<>();
 
        if (bankInfo == null)
            return bankList;
 
        EmpPaymentType paymentType = null;
 
        if (bankInfo.getPaymentTypeId() != null && bankInfo.getPaymentTypeId() > 0) {
            paymentType = empPaymentTypeRepository.findByIdAndIsActive(bankInfo.getPaymentTypeId(), 1)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Active Payment Type not found with ID: " + bankInfo.getPaymentTypeId()));
        }
 
        if (bankInfo.getPersonalAccount() != null) {
            BankDetails personalAccount = new BankDetails();
            personalAccount.setEmpId(employee);
            personalAccount.setAccType("PERSONAL");
            personalAccount.setBankName(bankInfo.getPersonalAccount().getBankName());
            personalAccount.setBankBranch(null);
            personalAccount.setBankHolderName(bankInfo.getPersonalAccount().getAccountHolderName());
            if (bankInfo.getSalaryAccount() == null) {
                personalAccount.setEmpPaymentType(paymentType);
            } else {
                personalAccount.setEmpPaymentType(null);
            }
 
            if (bankInfo.getPersonalAccount().getAccountNo() != null) {
                try {
                    Long accNoLong = Long.parseLong(bankInfo.getPersonalAccount().getAccountNo());
                    personalAccount.setAccNo(accNoLong);
                } catch (NumberFormatException e) {
                    throw new ResourceNotFoundException(
                            "Invalid account number format. Account number must be numeric.");
                }
            } else {
                throw new ResourceNotFoundException("Account number is required (NOT NULL column)");
            }
 
            if (bankInfo.getPersonalAccount().getIfscCode() != null) {
                personalAccount.setIfscCode(bankInfo.getPersonalAccount().getIfscCode());
            } else {
                throw new ResourceNotFoundException("IFSC Code is required (NOT NULL column)");
            }
 
// payableAt is only for salary account, not personal account
            personalAccount.setIsActive(1);
 
// Set created_by only if provided from frontend, otherwise don't set it (entity default will be used)
            if (createdBy != null && createdBy > 0) {
                personalAccount.setCreatedBy(createdBy);
                personalAccount.setCreatedDate(LocalDateTime.now());
            }
// If createdBy is null, don't set created_by - entity default value will be used
 
            bankList.add(personalAccount);
        }
 
        if (bankInfo.getSalaryAccount() != null) {
            BankDetails salaryAccount = new BankDetails();
            salaryAccount.setEmpId(employee);
            salaryAccount.setAccType("SALARY");
 
// Handle bank branch: can provide either ID or name
            if (bankInfo.getBankBranchId() != null && bankInfo.getBankBranchId() > 0) {
// If ID is provided, validate it exists in master and get the name
                if (bankInfo.getBankBranchName() != null && !bankInfo.getBankBranchName().trim().isEmpty()) {
                    throw new ResourceNotFoundException(
                            "Please provide either bankBranchId OR bankBranchName, not both.");
                }
                OrgBankBranch orgBankBranch = orgBankBranchRepository.findById(bankInfo.getBankBranchId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Organization Bank Branch not found with ID: " + bankInfo.getBankBranchId()));
 
                if (orgBankBranch.getBranch_name() != null && !orgBankBranch.getBranch_name().trim().isEmpty()) {
                    salaryAccount.setBankBranch(orgBankBranch.getBranch_name());
                }
            } else if (bankInfo.getBankBranchName() != null && !bankInfo.getBankBranchName().trim().isEmpty()) {
// If name is provided directly, store it (no validation against master)
                salaryAccount.setBankBranch(bankInfo.getBankBranchName().trim());
            }
// If neither ID nor name is provided, bankBranch remains null (optional field)
 
            salaryAccount.setEmpPaymentType(paymentType);
 
            if (bankInfo.getBankId() != null && bankInfo.getBankId() > 0) {
                OrgBank orgBank = orgBankRepository.findById(bankInfo.getBankId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Organization Bank not found with ID: " + bankInfo.getBankId()));
 
                if (orgBank.getBank_name() != null && !orgBank.getBank_name().trim().isEmpty()) {
                    salaryAccount.setBankName(orgBank.getBank_name());
                }
 
                if (bankInfo.getSalaryAccount().getIfscCode() != null
                        && !bankInfo.getSalaryAccount().getIfscCode().trim().isEmpty()) {
                    salaryAccount.setIfscCode(bankInfo.getSalaryAccount().getIfscCode());
                } else if (orgBank.getIfsc_code() != null && !orgBank.getIfsc_code().trim().isEmpty()) {
                    salaryAccount.setIfscCode(orgBank.getIfsc_code());
                } else {
                    throw new ResourceNotFoundException(
                            "IFSC Code is required (NOT NULL column). Please provide IFSC code either in salary account or ensure it exists in Organization Bank.");
                }
            } else {
                if (bankInfo.getSalaryAccount().getIfscCode() != null
                        && !bankInfo.getSalaryAccount().getIfscCode().trim().isEmpty()) {
                    salaryAccount.setIfscCode(bankInfo.getSalaryAccount().getIfscCode());
                } else {
                    throw new ResourceNotFoundException("IFSC Code is required (NOT NULL column)");
                }
            }
 
            if (bankInfo.getSalaryAccount().getAccountHolderName() != null
                    && !bankInfo.getSalaryAccount().getAccountHolderName().trim().isEmpty()) {
                salaryAccount.setBankHolderName(bankInfo.getSalaryAccount().getAccountHolderName());
            } else {
                String employeeName = employee.getFirst_name() + " " + employee.getLast_name();
                salaryAccount.setBankHolderName(employeeName.trim());
            }
 
            if (bankInfo.getSalaryAccount().getAccountNo() != null) {
                try {
                    Long accNoLong = Long.parseLong(bankInfo.getSalaryAccount().getAccountNo());
                    salaryAccount.setAccNo(accNoLong);
                } catch (NumberFormatException e) {
                    throw new ResourceNotFoundException(
                            "Invalid account number format. Account number must be numeric.");
                }
            } else {
                throw new ResourceNotFoundException("Account number is required (NOT NULL column)");
            }
 
// Set payableAt from DTO if provided
            if (bankInfo.getSalaryAccount() != null && bankInfo.getSalaryAccount().getPayableAt() != null
                    && !bankInfo.getSalaryAccount().getPayableAt().trim().isEmpty()) {
                salaryAccount.setPayableAt(bankInfo.getSalaryAccount().getPayableAt().trim());
            }
            salaryAccount.setIsActive(1);
 
// Set created_by only if provided from frontend, otherwise don't set it (entity default will be used)
            if (createdBy != null && createdBy > 0) {
                salaryAccount.setCreatedBy(createdBy);
                salaryAccount.setCreatedDate(LocalDateTime.now());
            }
// If createdBy is null, don't set created_by - entity default value will be used
 
            bankList.add(salaryAccount);
        }
 
        return bankList;
    }
 
    /**
     * Helper: Update or create Bank entities
     */
    private void updateOrCreateBankEntities(List<BankDetails> newBanks, Employee employee, Integer updatedBy) {
        int empId = employee.getEmp_id();
 
        List<BankDetails> existingBanks = bankDetailsRepository.findByEmpIdAndIsActive(empId, 1);
 
        // Match by account type (PERSONAL/SALARY) instead of index to prevent cross-updates
        for (BankDetails newBank : newBanks) {
            newBank.setEmpId(employee);
            newBank.setIsActive(1);
 
            // Find existing bank by account type (PERSONAL or SALARY)
            BankDetails existing = existingBanks.stream()
                    .filter(b -> b.getAccType() != null && b.getAccType().equals(newBank.getAccType()))
                    .findFirst()
                    .orElse(null);
 
            if (existing != null) {
                // Update existing bank of the same type
                updateBankFields(existing, newBank);
                // Set updated_by and updated_date on update
                if (updatedBy != null && updatedBy > 0) {
                    existing.setUpdatedBy(updatedBy);
                    existing.setUpdatedDate(LocalDateTime.now());
                }
                bankDetailsRepository.save(existing);
            } else {
                // Create new bank account
                bankDetailsRepository.save(newBank);
            }
        }
 
        // Deactivate existing banks that are not in the new list (by account type)
        for (BankDetails existing : existingBanks) {
            boolean foundInNewList = newBanks.stream()
                    .anyMatch(b -> b.getAccType() != null && b.getAccType().equals(existing.getAccType()));
           
            if (!foundInNewList) {
                // This account type is not in the new list, deactivate it
                existing.setIsActive(0);
                if (updatedBy != null && updatedBy > 0) {
                    existing.setUpdatedBy(updatedBy);
                    existing.setUpdatedDate(LocalDateTime.now());
                }
                bankDetailsRepository.save(existing);
            }
        }
    }
 
    /**
     * Helper: Update Bank fields
     */
    private void updateBankFields(BankDetails target, BankDetails source) {
        target.setEmpPaymentType(source.getEmpPaymentType());
        target.setBankHolderName(source.getBankHolderName());
        target.setAccNo(source.getAccNo());
        target.setIfscCode(source.getIfscCode());
        target.setPayableAt(source.getPayableAt());
        target.setBankName(source.getBankName());
        target.setBankBranch(source.getBankBranch());
        target.setAccType(source.getAccType());
        target.setBankStatementChequePath(source.getBankStatementChequePath());
        target.setIsActive(source.getIsActive());
    }
 
// ============================================================================
// HELPER METHODS - Agreement Operations
// ============================================================================
 
    /**
     * Helper: Prepare Agreement Information updates in Employee entity (in memory,
     * no DB writes)
     */
    private void prepareAgreementInfoUpdates(AgreementInfoDTO agreementInfo, Employee employee, Integer updatedBy) {
        if (agreementInfo == null)
            return;
 
// Set agreement information in Employee entity (in memory only)
        if (agreementInfo.getAgreementOrgId() != null) {
            Organization org = organizationRepository.findById(agreementInfo.getAgreementOrgId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agreement Organization not found with ID: " + agreementInfo.getAgreementOrgId()));
            employee.setAgreement_org_id(org);
        }
 
        if (agreementInfo.getAgreementType() != null && !agreementInfo.getAgreementType().trim().isEmpty()) {
            employee.setAgreement_type(agreementInfo.getAgreementType());
        }
 
// Set is_check_submit from frontend (Boolean: true/false)
// Note: is_check_submit is a foreign key to sce_emp_level table
// If true: set to valid emp_level_id (default to 1), if false: set to null
        if (agreementInfo.getIsCheckSubmit() != null) {
            if (Boolean.TRUE.equals(agreementInfo.getIsCheckSubmit())) {
// Checked: Set to valid emp_level_id (default to 1 - first level)
                employee.setIs_check_submit(1); // Default to emp_level_id = 1
                logger.info("Prepared is_check_submit (OIS Check Submit) for employee (emp_id: {}): 1 (checked)",
                        employee.getEmp_id());
            } else {
// Unchecked: Set to null (0 is not a valid foreign key value)
                employee.setIs_check_submit(null);
                logger.info("Prepared is_check_submit (OIS Check Submit) for employee (emp_id: {}): null (unchecked)",
                        employee.getEmp_id());
            }
        }
 
// Set updated_by and updated_date on Employee table ONLY if status is "Confirm"
        if (updatedBy != null && updatedBy > 0 && employee.getEmp_check_list_status_id() != null
                && "Confirm".equals(employee.getEmp_check_list_status_id().getCheck_app_status_name())) {
            employee.setUpdated_by(updatedBy);
            employee.setUpdated_date(LocalDateTime.now());
        }
    }
 
    /**
     * Helper: Save Agreement Cheque Details to database
     */
    private void saveAgreementChequeDetails(AgreementInfoDTO agreementInfo, Employee employee, Integer createdBy,
            Integer updatedBy) {
        if (agreementInfo == null)
            return;
 
// Save cheque details ONLY if isCheckSubmit is true AND cheque details are provided
// If isCheckSubmit = false, cheque details will NOT be saved even if provided
        if (Boolean.TRUE.equals(agreementInfo.getIsCheckSubmit()) && agreementInfo.getChequeDetails() != null
                && !agreementInfo.getChequeDetails().isEmpty()) {
            logger.info("Saving cheque details for employee (emp_id: {}), isCheckSubmit: {}", employee.getEmp_id(),
                    agreementInfo.getIsCheckSubmit());
 
            int empId = employee.getEmp_id();
 
            List<EmpChequeDetails> existingCheques = empChequeDetailsRepository.findAll().stream()
                    .filter(c -> c.getEmpId() != null && c.getEmpId().getEmp_id() == empId && c.getIsActive() == 1)
                    .collect(Collectors.toList());
 
            for (int i = 0; i < agreementInfo.getChequeDetails().size(); i++) {
                AgreementInfoDTO.ChequeDetailDTO chequeDTO = agreementInfo.getChequeDetails().get(i);
                if (chequeDTO == null)
                    continue;
 
                if (i < existingCheques.size()) {
                    EmpChequeDetails existing = existingCheques.get(i);
                    existing.setChequeNo(chequeDTO.getChequeNo());
                    existing.setChequeBankName(chequeDTO.getChequeBankName().trim());
                    existing.setChequeBankIfscCode(chequeDTO.getChequeBankIfscCode().trim());
                    existing.setIsActive(1);
// Set updated_by and updated_date on update
                    if (updatedBy != null && updatedBy > 0) {
                        existing.setUpdatedBy(updatedBy);
                    }
                    existing.setUpdatedDate(LocalDateTime.now());
                    empChequeDetailsRepository.save(existing);
                } else {
                    EmpChequeDetails cheque = new EmpChequeDetails();
                    cheque.setEmpId(employee);
                    cheque.setChequeNo(chequeDTO.getChequeNo());
                    cheque.setChequeBankName(chequeDTO.getChequeBankName().trim());
                    cheque.setChequeBankIfscCode(chequeDTO.getChequeBankIfscCode().trim());
                    cheque.setIsActive(1);
 
// Set created_by only if provided from frontend, otherwise don't set it (entity default will be used)
                    if (createdBy != null && createdBy > 0) {
                        cheque.setCreatedBy(createdBy);
                        cheque.setCreatedDate(LocalDateTime.now());
                    }
// If createdBy is null, don't set created_by - entity default value will be used
 
                    empChequeDetailsRepository.save(cheque);
                }
            }
 
            if (existingCheques.size() > agreementInfo.getChequeDetails().size()) {
                for (int i = agreementInfo.getChequeDetails().size(); i < existingCheques.size(); i++) {
                    existingCheques.get(i).setIsActive(0);
                    if (updatedBy != null && updatedBy > 0) {
                        existingCheques.get(i).setUpdatedBy(updatedBy);
                    }
                    existingCheques.get(i).setUpdatedDate(LocalDateTime.now());
                    empChequeDetailsRepository.save(existingCheques.get(i));
                }
            }
 
            logger.info("✅ Updated/Created {} cheque details for Employee ID: {}",
                    agreementInfo.getChequeDetails().size(), employee.getEmp_id());
        } else {
            int empId = employee.getEmp_id();
 
            List<EmpChequeDetails> existingCheques = empChequeDetailsRepository.findAll().stream()
                    .filter(c -> c.getEmpId() != null && c.getEmpId().getEmp_id() == empId && c.getIsActive() == 1)
                    .collect(Collectors.toList());
 
            for (EmpChequeDetails existing : existingCheques) {
                existing.setIsActive(0);
                if (updatedBy != null && updatedBy > 0) {
                    existing.setUpdatedBy(updatedBy);
                }
                existing.setUpdatedDate(LocalDateTime.now());
                empChequeDetailsRepository.save(existing);
            }
 
            if (!existingCheques.isEmpty()) {
                logger.info("Marked {} existing cheque details as inactive (isCheckSubmit=false) for Employee ID: {}",
                        existingCheques.size(), empId);
            }
        }
    }
 
// ============================================================================
// HELPER METHODS - Validation Operations
// ============================================================================
 
    /**
     * Helper: Validate Qualification DTO
     */
    private void validateQualification(QualificationDTO qualification) {
        if (qualification == null) {
            return; // Qualification is optional
        }
 
        if (qualification.getQualifications() != null) {
// Note: isHighest flag validation removed - qualification_id is now set from BasicInfoDTO.qualificationId
 
            for (QualificationDTO.QualificationDetailsDTO qual : qualification.getQualifications()) {
                if (qual == null)
                    continue;
 
                // Skip certificate validation if path is null, empty, or "string"
                if (qual.getCertificateFile() != null && !qual.getCertificateFile().trim().isEmpty()
                        && !"string".equalsIgnoreCase(qual.getCertificateFile().trim())) {
                    if (qual.getQualificationId() == null) {
                        throw new ResourceNotFoundException("Qualification ID is required when providing a certificate");
                    }
                    empDocTypeRepository.findById(qual.getQualificationId()).orElseThrow(
                            () -> new ResourceNotFoundException("Document type not found for qualification ID: " + qual.getQualificationId()));
                }
 
                if (qual.getQualificationId() != null) {
                    qualificationRepository.findById(qual.getQualificationId())
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Qualification not found with ID: " + qual.getQualificationId()));
                }
                if (qual.getQualificationDegreeId() != null) {
                    qualificationDegreeRepository.findById(qual.getQualificationDegreeId())
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Qualification Degree not found with ID: " + qual.getQualificationDegreeId()));
                }
            }
        }
    }
 
    /**
     * Helper: Validate Documents DTO
     */
    private void validateDocuments(DocumentDTO documents) {
        if (documents == null) {
            return; // Documents are optional
        }
 
        if (documents.getDocuments() != null) {
            for (DocumentDTO.DocumentDetailsDTO doc : documents.getDocuments()) {
                if (doc == null)
                    continue;
 
                // Skip validation if path is null, empty, or "string" (Swagger default)
                if (doc.getDocPath() == null || doc.getDocPath().trim().isEmpty()
                        || "string".equalsIgnoreCase(doc.getDocPath().trim())) {
                    continue;
                }
 
                if (doc.getDocTypeId() == null) {
                    throw new ResourceNotFoundException("Document Type ID is required for document");
                }
                empDocTypeRepository.findById(doc.getDocTypeId()).orElseThrow(
                        () -> new ResourceNotFoundException("Document Type not found with ID: " + doc.getDocTypeId()));
            }
        }
    }
 
    /**
     * Helper: Validate Category Info DTO
     */
    private void validateCategoryInfo(CategoryInfoDTO categoryInfo) {
        if (categoryInfo == null) {
            return; // Category info is optional
        }
 
        if (categoryInfo.getEmployeeTypeId() != null) {
            employeeTypeRepository.findById(categoryInfo.getEmployeeTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Employee Type not found with ID: " + categoryInfo.getEmployeeTypeId()));
        }
        if (categoryInfo.getDepartmentId() != null) {
            departmentRepository.findById(categoryInfo.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Department not found with ID: " + categoryInfo.getDepartmentId()));
        }
        if (categoryInfo.getDesignationId() != null) {
            designationRepository.findById(categoryInfo.getDesignationId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Designation not found with ID: " + categoryInfo.getDesignationId()));
        }
        if (categoryInfo.getSubjectId() != null && categoryInfo.getSubjectId() > 0) {
            subjectRepository.findById(categoryInfo.getSubjectId()).orElseThrow(
                    () -> new ResourceNotFoundException("Subject not found with ID: " + categoryInfo.getSubjectId()));
        }
    }
 
    /**
     * Helper: Validate Bank Info DTO
     */
    private void validateBankInfo(BankInfoDTO bankInfo) {
        if (bankInfo == null) {
            return; // Bank info is optional
        }
 
        if (bankInfo.getPaymentTypeId() != null && bankInfo.getPaymentTypeId() > 0) {
            empPaymentTypeRepository.findByIdAndIsActive(bankInfo.getPaymentTypeId(), 1)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Active Payment Type not found with ID: " + bankInfo.getPaymentTypeId()));
        }
 
        if (bankInfo.getPersonalAccount() != null) {
            if (bankInfo.getPersonalAccount().getAccountNo() == null
                    || bankInfo.getPersonalAccount().getAccountNo().trim().isEmpty()) {
                throw new ResourceNotFoundException("Personal Account Number is required");
            }
            try {
                Long.parseLong(bankInfo.getPersonalAccount().getAccountNo());
            } catch (NumberFormatException e) {
                throw new ResourceNotFoundException("Personal Account Number must be numeric");
            }
            if (bankInfo.getPersonalAccount().getIfscCode() == null
                    || bankInfo.getPersonalAccount().getIfscCode().trim().isEmpty()) {
                throw new ResourceNotFoundException("Personal Account IFSC Code is required");
            }
            if (bankInfo.getPersonalAccount().getAccountHolderName() == null
                    || bankInfo.getPersonalAccount().getAccountHolderName().trim().isEmpty()) {
                throw new ResourceNotFoundException("Personal Account Holder Name is required");
            }
        }
 
// Only validate salary account if it has actual data (at least accountNo is provided)
// If salaryAccount object exists but is empty, treat it as not provided
        if (bankInfo.getSalaryAccount() != null && bankInfo.getSalaryAccount().getAccountNo() != null
                && !bankInfo.getSalaryAccount().getAccountNo().trim().isEmpty()) {
// Salary account has data, validate all required fields
            if (bankInfo.getBankId() != null && bankInfo.getBankId() > 0) {
                orgBankRepository.findById(bankInfo.getBankId()).orElseThrow(() -> new ResourceNotFoundException(
                        "Organization Bank not found with ID: " + bankInfo.getBankId()));
            }
// Validate bank branch: either ID or name can be provided, but not both
            if (bankInfo.getBankBranchId() != null && bankInfo.getBankBranchId() > 0
                    && bankInfo.getBankBranchName() != null && !bankInfo.getBankBranchName().trim().isEmpty()) {
                throw new ResourceNotFoundException("Please provide either bankBranchId OR bankBranchName, not both.");
            }
            if (bankInfo.getBankBranchId() != null && bankInfo.getBankBranchId() > 0) {
                orgBankBranchRepository.findById(bankInfo.getBankBranchId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Organization Bank Branch not found with ID: " + bankInfo.getBankBranchId()));
            }
// If bankBranchName is provided, no validation needed - it will be stored directly
            if (bankInfo.getSalaryAccount().getAccountNo() == null
                    || bankInfo.getSalaryAccount().getAccountNo().trim().isEmpty()) {
                throw new ResourceNotFoundException("Salary Account Number is required");
            }
            try {
                Long.parseLong(bankInfo.getSalaryAccount().getAccountNo());
            } catch (NumberFormatException e) {
                throw new ResourceNotFoundException("Salary Account Number must be numeric");
            }
            if (bankInfo.getSalaryAccount().getIfscCode() == null
                    || bankInfo.getSalaryAccount().getIfscCode().trim().isEmpty()) {
                throw new ResourceNotFoundException("Salary Account IFSC Code is required");
            }
            if (bankInfo.getSalaryAccount().getAccountHolderName() == null
                    || bankInfo.getSalaryAccount().getAccountHolderName().trim().isEmpty()) {
                throw new ResourceNotFoundException("Salary Account Holder Name is required");
            }
        }
    }
 
    /**
     * Helper: Validate Agreement Info DTO
     */
    private void validateAgreementInfo(AgreementInfoDTO agreementInfo) {
        if (agreementInfo == null) {
            return; // Agreement info is optional
        }
 
// If isCheckSubmit is true, cheque details MUST be provided
        if (Boolean.TRUE.equals(agreementInfo.getIsCheckSubmit())) {
            if (agreementInfo.getChequeDetails() == null || agreementInfo.getChequeDetails().isEmpty()) {
                throw new ResourceNotFoundException(
                        "Cheque details are required when isCheckSubmit is true. Please provide at least one cheque detail.");
            }
        }
 
// Validate cheque details if isCheckSubmit is true and cheque details are provided
        if (Boolean.TRUE.equals(agreementInfo.getIsCheckSubmit()) && agreementInfo.getChequeDetails() != null
                && !agreementInfo.getChequeDetails().isEmpty()) {
 
            for (AgreementInfoDTO.ChequeDetailDTO chequeDTO : agreementInfo.getChequeDetails()) {
                if (chequeDTO == null)
                    continue;
 
                if (chequeDTO.getChequeNo() == null) {
                    throw new ResourceNotFoundException("Cheque Number is required (NOT NULL column)");
                }
 
                if (chequeDTO.getChequeBankName() == null || chequeDTO.getChequeBankName().trim().isEmpty()) {
                    throw new ResourceNotFoundException("Cheque Bank Name is required (NOT NULL column)");
                }
 
                if (chequeDTO.getChequeBankIfscCode() == null || chequeDTO.getChequeBankIfscCode().trim().isEmpty()) {
                    throw new ResourceNotFoundException("Cheque Bank IFSC Code is required (NOT NULL column)");
                }
            }
        }
    }
 
}
 
 
 
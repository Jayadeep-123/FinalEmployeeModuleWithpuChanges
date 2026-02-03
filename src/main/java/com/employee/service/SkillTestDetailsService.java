package com.employee.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.employee.dto.SkillTestDetailsDto;

import com.employee.dto.SkillTestDetailsRequestDto;
import com.employee.dto.SkillTestDetailsResultDto;
import com.employee.dto.SkillTestResultDTO;
import com.employee.entity.Building;
import com.employee.entity.Campus;
import com.employee.entity.City;
import com.employee.entity.Employee;
import com.employee.entity.EmployeeLevel;
import com.employee.entity.EmployeeType;
import com.employee.entity.Gender;
import com.employee.entity.Grade;
import com.employee.entity.JoiningAs;
import com.employee.entity.Qualification;

import com.employee.entity.SkillTestDetails;
import com.employee.entity.SkillTestResult;
import com.employee.entity.Stream;
import com.employee.entity.Structure;
import com.employee.entity.Subject;
import com.employee.exception.ResourceNotFoundException;
import com.employee.repository.BuildingRepository;
import com.employee.repository.CampusRepository;
import com.employee.repository.CityRepository;
import com.employee.repository.EmployeeLevelRepository;
import com.employee.repository.EmployeeRepository;
import com.employee.repository.EmployeeTypeRepository;
import com.employee.repository.GenderRepository;
import com.employee.repository.GradeRepository;
import com.employee.repository.JoiningAsRepository;
import com.employee.repository.QualificationRepository;
import com.employee.repository.SkillTestApprovalStatusRepository;
import com.employee.repository.SkillTestDetailsRepository;
import com.employee.repository.SkillTestResultRepository;
import com.employee.repository.StreamRepository;
import com.employee.repository.StructureRepository;
import com.employee.repository.SubjectRepository;
import com.employee.repository.SkillTestApprovalRepository;
import com.employee.entity.SkillTestApproval;

import com.employee.entity.OrientationGroup;
import com.employee.repository.OrientationGroupRepository;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SkillTestDetailsService {

    @Autowired
    private SkillTestDetailsRepository skillTestDetailsRepository;
    @Autowired
    CampusRepository campusrepository;

    // ... (All other autowired repositories remain the same) ...
    @Autowired
    private GenderRepository genderRepository;
    @Autowired
    private QualificationRepository qualificationRepository;
    @Autowired
    private JoiningAsRepository joiningAsRepository;
    @Autowired
    private StreamRepository streamRepository;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private EmployeeLevelRepository employeeLevelRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    GradeRepository graderepository;
    @Autowired
    StructureRepository structurerepository;
    @Autowired
    private EmployeeTypeRepository employeeTypeRepository;
    @Autowired
    SkillTestResultRepository skilltestresultrepository;
    @Autowired
    private CityRepository cityRepository;
    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private SkillTestApprovalStatusRepository skillTestApprovalStatusRepository;
    @Autowired
    private SkillTestApprovalRepository skillTestApprovalRepository;
    @Autowired
    private OrientationGroupRepository orientationGroupRepository;

    private Map<String, AtomicInteger> campusCounters = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeCounters() {
        // ... (Counter logic remains exactly the same as before) ...
        log.info("Initializing campus ID counters...");
        List<Campus> allCampuses = campusrepository.findAllWithCodeNotNull();
        for (Campus campus : allCampuses) {
            String baseKey = "TEMP" + campus.getCode();
            int lastValue = 0;
            try {
                String lastIdInSkillTest = skillTestDetailsRepository.findMaxTempPayrollIdByKey(baseKey + "%");
                String lastIdInEmployee = employeeRepository.findMaxTempPayrollIdByKey(baseKey + "%");
                if (lastIdInSkillTest != null) {
                    try {
                        int val = Integer.parseInt(lastIdInSkillTest.substring(baseKey.length()));
                        lastValue = Math.max(lastValue, val);
                    } catch (Exception e) {
                    }
                }
                if (lastIdInEmployee != null) {
                    try {
                        int val = Integer.parseInt(lastIdInEmployee.substring(baseKey.length()));
                        lastValue = Math.max(lastValue, val);
                    } catch (Exception e) {
                    }
                }
            } catch (Exception e) {
                log.error("Error for key {}: {}", baseKey, e.getMessage());
            }
            campusCounters.put(baseKey, new AtomicInteger(lastValue));
        }
    }

    // ======================================
    // SAVE METHOD - UPDATED RETURN TYPE
    // ======================================
    @Transactional
    public SkillTestDetailsDto saveSkillTestDetails(SkillTestDetailsRequestDto requestDto, int emp_id) { // Returns
                                                                                                         // Response DTO

        if (emp_id <= 0) {
            throw new IllegalArgumentException("Employee ID must be > 0");
        }

        // Validate createdBy is provided and valid (must be done early)
        if (requestDto.getCreatedBy() == null || requestDto.getCreatedBy() <= 0) {
            throw new ResourceNotFoundException(
                    "createdBy is required and must be greater than 0. Please provide the user ID who is creating this record.");
        }

        Employee employee = employeeRepository.findById(emp_id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        Campus campus = employee.getCampus_id();
        if (campus == null) {
            throw new ResourceNotFoundException("Employee has no campus assigned");
        }

        // === Generate TempPayrollId ===
        String baseKey = "TEMP" + campus.getCode();

        // ... (Logic to calculate maxValue remains the same) ...
        String max1 = skillTestDetailsRepository.findMaxTempPayrollIdByKey(baseKey + "%");
        String max2 = employeeRepository.findMaxTempPayrollIdByKey(baseKey + "%");
        int maxValue = 0;
        try {
            if (max1 != null)
                maxValue = Math.max(maxValue, Integer.parseInt(max1.substring(baseKey.length())));
        } catch (Exception e) {
        }
        try {
            if (max2 != null)
                maxValue = Math.max(maxValue, Integer.parseInt(max2.substring(baseKey.length())));
        } catch (Exception e) {
        }

        int nextValue = maxValue + 1;
        String generatedTempPayrollId = baseKey + String.format("%04d", nextValue);
        campusCounters.computeIfAbsent(baseKey, k -> new AtomicInteger(0)).set(nextValue);

        // === Aadhaar Validation ===
        if (requestDto.getAadhaarNo() != null && requestDto.getAadhaarNo() > 0) {
            String aadhaar = String.valueOf(requestDto.getAadhaarNo());
            if (!aadhaar.matches("^[0-9]{12}$"))
                throw new ResourceNotFoundException("Aadhaar must be exactly 12 digits");
            if (!isValidAadhaar(aadhaar))
                throw new ResourceNotFoundException("Invalid Aadhaar (Verhoeff failed)");
        }

        // === Fetch Relations ===
        Gender gender = null;
        if (requestDto.getGenderId() != null && requestDto.getGenderId() > 0)
            gender = genderRepository.findById(requestDto.getGenderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Gender not found"));

        Qualification qualification = null;
        if (requestDto.getQualificationId() != null && requestDto.getQualificationId() > 0)
            qualification = qualificationRepository.findById(requestDto.getQualificationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Qualification not found"));

        JoiningAs joiningAs = null;
        if (requestDto.getJoiningAsId() != null && requestDto.getJoiningAsId() > 0)
            joiningAs = joiningAsRepository.findById(requestDto.getJoiningAsId())
                    .orElseThrow(() -> new ResourceNotFoundException("JoiningAs not found"));

        Stream stream = null;
        if (requestDto.getStreamId() != null && requestDto.getStreamId() > 0)
            stream = streamRepository.findById(requestDto.getStreamId())
                    .orElseThrow(() -> new ResourceNotFoundException("Stream not found"));

        Subject subject = null;
        if (requestDto.getSubjectId() != null && requestDto.getSubjectId() > 0)
            subject = subjectRepository.findById(requestDto.getSubjectId())
                    .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));

        EmployeeLevel employeeLevel = null;
        if (requestDto.getEmp_level_id() != null && requestDto.getEmp_level_id() > 0)
            employeeLevel = employeeLevelRepository.findById(requestDto.getEmp_level_id())
                    .orElseThrow(() -> new ResourceNotFoundException("EmployeeLevel not found"));

        Grade grade = null;
        if (requestDto.getEmp_grade_id() != null && requestDto.getEmp_grade_id() > 0)
            grade = graderepository.findById(requestDto.getEmp_grade_id())
                    .orElseThrow(() -> new ResourceNotFoundException("Grade not found"));

        Structure structure = null;
        if (requestDto.getEmp_structure_id() != null && requestDto.getEmp_structure_id() > 0)
            structure = structurerepository.findById(requestDto.getEmp_structure_id())
                    .orElseThrow(() -> new ResourceNotFoundException("Structure not found"));

        EmployeeType employeeType = null;
        if (requestDto.getEmpTypeId() != null && requestDto.getEmpTypeId() > 0)
            employeeType = employeeTypeRepository.findById(requestDto.getEmpTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("EmployeeType not found"));

        OrientationGroup group = null;
        if (requestDto.getGroupId() != null && requestDto.getGroupId() > 0) {
            group = orientationGroupRepository.findById(requestDto.getGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Orientation Group not found"));
        }

        Campus detailCampus = null;
        if (requestDto.getCmpsId() != null && requestDto.getCmpsId() > 0) {
            detailCampus = campusrepository.findById(requestDto.getCmpsId())
                    .orElseThrow(() -> new ResourceNotFoundException("Campus not found for provided ID"));
        }

        City city = null;
        if (requestDto.getCityId() != null && requestDto.getCityId() > 0) {
            city = cityRepository.findById(requestDto.getCityId())
                    .orElseThrow(() -> new ResourceNotFoundException("City not found"));
        }

        Building building = null;
        if (requestDto.getBuildingId() != null && requestDto.getBuildingId() > 0) {
            building = buildingRepository.findById(requestDto.getBuildingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Building not found"));
        }

        // === Create Entity ===
        SkillTestDetails newDetails = new SkillTestDetails();

        newDetails.setAadhaar_no(requestDto.getAadhaarNo());
        newDetails.setPrevious_chaitanya_id(requestDto.getPreviousChaitanyaId());
        newDetails.setFirstName(requestDto.getFirstName());
        newDetails.setLastName(requestDto.getLastName());
        newDetails.setDob(requestDto.getDob());
        newDetails.setEmail(requestDto.getEmail());
        newDetails.setTotalExperience(requestDto.getTotalExperience());
        newDetails.setContact_number(requestDto.getContactNumber());
        newDetails.setTempPayrollId(generatedTempPayrollId);

        // === Password Logic ===
        String firstName = requestDto.getFirstName();
        String namePart = (firstName == null || firstName.length() < 3) ? "emp" : firstName.substring(0, 3);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
        String dobPart = requestDto.getDob().format(formatter);
        newDetails.setPassword(namePart + dobPart);

        // === Set Relations ===
        newDetails.setGender(gender);
        newDetails.setQualification(qualification);
        newDetails.setJoiningAs(joiningAs);
        newDetails.setStream(stream);
        newDetails.setSubject(subject);
        newDetails.setEmployeeLevel(employeeLevel);
        newDetails.setEmpGrade(grade);
        newDetails.setEmpStructure(structure);
        newDetails.setEmployeeType(employeeType);

        newDetails.setOrientationGroup(group);
        newDetails.setCampus(detailCampus);
        newDetails.setCity(city);
        newDetails.setBuilding(building);

        // === Audit Fields ===
        // Set created_by from user input (Request DTO) - validation already done at
        // method start
        newDetails.setCreatedBy(requestDto.getCreatedBy());
        newDetails.setCreatedDate(LocalDateTime.now());
        newDetails.setIsActive(1);

        // === SAVE THE ENTITY ===
        SkillTestDetails savedDetails = skillTestDetailsRepository.save(newDetails);

        // === CONVERT ENTITY TO DTO AND RETURN ===
        return convertToDto(savedDetails);
    }

    /**
     * Helper method to convert SkillTestDetails entity to DTO
     * This avoids serialization issues with Hibernate lazy loading
     */
    private SkillTestDetailsDto convertToDto(SkillTestDetails entity) {
        SkillTestDetailsDto dto = new SkillTestDetailsDto();

        // Set response fields
        dto.setSkillTestDetlId(entity.getSkillTestDetlId());
        dto.setTempPayrollId(entity.getTempPayrollId());
        dto.setCreatedDate(entity.getCreatedDate());

        // Set personal info
        dto.setAadhaarNo(entity.getAadhaar_no());
        dto.setPreviousChaitanyaId(entity.getPrevious_chaitanya_id());
        dto.setFirstName(entity.getFirstName());
        dto.setLastName(entity.getLastName());
        dto.setDob(entity.getDob());
        dto.setContactNumber(entity.getContact_number());
        dto.setEmail(entity.getEmail());
        dto.setTotalExperience(entity.getTotalExperience());

        // Set foreign key IDs and Names from relationships
        if (entity.getGender() != null) {
            dto.setGenderId(entity.getGender().getGender_id());
            dto.setGenderName(entity.getGender().getGenderName());
        }
        if (entity.getQualification() != null) {
            dto.setQualificationId(entity.getQualification().getQualification_id());
            dto.setQualificationName(entity.getQualification().getQualification_name());
        }
        if (entity.getJoiningAs() != null) {
            dto.setJoiningAsId(entity.getJoiningAs().getJoin_type_id());
            dto.setJoiningAsName(entity.getJoiningAs().getJoin_type());
        }
        if (entity.getStream() != null) {
            dto.setStreamId(entity.getStream().getStreamId());
            dto.setStreamName(entity.getStream().getStreamName());
        }
        if (entity.getSubject() != null) {
            dto.setSubjectId(entity.getSubject().getSubject_id());
            dto.setSubjectName(entity.getSubject().getSubject_name());
        }
        if (entity.getEmployeeLevel() != null) {
            dto.setEmp_level_id(entity.getEmployeeLevel().getEmp_level_id());
            dto.setEmpLevelName(entity.getEmployeeLevel().getLevel_name());
        }
        if (entity.getEmpGrade() != null) {
            dto.setEmp_grade_id(entity.getEmpGrade().getEmpGradeId());
            dto.setEmpGradeName(entity.getEmpGrade().getGradeName());
        }
        if (entity.getEmpStructure() != null) {
            dto.setEmp_structure_id(entity.getEmpStructure().getEmpStructureId());
            dto.setEmpStructureName(entity.getEmpStructure().getStructureName());
        }
        if (entity.getEmployeeType() != null) {
            dto.setEmpTypeId(entity.getEmployeeType().getEmp_type_id());
            dto.setEmpTypeName(entity.getEmployeeType().getEmp_type());
        }
        if (entity.getOrientationGroup() != null) {
            dto.setGroupId(entity.getOrientationGroup().getGroupId());
        }
        if (entity.getCampus() != null) {
            dto.setCampusId(entity.getCampus().getCampusId());
        }
        if (entity.getCity() != null) {
            dto.setCityId(entity.getCity().getCityId());
        }
        if (entity.getBuilding() != null) {
            dto.setBuildingId(entity.getBuilding().getBuildingId());
        }

        // Set audit fields
        dto.setCreatedBy(entity.getCreatedBy());

        return dto;
    }

    /**
     * Get skill test details by tempPayrollId
     *
     * @param tempPayrollId The temporary payroll ID
     * @return SkillTestDetailsDto containing all skill test details
     * @throws ResourceNotFoundException if skill test details not found
     */
    @Transactional(readOnly = true)
    public SkillTestDetailsDto getSkillTestDetailsByTempPayrollId(String tempPayrollId) {
        if (tempPayrollId == null || tempPayrollId.trim().isEmpty()) {
            throw new ResourceNotFoundException("tempPayrollId is required");
        }

        SkillTestDetails skillTestDetails = skillTestDetailsRepository
                .findActiveByTempPayrollId(tempPayrollId.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Skill test details not found for tempPayrollId: " + tempPayrollId));

        return convertToDto(skillTestDetails);
    }

    // ... (isValidAadhaar remains the same) ...
    private boolean isValidAadhaar(String aadhaar) {
        // ... Verhoeff logic ...
        int[][] D = { { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 }, { 1, 2, 3, 4, 0, 6, 7, 8, 9, 5 },
                { 2, 3, 4, 0, 1, 7, 8, 9, 5, 6 }, { 3, 4, 0, 1, 2, 8, 9, 5, 6, 7 }, { 4, 0, 1, 2, 3, 9, 5, 6, 7, 8 },
                { 5, 9, 8, 7, 6, 0, 4, 3, 2, 1 }, { 6, 5, 9, 8, 7, 1, 0, 4, 3, 2 }, { 7, 6, 5, 9, 8, 2, 1, 0, 4, 3 },
                { 8, 7, 6, 5, 9, 3, 2, 1, 0, 4 }, { 9, 8, 7, 6, 5, 4, 3, 2, 1, 0 } };
        int[][] P = { { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 }, { 1, 5, 7, 6, 2, 8, 3, 0, 9, 4 },
                { 5, 8, 0, 3, 7, 9, 6, 1, 4, 2 }, { 8, 9, 1, 6, 0, 4, 3, 5, 2, 7 }, { 9, 4, 5, 3, 1, 2, 6, 8, 7, 0 },
                { 4, 2, 8, 6, 5, 7, 3, 9, 0, 1 }, { 2, 7, 9, 3, 8, 0, 6, 4, 1, 5 }, { 7, 0, 4, 6, 9, 1, 3, 2, 5, 8 } };
        int checksum = 0;
        for (int i = 0; i < aadhaar.length(); i++) {
            int digit = aadhaar.charAt(aadhaar.length() - 1 - i) - '0';
            checksum = D[checksum][P[i % 8][digit]];
        }
        return checksum == 0;
    }

    public List<SkillTestDetailsResultDto> get_details_of_passed_employees() {

        // 1. Get all results
        List<SkillTestResult> testResults = skilltestresultrepository.findTestResultsWithIds();

        // Safety check for null list from Repo
        if (testResults == null || testResults.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. Filter & Map directly from SkillTestResult
        return testResults.stream()
                .filter(result -> result.getSkillTestApprovalStatus() != null &&
                        result.getSkillTestApprovalStatus().getStatusName() != null &&
                        result.getSkillTestApprovalStatus().getStatusName().trim().toLowerCase()
                                .startsWith("skill test approv"))
                .filter(result -> result.getSkillTestDetlId() != null) // Prevent NullPointer
                .map(result -> {
                    SkillTestDetailsResultDto dto = new SkillTestDetailsResultDto();
                    SkillTestDetails entity = result.getSkillTestDetlId();

                    // FIX 1: Added Space in Name
                    String fName = entity.getFirstName() != null ? entity.getFirstName() : "";
                    String lName = entity.getLastName() != null ? entity.getLastName() : "";
                    dto.setEmployeeName((fName + " " + lName).trim());

                    dto.setTempPayrollId(entity.getTempPayrollId());
                    dto.setJoinDate(entity.getJoinDate());

                    // FIX 2: Ensure getGenderName() exists in your Gender Entity!
                    if (entity.getCity() != null) {
                        dto.setCity(entity.getCity().getCityName());
                    } else {
                        dto.setCity("N/A");
                    }

                    if (entity.getCampus() != null) {
                        dto.setCampus(entity.getCampus().getCampusName());
                    } else {
                        dto.setCampus("N/A");
                    }

                    // Populate Employee Number (from Employee entity if available, else
                    // previous_chaitanya_id as fallback)
                    if (entity.getEmpId() != null && entity.getEmpId().getPayRollId() != null) {
                        dto.setEmployeeNumber(entity.getEmpId().getPayRollId());
                    } else {
                        dto.setEmployeeNumber(
                                entity.getPrevious_chaitanya_id() != null ? entity.getPrevious_chaitanya_id() : "N/A");
                    }

                    // Populate Status FROM SKILL TEST RESULT, NOT SKILL TEST DETAILS
                    if (result.getSkillTestApprovalStatus() != null) {
                        dto.setStatus(result.getSkillTestApprovalStatus().getStatusName());
                    } else {
                        dto.setStatus("N/A");
                    }

                    dto.setContactNumber(entity.getContact_number());
                    dto.setTotalExperience(entity.getTotalExperience());

                    if (entity.getGender() != null) {
                        dto.setGender(entity.getGender().getGenderName());
                    } else {
                        dto.setGender("N/A");
                    }

                    return dto;
                }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SkillTestApproval getApprovalDetailsByTempPayrollId(String tempPayrollId) {
        return skillTestApprovalRepository.findByTempEmployeeId(tempPayrollId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Approval details not found for tempPayrollId: " + tempPayrollId));
    }

    @Transactional(readOnly = true)
    public List<SkillTestResultDTO> getSkillTestResultsByPayrollId(String tempPayrollId) {
        if (tempPayrollId == null || tempPayrollId.trim().isEmpty()) {
            throw new ResourceNotFoundException("tempPayrollId is required");
        }
        log.info("Fetching skill test results for tempPayrollId: {}", tempPayrollId);
        return skilltestresultrepository.findSkillTestDetailsByPayrollId(tempPayrollId.trim());
    }

    /**
     * Get list of all skill test details with specific fields
     */
    @Transactional(readOnly = true)
    public List<com.employee.dto.SkillTestListDto> getSkillTestList() {
        // Use repository method to get active records directly from DB
        return skillTestDetailsRepository.findByIsActive(1).stream()
                .map(entity -> {
                    com.employee.dto.SkillTestListDto dto = new com.employee.dto.SkillTestListDto();

                    // 1. Employee Name
                    String fName = entity.getFirstName() != null ? entity.getFirstName() : "";
                    String lName = entity.getLastName() != null ? entity.getLastName() : "";
                    dto.setEmployeeName((fName + " " + lName).trim());

                    // 2. Temp Payroll ID
                    dto.setTempPayrollId(entity.getTempPayrollId());

                    // 3. Employee Number
                    // Try to get from Employee entity first, else fallback to previous_chaitanya_id
                    if (entity.getEmpId() != null && entity.getEmpId().getPayRollId() != null) {
                        dto.setEmployeeNumber(entity.getEmpId().getPayRollId());
                    } else {
                        dto.setEmployeeNumber(
                                entity.getPrevious_chaitanya_id() != null ? entity.getPrevious_chaitanya_id() : "N/A");
                    }

                    // 4. Join Date (Mapping CreatedDate as fallback, or JoinDate if available in
                    // fields)
                    // Requirement said "join date". Entity has createdDate. Employee entity has
                    // join_date.
                    // SkillTestDetails entity doesn't seem to have a specific 'join_date' field in
                    // the class,
                    // but let's check if it exists or use createdDate as proxy for when this record
                    // was made.
                    // CHECK: The DTO we made has LocalDateTime.
                    dto.setJoinDate(entity.getCreatedDate());

                    // 5. City
                    if (entity.getCity() != null) {
                        dto.setCity(entity.getCity().getCityName());
                    } else {
                        dto.setCity("N/A");
                    }

                    // 6. Campus
                    if (entity.getCampus() != null) {
                        dto.setCampus(entity.getCampus().getCampusName());
                    } else {
                        dto.setCampus("N/A");
                    }

                    // 7. Gender
                    if (entity.getGender() != null) {
                        dto.setGender(entity.getGender().getGenderName());
                    } else {
                        dto.setGender("N/A");
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }
}
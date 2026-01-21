package com.employee.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.employee.controller.ExamResultDTO;
import com.employee.dto.EmpExamDataDTO;
import com.employee.entity.SkillTestDetails;
import com.employee.repository.SkillTestDetailsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class EmpExamIntegrationService {

    // --- API CONFIGURATION ---
    private final String API_BASE_URL = "https://webservice.scaits.net/empExamService";
    // Modified URL as per new requirement
    private final String SAVE_EMP_URL = "https://testreportsapi.scaits.net/scaits/saveEmpBioDetails";
    private final String FETCH_RESULT_URL = "https://testreportsapi.scaits.net/scaits/getEmpTestResult";

    // Credentials from Email
    // private final String API_USERNAME = "QBemp$est";
    // private final String API_PASSWORD = "ScaitsQB";

    private final String API_USERNAME = "ScaitsQB";
    private final String API_PASSWORD = "QBemp$est";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SkillTestDetailsRepository skillTestRepository;
    private final com.employee.repository.SkillTestResultRepository skillTestResultRepository;

    public EmpExamIntegrationService(RestTemplate restTemplate,
            ObjectMapper objectMapper,
            SkillTestDetailsRepository skillTestRepository,
            com.employee.repository.SkillTestResultRepository skillTestResultRepository) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.skillTestRepository = skillTestRepository;
        this.skillTestResultRepository = skillTestResultRepository;
    }

    // ==================================================================================
    // 1. SYNC METHOD (Fetch from DB -> Map to DTO -> POST to External API)
    // ==================================================================================
    public String syncEmployeeFromDb(String tempPayrollId) {
        // 1. Fetch using the custom String finder
        SkillTestDetails entity = skillTestRepository.findByTempPayrollId(tempPayrollId)
                .orElseThrow(() -> new RuntimeException("Employee not found with Payroll ID: " + tempPayrollId));

        // 2. Map Entity -> DTO
        EmpExamDataDTO dto = mapEntityToDto(entity);

        // 3. Send to External API
        return pushEmployeeData(entity.getTempPayrollId(), dto);
    }

    // ==================================================================================
    // 2. PREVIEW METHOD (The GET method you requested to check data locally)
    // ==================================================================================
    public EmpExamDataDTO previewEmployeeData(String tempPayrollId) {
        // 1. Fetch using the custom String finder
        SkillTestDetails entity = skillTestRepository.findByTempPayrollId(tempPayrollId)
                .orElseThrow(() -> new RuntimeException("Employee not found with Payroll ID: " + tempPayrollId));

        // 2. Map & Return
        return mapEntityToDto(entity);
    }

    // ==================================================================================
    // 3. FETCH RESULT METHOD (Pull Exam Results from External API & SAVE to DB)
    // ==================================================================================
    public ExamResultDTO fetchExamResult(String empId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Assuming Fetch also needs Auth now? If not, removing this line won't hurt if
            // headers are empty
            // headers.setBasicAuth(API_USERNAME, API_PASSWORD);

            HttpEntity<String> requestEntity = new HttpEntity<>(headers);
            // new param is 'empPayrollId'
            String finalUrl = FETCH_RESULT_URL + "?empPayrollId=" + empId;

            System.out.println("Fetching Results from: " + finalUrl);

            ResponseEntity<ExamResultDTO> response = restTemplate.exchange(
                    finalUrl,
                    HttpMethod.GET,
                    requestEntity,
                    ExamResultDTO.class);

            ExamResultDTO dto = response.getBody();

            // --- SAVE LOGIC ---
            if (dto != null) {
                saveExamResult(dto);
            }

            return dto;

        } catch (Exception e) {
            System.err.println("Error Fetching Exam Result: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void saveExamResult(ExamResultDTO dto) {
        try {
            // 1. Find Employee locally
            SkillTestDetails employee = skillTestRepository.findByTempPayrollId(dto.getPayrollId())
                    .orElseThrow(
                            () -> new RuntimeException("Employee not found for Result Sync: " + dto.getPayrollId()));

            // 2. Map DTO -> Entity
            com.employee.entity.SkillTestResult resultEntity = new com.employee.entity.SkillTestResult();

            resultEntity.setSkillTestDetlId(employee);
            resultEntity.setEmpName(employee.getFirstName() + " " + employee.getLastName());

            // --- INACTIVATION LOGIC ---
            // Find existing ACTIVE results for this employee
            java.util.List<com.employee.entity.SkillTestResult> existingActive = skillTestResultRepository
                    .findBySkillTestDetlIdAndIsActive(employee, 1);

            if (existingActive != null && !existingActive.isEmpty()) {
                for (com.employee.entity.SkillTestResult oldResult : existingActive) {
                    oldResult.setIsActive(0); // Deactivate
                }
                skillTestResultRepository.saveAll(existingActive);
                System.out.println("Deactivated " + existingActive.size() + " old results for: " + dto.getPayrollId());
            }
            // --------------------------

            // Parse Date (dd-MM-yyyy -> SQL Date)
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd-MM-yyyy");
                java.util.Date parsed = sdf.parse(dto.getExamDate());
                resultEntity.setExamDate(new java.sql.Date(parsed.getTime()));
            } catch (Exception e) {
                System.err.println("Date Parse Error: " + e.getMessage());
                // Fallback to current date or handle accordingly
                resultEntity.setExamDate(new java.sql.Date(System.currentTimeMillis()));
            }

            // Parse Numbers (Handle Strings "16.0", "30", etc.)
            resultEntity.setNoOfQuestion(parseInteger(dto.getTotalQuestions()));
            resultEntity.setNoOfQuesAttempt(parseInteger(dto.getAttempted()));
            resultEntity.setNoOfQuesUnattempt(parseInteger(dto.getUnAttempted()));
            resultEntity.setNoOfQuesCorrect(parseInteger(dto.getCorrect()));
            resultEntity.setNoOfQuesWrong(parseInteger(dto.getWrong()));

            // Total Marks (Stored as int in DB, but might come as "16.0" from API)
            resultEntity.setTotalMarks((int) parseDouble(dto.getTotalMarks()));

            // Hardcoded / Default fields
            resultEntity.setIsActive(1);
            resultEntity.setCreatedBy(1); // Hardcoded as per instruction

            // Save
            skillTestResultRepository.save(resultEntity);
            System.out.println("Saved Exam Result for: " + dto.getPayrollId());

        } catch (Exception e) {
            System.err.println("Error Saving Local Result: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int parseInteger(String val) {
        try {
            return (int) Double.parseDouble(val); // Handle "30.0" or "30"
        } catch (Exception e) {
            return 0;
        }
    }

    private double parseDouble(String val) {
        try {
            return Double.parseDouble(val);
        } catch (Exception e) {
            return 0.0;
        }
    }

    // ==================================================================================
    // HELPER: CENTRALIZED MAPPING LOGIC (DB Entity -> API DTO)
    // ==================================================================================
    private EmpExamDataDTO mapEntityToDto(SkillTestDetails entity) {
        EmpExamDataDTO dto = new EmpExamDataDTO();

        // --- Direct Fields ---
        dto.setTempId(entity.getTempPayrollId()); // Renamed
        dto.setName(entity.getFirstName());
        dto.setSurname(entity.getLastName());

        // userName = "SURNAME FIRSTNAME" (Based on sample: "GADDAM MAHESH KUMAR")
        String fullName = (entity.getLastName() != null ? entity.getLastName() : "") + " " +
                (entity.getFirstName() != null ? entity.getFirstName() : "");
        dto.setUserName(fullName.trim());

        // DOB: Avoid empty string "" which crashes DBs. Send null or formatted date.
        if (entity.getDob() != null) {
            dto.setDob(entity.getDob().toString());
        } else {
            dto.setDob(null); // Explicitly null rather than ""
        }

        // Mobile: Default to 0L if null
        dto.setMobileNo(entity.getContact_number() != null ? entity.getContact_number() : 0L);
        dto.setEmail(entity.getEmail());

        // Passwords
        String rawPassword = entity.getPassword();
        dto.setPasswordDecrypt(rawPassword); // Send raw in 'passwordDecrypt'
        dto.setPassword(md5Hash(rawPassword)); // Send hash in 'password'

        dto.setStatus("Test"); // Changed to "Test" as per payload sample

        // --- Dynamic Relationships (Using Null Checks) ---

        // Employee Type (Hardcoded to EMPLOYEE as per requirement)
        dto.setStudentType("EMPLOYEE");

        // Gender
        if (entity.getGender() != null) {
            dto.setGender(entity.getGender().getGenderName());
        } else {
            dto.setGender("MALE"); // Fallback
        }

        // Subject
        if (entity.getSubject() != null) {
            dto.setSubject(entity.getSubject().getSubject_name());
        }

        // EmpLevel (Renamed from Program)
        if (entity.getEmployeeLevel() != null) {
            dto.setEmpLevel(entity.getEmployeeLevel().getLevel_name());
        }

        // Campus
        if (entity.getCampus() != null) {
            dto.setCampusId(Long.valueOf(entity.getCampus().getCampusId()));
            dto.setCampusName(entity.getCampus().getCampusName());
        }

        // City
        if (entity.getCity() != null) {
            dto.setCityId(Long.valueOf(entity.getCity().getCityId()));
            dto.setCityName(entity.getCity().getCityName());
        }

        // Building removed from payload

        // Group
        if (entity.getOrientationGroup() != null) {
            dto.setGroup(entity.getOrientationGroup().getGroupName());
        } else {
            dto.setGroup("Mpc"); // Default from sample if missing
        }

        return dto;
    }

    // Helper for MD5
    private String md5Hash(String input) {
        if (input == null)
            return null;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            java.math.BigInteger no = new java.math.BigInteger(1, messageDigest);
            String hashtext = no.toString(16);
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }
            return hashtext;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // ==================================================================================
    // HELPER: PUSH DATA TO API (The actual POST request)
    // ==================================================================================
    private String pushEmployeeData(String empId, EmpExamDataDTO employeeData) {
        try {
            // Log JSON for debugging
            String jsonPreview = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(employeeData);
            System.out.println("--- SYNCING EMP: " + empId + " ---");
            System.out.println(jsonPreview);
            System.out.println("--------------------------------");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // UPDATED: Basic Auth
            headers.setBasicAuth(API_USERNAME, API_PASSWORD);

            HttpEntity<EmpExamDataDTO> requestEntity = new HttpEntity<>(employeeData, headers);
            String finalUrl = SAVE_EMP_URL + "?empId=" + empId;

            ResponseEntity<String> response = restTemplate.exchange(
                    finalUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String.class);

            return "Success: " + response.getBody();

        } catch (Exception e) {
            System.err.println("API Call Failed: " + e.getMessage());
            return "Failed: " + e.getMessage();
        }
    }
}
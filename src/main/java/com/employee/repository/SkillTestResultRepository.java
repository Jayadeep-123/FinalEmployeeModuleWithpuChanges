package com.employee.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.employee.dto.SkillTestResultDTO;
import com.employee.entity.SkillTestResult;

public interface SkillTestResultRepository extends JpaRepository<SkillTestResult, Integer> {

    @Query("SELECT new com.employee.dto.SkillTestResultDTO("
            + "d.tempPayrollId, "
            + "CONCAT(d.firstName, ' ', d.lastName), "
            + "s.subject_name, "
            + "r.examDate, "
            + "r.noOfQuestion, "
            + "r.noOfQuesAttempt, "
            + "r.noOfQuesUnattempt, "
            + "r.noOfQuesWrong, "
            + "r.totalMarks) "
            + "FROM SkillTestResult r "
            + "JOIN r.skillTestDetlId d "
            + "LEFT JOIN d.subject s "
            + "WHERE d.tempPayrollId = :tempPayrollId "
            + "AND r.isActive = 1 "
            + "ORDER BY r.examDate DESC")
    List<SkillTestResultDTO> findSkillTestDetailsByPayrollId(@Param("tempPayrollId") String tempPayrollId);

    @Query("SELECT r from  SkillTestResult r JOIN FETCH r.skillTestDetlId d LEFT JOIN FETCH r.skillTestApprovalStatus")
    List<SkillTestResult> findTestResultsWithIds();

    // Find active results for a specific employee
    List<SkillTestResult> findBySkillTestDetlIdAndIsActive(com.employee.entity.SkillTestDetails skillTestDetlId,
            int isActive);

    @Query("SELECT r FROM SkillTestResult r JOIN r.skillTestDetlId d " +
            "WHERE d.tempPayrollId = :tempPayrollId AND r.isActive = 1 " +
            "ORDER BY r.examDate DESC LIMIT 1")
    java.util.Optional<SkillTestResult> findLatestActiveByPayrollId(@Param("tempPayrollId") String tempPayrollId);
}
package com.employee.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.employee.entity.EmpSalaryInfo;

@Repository
public interface EmpSalaryInfoRepository extends JpaRepository<EmpSalaryInfo, Integer> {

    // Find by ID and is_active = 1
    @Query("SELECT esi FROM EmpSalaryInfo esi WHERE esi.empSalInfoId = :id AND esi.isActive = :isActive")
    Optional<EmpSalaryInfo> findByIdAndIsActive(@Param("id") Integer id, @Param("isActive") Integer isActive);

    // Find by employee ID and is_active = 1
    @Query("SELECT esi FROM EmpSalaryInfo esi WHERE esi.empId.emp_id = :empId AND esi.isActive = :isActive")
    Optional<EmpSalaryInfo> findByEmpIdAndIsActive(@Param("empId") Integer empId, @Param("isActive") Integer isActive);

    // Alternative: Find by employee entity and is_active = 1
    @Query("SELECT esi FROM EmpSalaryInfo esi JOIN esi.empId e WHERE e.emp_id = :empId AND esi.isActive = :isActive")
    Optional<EmpSalaryInfo> findByEmployeeIdAndIsActive(@Param("empId") Integer empId,
            @Param("isActive") Integer isActive);

    // Find by payroll ID
    @Query("SELECT esi FROM EmpSalaryInfo esi WHERE esi.payrollId = :payrollId")
    Optional<EmpSalaryInfo> findByPayrollId(@Param("payrollId") String payrollId);

    // Update only payroll_id field using native SQL (bypasses entity save which may
    // trigger encryption on bytea fields)
    // Note: @Transactional removed - transaction is managed by the service method
    // using REQUIRES_NEW
    @Modifying
    @Query(value = "UPDATE sce_employee.sce_emp_sal_info SET payroll_id = :payrollId WHERE emp_id = :empId AND is_active = 1", nativeQuery = true)
    int updatePayrollIdOnly(@Param("empId") Integer empId, @Param("payrollId") String payrollId);

    // Find decrypted salary info by temp_payroll_id (joining with Employee table
    // for reliability)
    @Query(value = "SELECT esi.emp_sal_info_id, esi.emp_id, e.payroll_id, esi.emp_payment_type_id, " +
            "sce_employee.fn_decrypt_sal(esi.monthly_take_home) as monthly_take_home, " +
            "sce_employee.fn_decrypt_sal(esi.ctc_words) as ctc_words, " +
            "sce_employee.fn_decrypt_sal(esi.yearly_ctc) as yearly_ctc, " +
            "esi.emp_structure_id, esi.grade_id, esi.temp_payroll_id, esi.cost_center_id, " +
            "esi.is_pf_eligible, esi.is_esi_eligible, esi.is_active, esi.created_by, " +
            "esi.created_date, esi.updated_by, esi.updated_date " +
            "FROM sce_employee.sce_emp_sal_info esi " +
            "JOIN sce_employee.sce_emp e ON esi.emp_id = e.emp_id " +
            "WHERE e.temp_payroll_id = :tempPayrollId AND esi.is_active = 1", nativeQuery = true)
    Optional<DecryptedSalaryInfoProjection> findDecryptedByTempPayrollId(@Param("tempPayrollId") String tempPayrollId);

    // Find decrypted salary info by payroll_id (joining with Employee table for
    // reliability)
    @Query(value = "SELECT esi.emp_sal_info_id, esi.emp_id, e.payroll_id, esi.emp_payment_type_id, " +
            "sce_employee.fn_decrypt_sal(esi.monthly_take_home) as monthly_take_home, " +
            "sce_employee.fn_decrypt_sal(esi.ctc_words) as ctc_words, " +
            "sce_employee.fn_decrypt_sal(esi.yearly_ctc) as yearly_ctc, " +
            "esi.emp_structure_id, esi.grade_id, esi.temp_payroll_id, esi.cost_center_id, " +
            "esi.is_pf_eligible, esi.is_esi_eligible, esi.is_active, esi.created_by, " +
            "esi.created_date, esi.updated_by, esi.updated_date " +
            "FROM sce_employee.sce_emp_sal_info esi " +
            "JOIN sce_employee.sce_emp e ON esi.emp_id = e.emp_id " +
            "WHERE e.payroll_id = :payrollId AND esi.is_active = 1", nativeQuery = true)
    Optional<DecryptedSalaryInfoProjection> findDecryptedByPayrollId(@Param("payrollId") String payrollId);

}

package com.employee.repository;
 
import java.util.Optional;
 
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
 
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
	Optional<EmpSalaryInfo> findByEmployeeIdAndIsActive(@Param("empId") Integer empId, @Param("isActive") Integer isActive);
	
	// Find by payroll ID
	@Query("SELECT esi FROM EmpSalaryInfo esi WHERE esi.payrollId = :payrollId")
	Optional<EmpSalaryInfo> findByPayrollId(@Param("payrollId") String payrollId);
	
	// Update only payroll_id field using native SQL (bypasses entity save which may trigger encryption on bytea fields)
	// Note: @Transactional removed - transaction is managed by the service method using REQUIRES_NEW
	@Modifying
	@Query(value = "UPDATE sce_employee.sce_emp_sal_info SET payroll_id = :payrollId WHERE emp_id = :empId AND is_active = 1", nativeQuery = true)
	int updatePayrollIdOnly(@Param("empId") Integer empId, @Param("payrollId") String payrollId);
 
}
 
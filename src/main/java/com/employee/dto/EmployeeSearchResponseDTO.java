package com.employee.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Employee Search Response
 * Contains employee search result fields
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeSearchResponseDTO {
    private Integer empId; // emp_id
    private String empName; // first_name + last_name
    private String payRollId; // payroll_id
    private String departmentName; // department name
    private String modeOfHiringName; // mode of hiring name
    private String tempPayrollId; // temp_payroll_id (kept for backward compatibility)
}


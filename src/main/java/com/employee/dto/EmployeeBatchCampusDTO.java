package com.employee.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeBatchCampusDTO {
    private String payrollId;
    private String employeeName;
    private List<CampusDetailDTO> campusDetails;

    // Additional Fields
    private String employeeType; // "Shared" or "Not Shared"
    private Integer managerId;
    private String managerName;
    private Integer reportingManagerId;
    private String reportingManagerName;
    private Integer departmentId;
    private String departmentName;
    private Integer designationId;
    private String designationName;
}

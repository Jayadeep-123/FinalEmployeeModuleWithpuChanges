package com.employee.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CampusEmployeeDTO {
    private Integer cmpsEmployeeId;
    private Integer empId;
    private Integer cmpsId;
    private Integer roleId;
    private Integer attendanceStatus;
    private Integer isActive;
    private Integer createdBy;
    private Integer updatedBy;
}

package com.employee.dto;
 
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
 
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeCampusAddressDTO {
	private String payrollId;
    private String fullAddress;
    private String buildingMobileNo;
    private String employeeMobileNo;
    private String managerName;
    private String managerMobileNo;
    private String ReportingManagerName;
    private String reportingManagerMobileNo;
}
 
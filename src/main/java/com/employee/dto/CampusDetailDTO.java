package com.employee.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CampusDetailDTO {
    private Integer campusId;
    private String campusName;
    private Integer cityId;
    private String city;
    private String fullAddress;
    private String buildingMobileNo;
}

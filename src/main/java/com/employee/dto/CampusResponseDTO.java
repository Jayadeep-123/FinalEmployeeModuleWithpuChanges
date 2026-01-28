package com.employee.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CampusResponseDTO {
    private Integer campusId;
    private String campusName;
    private String campusCode;
    private String campusType;

    private Integer businessId;
    private String businessName;

    private Integer cityId;
    private String cityName;
}

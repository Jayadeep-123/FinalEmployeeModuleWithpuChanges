package com.employee.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeLocationDTO {
    private Integer stateId;
    private String stateName;
    private Integer cityId;
    private String cityName;
    private List<CampusInfoDTO> campuses;
}

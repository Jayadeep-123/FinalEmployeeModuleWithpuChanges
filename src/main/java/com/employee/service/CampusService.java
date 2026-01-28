package com.employee.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.employee.dto.CampusResponseDTO;
import com.employee.entity.Campus;
import com.employee.repository.CampusRepository;

@Service
public class CampusService {

    @Autowired
    private CampusRepository campusRepository;

    public List<CampusResponseDTO> getCampusesByBusinessId(Integer businessId) {
        List<Campus> campuses = campusRepository.findByBusinessTypeIdAndIsActive(businessId);

        return campuses.stream().map(campus -> {
            CampusResponseDTO dto = new CampusResponseDTO();
            dto.setCampusId(campus.getCampusId());
            dto.setCampusName(campus.getCampusName());
            dto.setCampusCode(campus.getCmps_code());
            dto.setCampusType(campus.getCmps_type());

            if (campus.getBusinessType() != null) {
                dto.setBusinessId(campus.getBusinessType().getBusinessTypeId());
                dto.setBusinessName(campus.getBusinessType().getBusinessTypeName());
            }

            if (campus.getCity() != null) {
                dto.setCityId(campus.getCity().getCityId());
                dto.setCityName(campus.getCity().getCityName());
            }

            return dto;
        }).collect(Collectors.toList());
    }
}

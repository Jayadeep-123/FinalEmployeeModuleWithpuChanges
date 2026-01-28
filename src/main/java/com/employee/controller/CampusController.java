package com.employee.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.employee.dto.CampusResponseDTO;
import com.employee.service.CampusService;

@RestController
@RequestMapping("/api/campus")
@CrossOrigin("*")
public class CampusController {

    @Autowired
    private CampusService campusService;

    @GetMapping("/business/{businessId}")
    public ResponseEntity<List<CampusResponseDTO>> getCampusesByBusinessId(@PathVariable Integer businessId) {
        List<CampusResponseDTO> campuses = campusService.getCampusesByBusinessId(businessId);

        if (campuses.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(campuses);
    }
}

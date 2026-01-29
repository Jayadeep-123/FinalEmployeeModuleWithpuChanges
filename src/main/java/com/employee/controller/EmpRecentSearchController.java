package com.employee.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.employee.dto.EmpRecentSearchDTO;
import com.employee.service.EmpRecentSearchService;

@RestController
@RequestMapping("/api/recent-search")
public class EmpRecentSearchController {

    @Autowired
    private EmpRecentSearchService empRecentSearchService;

    @PostMapping("/save")
    public ResponseEntity<String> saveRecentSearch(@RequestBody EmpRecentSearchDTO dto) {
        try {
            empRecentSearchService.saveRecentSearch(dto);
            return ResponseEntity.ok("Recent search tracked successfully.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to track recent search: " + e.getMessage());
        }
    }

    @PostMapping("/logout/{loginEmpId}")
    public ResponseEntity<String> updateFinalLogout(@PathVariable("loginEmpId") Integer loginEmpId) {
        try {
            empRecentSearchService.updateFinalLogout(loginEmpId);
            return ResponseEntity.ok("Session logout tracked successfully.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to track session logout: " + e.getMessage());
        }
    }
}

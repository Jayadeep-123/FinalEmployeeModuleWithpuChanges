package com.employee.entity;

import java.sql.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "emp_onboarding_status", schema = "sce_employee")
public class EmpOnboardingStatusView {

	@Id
	private int emp_id;
	private String employee_name;
	private int cmps_id;
	private String cmps_name;
	private Integer category_id;
	private String category_name;
	private String payroll_id;
	private String temp_payroll_id;
	private Date date_of_join;
	private Date leaving_date;
	private String gender_name;
	private String city_name;
	private String remarks;
	private int join_type_id;
	private String join_type;
	private String check_app_status_name;

}

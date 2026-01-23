package com.employee.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BankInfoGetDTO {

	private String paymentType;
	private String bankName;
	private String bankBranch;
	private String personalAccountHolderName;
	private Long personalAccountNumber;
	private String ifscCode;
	private String isSalaryLessThan40000; // optional for salary account
	private String payableAt;

}
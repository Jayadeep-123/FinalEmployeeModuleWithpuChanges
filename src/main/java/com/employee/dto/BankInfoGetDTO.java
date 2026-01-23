package com.employee.dto;
 
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
 
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BankInfoGetDTO {
	
	 private String paymentType;
	    private String bankName;
	    private String bankBranch;
	    private String personalAccountHolderName;
	    private Long personalAccountNumber;
	    private String ifscCode;
	    private String isSalaryLessThan40000; // optional for salary account
	    private String payableAt;  
	    
	    private String BankManagerName;
	    private Long ManagerContact;
	    private String ManagerEmail;
	    private Long RelationshipOfficerNumber;
	    private String RelationshipOfficerName;
	    private String RelationshipOfficerEmail;
	    
}
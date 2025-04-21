package org.saidone.model;

import lombok.Data;

@Data
public class SystemSearchRequest {
	
	private String query;
	private int maxItems;
	private int skipCount;

}

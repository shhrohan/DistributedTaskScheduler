package com.cred.distributedtaskscehduler.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChildTask {

	private String masterTaskId;
	private List<Object> inputList;
	private String runFunction;
	
}

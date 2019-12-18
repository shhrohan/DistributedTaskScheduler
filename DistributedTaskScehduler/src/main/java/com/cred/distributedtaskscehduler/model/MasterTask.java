package com.cred.distributedtaskscehduler.model;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

import com.cred.distributedtaskscehduler.enums.TaskStatus;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MasterTask implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public MasterTask(){
		
	}
	
	String id = UUID.randomUUID().toString();

	TaskStatus status = TaskStatus.QUEUED;
	List<Object> inputList;
	String variableJson;
	String templateFunction;

	public MasterTask(String variableJson, String templateFunction) {
		this.variableJson = variableJson;
		this.templateFunction = templateFunction;
	}
}

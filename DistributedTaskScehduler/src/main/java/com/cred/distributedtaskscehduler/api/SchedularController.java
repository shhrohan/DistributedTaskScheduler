	package com.cred.distributedtaskscehduler.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.cred.distributedtaskscehduler.config.ProjectConfiguraton;
import com.cred.distributedtaskscehduler.model.MasterTask;
import com.cred.distributedtaskscehduler.service.SchedulerService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Controller
@Slf4j
@RequestMapping("schedule")
public class SchedularController {

	@Autowired
	SchedulerService schedulerService;
	
	@Autowired
	ProjectConfiguraton config;

	@GetMapping("/ping")
	@ResponseBody
	public Mono<String> ping() {

		log.info("Received [PING]. Replying with [PONG]");
		return Mono.just("Scheduler Service Status :  [Running]");
	}

	@PostMapping(value = "/create", consumes = { MediaType.APPLICATION_FORM_URLENCODED_VALUE })
	public Mono<ResponseEntity<String>> create(MasterTask masterTask) {

		log.info("Received new task for execution : " + masterTask);
		
		return schedulerService
			.submitTask(masterTask)
			.flatMap(masterTaskId -> Mono.just(new ResponseEntity<String>(masterTaskId, HttpStatus.ACCEPTED)))
			.switchIfEmpty(Mono.defer(() -> {
				log.error("Could not submit Task : " + masterTask.getId());
			return Mono.just(new ResponseEntity<String>("", HttpStatus.ACCEPTED));
		}));
	}

	@GetMapping("/status")
	@ResponseBody
	public Mono<ResponseEntity<MasterTask>> status(@RequestParam String taskId) {

		log.info("Received Status request for taskId : " + taskId);
		return schedulerService
				.getTaskResult(taskId)
				.flatMap(taskExecuteStatus -> 
					Mono.just(new ResponseEntity<MasterTask>(taskExecuteStatus, HttpStatus.ACCEPTED)))
				.switchIfEmpty(Mono.defer(() -> {
					log.error("Status of Task : " + taskId + "NOT FOUND");
					return Mono.empty();
				}));
	}
	
	@GetMapping("/node-beat")
	@ResponseBody
	public Mono<ResponseEntity<Boolean>> nodeHeartBeat(@RequestParam String nodeId) {

		return schedulerService	
				.updateWorkerNodes(nodeId, null)
				.flatMap(taskExecuteStatus -> 
					Mono.just(new ResponseEntity<Boolean>(taskExecuteStatus, HttpStatus.ACCEPTED)))
				.switchIfEmpty(Mono.defer(() -> {
					log.error("Node with [Node Id : " + nodeId + "could not be registered");
					return Mono.just(new ResponseEntity<Boolean>(false, HttpStatus.BAD_REQUEST));
				}));
	}
	
}

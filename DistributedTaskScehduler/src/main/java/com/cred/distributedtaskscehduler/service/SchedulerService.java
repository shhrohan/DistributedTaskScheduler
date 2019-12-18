	package com.cred.distributedtaskscehduler.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import com.cred.distributedtaskscehduler.config.ProjectConfiguraton;
import com.cred.distributedtaskscehduler.enums.TaskStatus;
import com.cred.distributedtaskscehduler.model.MasterTask;
import com.cred.distributedtaskscehduler.redis.ResultCacheService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class SchedulerService {

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	ProjectConfiguraton configuraton;
	
	@Autowired
	ResultCacheService resultService;

	private Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private Map<String, JsonObject> workerNodes = new ConcurrentHashMap<>();

	public Mono<Boolean> updateWorkerNodes(String nodeId, JsonObject nodeConfig) {
		if (workerNodes.put(nodeId, nodeConfig) != null) {
			return Mono.just(true);
		}
		return Mono.empty();
	}
	
	public Integer getAvailableWorkerNodeCount() {
		return workerNodes.size();
	}

	public Mono<String> submitTask(MasterTask masterTask) {

		log.info("Publishing Task For Execution : " + masterTask);

		return Mono.create(stringMonoSink -> {

			ListenableFuture<SendResult<String, String>> future = kafkaTemplate.send(configuraton.getPubsubTopic(),
					gson.toJson(masterTask));

			future.addCallback(new ListenableFutureCallback<SendResult<String, String>>() {

				@Override
				public void onSuccess(SendResult<String, String> result) {
					log.info("Published successfully");
					masterTask.setStatus(TaskStatus.SUBMITTED);
					resultService.updateTaskResult(masterTask); 
					stringMonoSink.success(masterTask.getId());
				}

				@Override
				public void onFailure(Throwable ex) {
					try {
						masterTask.setStatus(TaskStatus.SUBMITION_FAILED);
						resultService.updateTaskResult(masterTask);
						log.error("Error while publishing Task for execution, [Retrying after 30 Seconds]", ex);
						Thread.sleep(30000);
						submitTask(masterTask);
					} catch (InterruptedException e) {
						log.error(e.getMessage());
						Thread.currentThread().interrupt();
						stringMonoSink.error(ex);
					}
				}
			});
		});
	}

	public Mono<MasterTask> getTaskResult(String taskId) {
		return this.resultService.getMasterTaskResult(taskId);
	}

}

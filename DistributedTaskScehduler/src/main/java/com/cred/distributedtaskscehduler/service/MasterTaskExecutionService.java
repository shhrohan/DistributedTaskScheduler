package com.cred.distributedtaskscehduler.service;

import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import com.cred.distributedtaskscehduler.config.ProjectConfiguraton;
import com.cred.distributedtaskscehduler.model.ChildTask;
import com.cred.distributedtaskscehduler.model.MasterTask;
import com.cred.distributedtaskscehduler.redis.ResultCacheService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MasterTaskExecutionService {

	private Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private ObjectMapper mapper;

	@Autowired
	ProjectConfiguraton configuraton;

	@Autowired
	SchedulerService schedulerService;

	@Autowired
	ResultCacheService cacheService;

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@PostConstruct
	private void init() {
		mapper = new ObjectMapper();
	}

	@KafkaListener(topics = "#{'${pubsub.topic}'}", groupId = "#{'${pubsub.consumergroup}'}")
	public void consumeTaskOnSubmition(String message) {

		try {
			log.info("Received message from kafka : " + message);
			MasterTask masterTask = gson.fromJson(message, MasterTask.class);

			String runFunction = getActualFunctionToRun(masterTask);

			int availableWorkerNodes = schedulerService.getAvailableWorkerNodeCount();
			masterTask.setChunksToExecute(availableWorkerNodes);
			int i = 0;
			int breaks = masterTask.getInputList().size() / availableWorkerNodes;
			do {
				publishChildTask(
						new ChildTask(masterTask.getId(), masterTask.getInputList().subList(i, breaks), runFunction));
				i = breaks;
				breaks += breaks;

				if (breaks >= masterTask.getInputList().size()) {
					breaks = masterTask.getInputList().size() - 1;
					publishChildTask(new ChildTask(masterTask.getId(), masterTask.getInputList().subList(i, breaks),
							runFunction));
					break;
				}
			} while (breaks < masterTask.getInputList().size());

			/*
			 * Update redis with master task's chunks
			 */
			cacheService.updateTaskResult(masterTask);

		} catch (Exception ex) {
			log.error("received error while reading MasterTask message from kafka", ex);
		}
	}

	private void publishChildTask(ChildTask childTask) {

		ListenableFuture<SendResult<String, String>> future = kafkaTemplate.send(configuraton.getPubsubChildTaskTopic(),
				gson.toJson(childTask));

		future.addCallback(new ListenableFutureCallback<SendResult<String, String>>() {

			@Override
			public void onSuccess(SendResult<String, String> result) {
				log.info("Published successfully");
			}

			@Override
			public void onFailure(Throwable ex) {
				try {
					log.error("Error while publishing Task for execution Waiting 30 Secs before attempting to publish",
							ex);
					Thread.sleep(30000);
					publishChildTask(childTask);
				} catch (InterruptedException e) {
					log.error("", e);
					Thread.currentThread().interrupt();
				}
			}
		});

	}

	private String getActualFunctionToRun(MasterTask masterTask) throws JsonProcessingException {
		String executableFunction = masterTask.getTemplateFunction();
		Map<String, String> variableMap = mapper.readValue(masterTask.getVariableJson(),
				new TypeReference<Map<String, String>>() {
				});
		for (Entry<String, String> placeHolder : variableMap.entrySet()) {
			executableFunction = executableFunction.replace("{{" + placeHolder.getKey() + "}}", placeHolder.getValue());
		}
		return executableFunction;
	}

}

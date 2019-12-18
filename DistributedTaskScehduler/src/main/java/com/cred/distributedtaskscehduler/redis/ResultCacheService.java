package com.cred.distributedtaskscehduler.redis;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import com.cred.distributedtaskscehduler.enums.TaskStatus;
import com.cred.distributedtaskscehduler.model.MasterTask;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@SuppressWarnings("rawtypes")
public class ResultCacheService {

	@Autowired
	private ReactiveRedisOperations<String, String> userOps;

	@Autowired
	RedisTemplate<String, String>  redisTemplate;
	
	private Gson gson = new GsonBuilder().setPrettyPrinting().create();

	public void updateTaskResult(MasterTask task) {

		/*
		 * Below operation happens in Transactions
		 */
		
		redisTemplate.execute(new SessionCallback<List<Object>>() {
			@SuppressWarnings("unchecked")
			@Override
			public List<Object> execute(RedisOperations operations) {
				operations.multi();
				MasterTask cachedTask =  gson.fromJson(operations.opsForValue().get(task.getId()).toString(), MasterTask.class);
				cachedTask.setStatus(task.getStatus());
				operations.opsForSet().add(task.getId(), task);
				return operations.exec();
			}
		});
	}
	
	public void incrementChunksCompleted(String taskId) {

		/*
		 * Below operation happens in Transactions
		 */
		
		redisTemplate.execute(new SessionCallback<List<Object>>() {
			@SuppressWarnings("unchecked")
			@Override
			public List<Object> execute(RedisOperations operations) {
				operations.multi();
				MasterTask cachedTask =  gson.fromJson(operations.opsForValue().get(taskId).toString(), MasterTask.class);
				cachedTask.setChunksExecuted(cachedTask.getChunksExecuted() + 1);
				if(cachedTask.getChunksExecuted() == cachedTask.getChunksToExecute()) {
					cachedTask.setStatus(TaskStatus.COMPLETED);
				}
				operations.opsForSet().add(taskId, cachedTask);
				return operations.exec();
			}
		});
	}
	
	public void markTaskCompleted(MasterTask task) {

		/*
		 * Below operation happens in Transactions
		 */
		
		redisTemplate.execute(new SessionCallback<List<Object>>() {
			@SuppressWarnings("unchecked")
			@Override
			public List<Object> execute(RedisOperations operations) {
				operations.multi();
				MasterTask cachedTask =  gson.fromJson(operations.opsForValue().get(task.getId()).toString(), MasterTask.class);
				
				cachedTask.setStatus(task.getStatus());
				operations.opsForSet().add(task.getId(), cachedTask);
				return operations.exec();
			}
		});
	}

	public Mono<MasterTask> getMasterTaskResult(String taskId) {

		return userOps.opsForValue().get(taskId).flatMap( taskJson -> {
			MasterTask task = gson.fromJson(taskJson, MasterTask.class);
			return Mono.just(task);
		}).switchIfEmpty(
				Mono.defer(() ->{
					log.warn("No Status available for task [id : " + taskId +"]");
					return Mono.empty();
				})
		);
		
	}


}

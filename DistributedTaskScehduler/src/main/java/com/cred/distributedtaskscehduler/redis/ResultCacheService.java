package com.cred.distributedtaskscehduler.redis;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import com.cred.distributedtaskscehduler.model.MasterTask;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@SuppressWarnings("unchecked")
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

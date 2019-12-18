package com.cred.distributedtaskscehduler.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@Data
public class ProjectConfiguraton {

	@Value("${pubsub.namespace}")
	private String namespace;

	@Value("${pubsub.topic}")
	private String pubsubTopic;
	
	@Value("${pubsub.task-shard.topic}")
	private String pubsubChildTaskTopic;
	
	@Value("${pubsub.result.topic}")
	private String pubsubResultTopic;

	@Value("${pubsub.consumergroup}")
	private String consumerGroup;

	@Value("${pubsub.consumercount}")
	private int consumerCount;
	
}

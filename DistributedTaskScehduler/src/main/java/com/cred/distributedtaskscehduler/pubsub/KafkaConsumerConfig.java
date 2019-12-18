package com.cred.distributedtaskscehduler.pubsub;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import com.cred.distributedtaskscehduler.config.ProjectConfiguraton;


@EnableKafka
@Configuration
public class KafkaConsumerConfig {
	private ProjectConfiguraton config;

	@Autowired
	public void setConfig(ProjectConfiguraton config) {
		this.config = config;
	}
	
	private ConsumerFactory<String, String> consumerFactory() {
		Map<String, Object> props = new HashMap<>();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getNamespace());
		props.put(ConsumerConfig.GROUP_ID_CONFIG, config.getConsumerGroup());
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		return new DefaultKafkaConsumerFactory<>(props);
	}

	@Bean
	public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
		ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConcurrency(config.getConsumerCount());
		factory.setConsumerFactory(consumerFactory());

		return factory;
	}
	
}
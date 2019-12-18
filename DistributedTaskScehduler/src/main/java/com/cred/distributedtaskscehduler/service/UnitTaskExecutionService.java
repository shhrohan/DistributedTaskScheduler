package com.cred.distributedtaskscehduler.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.cred.distributedtaskscehduler.model.ChildTask;
import com.cred.distributedtaskscehduler.redis.ResultCacheService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UnitTaskExecutionService {

	private Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private CountDownLatch latch;
	private ExecutorService e;

	@Autowired
	private ResultCacheService cacheService;
	
	@PostConstruct
	private void init() {
		latch = new CountDownLatch(Runtime.getRuntime().availableProcessors());
		e = Executors.newWorkStealingPool();
	}

	@KafkaListener(topics = "#{'${pubsub.task-shard.topic}'}", groupId = "#{'${pubsub.consumergroup}'}")
	public void consumeTaskShard(String message) {

		log.info("Received message from kafka : " + message);
		ChildTask childTask = gson.fromJson(message, ChildTask.class);

		int availableProcessors = Runtime.getRuntime().availableProcessors();
		int i = 0;
		int breaks = childTask.getInputList().size() / availableProcessors;
		int threadId = 1;
		do {
			executeChildTaskShard(childTask.getMasterTaskId(), childTask.getInputList().subList(i, breaks), threadId++,
					childTask.getRunFunction());
			i = breaks;
			breaks += breaks;

			if (breaks >= childTask.getInputList().size()) {
				breaks = childTask.getInputList().size() - 1;
				executeChildTaskShard(childTask.getMasterTaskId(), 
						childTask.getInputList().subList(i, breaks),
						threadId++, childTask.getRunFunction());
				break;
			}
		} while (breaks < childTask.getInputList().size());

		try {
			while (true) {
				if (!latch.await(5L, TimeUnit.SECONDS)) {
					log.info("Waiting 5 seconds for Child Thread To Finish [Running Threads :  " + latch.getCount()
							+ "]");
				} else {
					log.info("All Child Threads have Finished");
					break;
				}
			}
		} catch (Exception ex) {
			log.error("Error while running child task for Master task (id : " + childTask.getMasterTaskId() + "] ", ex);
		}
	}

	private void executeChildTaskShard(String masterTaskId, List<Object> subList, int threadId, String runFunction) {

		WorkerThread worker = new WorkerThread(masterTaskId, subList, threadId, runFunction);
		e.submit(worker);
	}

	private class WorkerThread implements Runnable {

		String jobId;
		int threadId;
		List<Object> inputList;
		String functionToRun;

		public WorkerThread(String jobId, List<Object> input, int threadID, String command) {
			this.jobId = jobId;
			this.inputList = input;
			this.functionToRun = command;
			this.threadId = threadID;
		}

		@Override
		public void run() {
			runProcess(this.functionToRun);
		}

		private void runProcess(String command) {
			try {
				StringBuilder stbOutput = new StringBuilder();
				boolean ranOK = true;

				for (int i = 0; i < inputList.size(); i++) {

					String[] cmd = new String[2];
					cmd[0] = command;
					cmd[1] = inputList.get(i).toString();
					Process proc = Runtime.getRuntime().exec(cmd);

					BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream()));
					writer.write(inputList.get(i).toString(), 0, inputList.get(i).toString().length());
					writer.newLine();
					writer.close();

					BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));

					String s = null;
					while ((s = stdInput.readLine()) != null) {
						stbOutput.append(s + "\n");
					}

					BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
					StringBuilder stbError = new StringBuilder();
					while ((s = stdError.readLine()) != null) {
						stbError.append(s + "\n");
					}

					int exitValue = proc.waitFor();
					proc.getErrorStream().close();
					proc.getInputStream().close();
					proc.getOutputStream().close();
					log.info("Workthread [id : " + this.threadId + "] for input [" + inputList.get(i)
							+ "] returned with : " + exitValue);

					if (proc.exitValue() != 0) {
						log.error("[ERROR OCCURRED] Workthread [id : " + this.threadId + "] for input ["
								+ inputList.get(i) + "]\nConsole Output:\n" + stbOutput.toString()
								+ "\nError Output : \n" + stbError.toString());
					}
					ranOK &= (exitValue == 0);
				}

				if (ranOK) {
					log.info("All inputs for Workthread [id : " + this.threadId + "] ran OK");
					cacheService.incrementChunksCompleted(jobId);
					latch.countDown();
				} else {
					log.error("NOT All inputs for Workthread [id : " + this.threadId
							+ "] ran OK. Moving ahead since this is Operational Failure and not an infra one hence on Reattempt also this would FAIL.");
					latch.countDown();
				}

				
			} catch (Exception ex) {
				log.error("Workerthread [id : " + this.threadId + "] for job [jobId : " + this.jobId
						+ "] returned with : [ERROR]", ex);
			}
		}

	}
}

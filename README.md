# DistributedTaskScheduler  

# Prerequisites :
  server.port= 30000

  # pubsub (kafka) props
  pubsub.namespace=localhost:9092

  pubsub.topic = distributed_task

  pubsub.task-shard.topic = distributed_task_shard

  pubsub.result.topic = distributed_task_completed

  pubsub.consumergroup = task-subscribers

  pubsub.consumercount = 2

  # Redis Caching Params
  spring.cache.type=redis

  spring.redis.host=localhost

  spring.redis.port=6379
  
#  Assumptions :
1. If there are multiple Schedulers working concurrently then, They will have to be working under publically exposed Loadbalancer which will route client request to 1 of the working instances of the server nodes
# Work Flow 
a) 1 or more scheduler working behind an NginX Load balancer server. [Not part of this assignment. But an assumption]

b) client will send a serialized MasterTask Object over a rest (POst) call.

c) Status of the task submited by the client will have to be polled by the client. API for which is made available already in the implementation.
  
d) Once client submits the task its saved to Redis with appropriate status.
  
e) same task is published over kafka for one of the Subscribers to Consume it.
  
f) the scheduler consumer instance, will have hear beats available from all the worker nodes up and running. 

    f1) the scheduler consumer instance, will replace all the variables in from Template function using values provided in variableJson field of the MasterTask.
    
    f2) based on the number of worker nodes available, the input list that is to be processed by the command will be devided and further published to KAFKA again in chunked format.
  
g) Worker node will consume the shard of the tsk it has to execute. it will even furthe devide the shard based on number parallel cores avaliable at its disposal. to have to maximum possible concurrency in terms of shards execution

h) each thread will mutate the latch supplied to it.

i) once latch is reached to 0 on the worked consumer, then this worker consumer will increment the chunks executed variable of the Master task and update redis

j) client will automaticlly see task status as finished once Worker consumers have executed on api available at its end where it was earlier seeing in progress status.

k) All redis updates are supposed to happen in atomic transactions. to avoid stale ready.
  
  
 

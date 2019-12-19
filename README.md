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

2. The implentation support scope is limited bash scripts. this can be easilly enhanced to spport and run other languages as well given more time and efort
# Work Flow 
a) 1 or more scheduler working behind an NginX Load balancer server. [Not part of this assignment. But an assumption]
    a1) Scheduler can also work on multiple nodes since it is stateless.
    a2) When configured to run only on 1 node then no need of nGinX or any load balancer 

b) client will send a serialized MasterTask Object over a rest (POst) call.
    b1) MasterTask class is available in the Code
    b2) Its simple in structure. which reduces client side complexity to while submiting tasks to scheduler
    b3) Scheduler has been made to attemp max 10 times to submit the tasks

c) Client will poll execution Status of the task submited. API is made available already in the implementation is scheduler.
  
d) Once client submits the task

    d1) scheduler saves it to Redis with appropriate status.
    
    d2) publishes it to Kafka for initiating Execution
    
    d3) Any of the running Scheduler subscriber recieves it
    
    d4) prepares executable function after replacing variables in templateFunction.
    
    d5) devides the task input load depending on the available worker nodes
    
    d6) pubilsh individual task shard to kafka for Worker consumers to execute it.
    
f) the scheduler consumer instance, will have hear beats available from all the worker nodes up and running. 

    f1) the scheduler consumer instance, will replace all the variables in from Template function using values provided in variableJson field of the MasterTask.
    
    f2) based on the number of worker nodes available, the input list that is to be processed by the command will be devided and further published to KAFKA again in chunked format.
  
g) Worker node will consume the shard of the task it has to execute. 

    g1) it will even furthe devide the shard based on number parallel cores avaliable at its disposal. 
    
    g2) To have to maximum possible concurrency in terms of shards execution
    
    g3) each thread will mutate the latch supplied to it.
    
    g4) once latch is reached to 0 on the worked consumer, then this worker consumer will increment the chunks executed variable of the Master task and update redi
    
    g5) client will automaticlly see task status as finished once all Worker consumers have executed their shards.
    
    g6) Each worker consumer initiate Redis Transaction  to update its part of the work done.
    

k) All redis updates are supposed to happen in atomic transactions. to avoid stale ready.

## Prerequisites

### Integration with Apache Kafka

Streaming-Lite uses [kafka-clients](https://docs.confluent.io/platform/current/clients/index.html) to read from and write to kafka. In most cases library default configuration options apply. Most important of them:
- `partition.assignment.strategy` - RangeAssignor

However, there are few set differently:
- `enable.auto.commit` - false
- `isolation.level` - READ_COMMITTED

:::caution

If you want to read events from output topics in transactional manner your kafka client need to set `isolation.level` to `READ_COMMITED`, by default kafka set `READ_UNCOMMITED` option - more details in [kafka documentation](https://kafka.apache.org/24/documentation.html)

:::

## Nussknacker and K8s cluster

### Runtime container
Nussknacker scenario is deployed as k8s [deployment](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/). 
By default, it creates [nussknacker-lite-kafka-runtime](https://hub.docker.com/r/touk/nussknacker-lite-kafka-runtime) runtime container with scenario json representation passed as config map.
Runtime container executes infinite loop responsible for polling events from kafka topic.

### Startup probes
Nussknacker configures HTTP readiness and liveness healtcheck probes for runtime container.

Readiness probe is used at first and has and impact for container being treated as ready.
It's configuration should include e.g how many partition kafka topic has, what kind of metric provider is used
By default, is checks if container is ready every second for 60 times.


| Probe          | Type             | Default value |
|----------------|------------------|---------------|
| readinessProbe | periodSeconds    | 1s            |
| readinessProbe | failureThreshold | 60 times      |
| livenessProbe  | periodSeconds    | 3s            |
| livenessProbe  | failureThreshold | 10 times      |


### Parallelism
Tuning parallelism one can impact how many independent consumers are reading from kafka topic. 

It's worth noticing that in general number of replicas count should not exceed number of kafka partitions because in such a case exceeding replica will stay idle due to kafka assignment strategies.

### Deployment strategy

Nu uses `Recreate` deployment strategy together with `READ_COMMITED` `isolation.level` property to ensure transactional manner of processing events and to avoid
situation in which different deployments both reads events from the same topic.

### Failure recovery

In case of an unexpected errors e.g. network issue between kafka cluster and runtime nussknacker will try to reconnect after `waitAfterFailureDelay`

### Other
Other values different to K8s defaults
- `minReadySeconds` - 10 - specifies the minimum number of seconds for which a newly created Pod should be ready without any of its containers crashing, for it to be considered available
- `progressDeadlineSeconds` - 600 - specifies the number of seconds you want to wait for your Deployment to progress before the system reports back that the Deployment has failed progressing

## Scenarios - monitoring and troubleshooting

Each scenario has its own performance characteristics and considerations. This section describes common ways to monitor the health of a running scenario and how to handle common problems. Most of the sections are aimed not only at operations people but also at (especially advanced) editors of Nussknacker scenarios.

### Metrics

Each k8s pod representing runtime container is visble in metrics tab as separate instanceId.

![lite metrics](img/lite_metrics.png "lite metrics")

### Logging level 
Runtime container logging level can be specified by setting env variable
- `NUSSKNACKER_LOG_LEVEL` - logging level of console STDOUT appender

If you need more fine-grained control over logging you can provide your own `logback.xml` config file by overriding config map linked to your runtime container under `logback.xml` key. 
Please mind, that modifications made to this config map are transient - every (re)deploy of scenario, creates config map from scratch with default content.

In case you want to customize logging config in all your runtime containers at once, create `shared` config map with key `logback-inclusions.xml` with part of configuration which is to be included. Remember, that configuration must have its elements nested inside `<included></included>` tags. For more information checkout [Logback docs](https://logback.qos.ch/manual/configuration.html#fileInclusion)
Nussknacker does not maintain `shared` config map - so, here on the other hand, redeployment won't change it.

### Managing lifecycle of scenario

State of the scenario can be viewed in the scenario list, in the scenario details view or via API . Possible states can be grouped in the following categories:

* Not running
    * _NotDeployed_ (initial status before first deploy)
    * _Canceled_
* Running without problems
    * _Running_ - all replicas passed readiness probe and kafka clients connected to partitions
    * _Restarting_ - (after an unexpected exception)
* Temporary states
    * _DuringDeploy_ - some replicas not spawned or still connecting to kafka partitions
* Problem
    * _Failed_ - the scenario ended with an error
    * _Unknown_ should not happen, check the logs and consult Nussknacker team

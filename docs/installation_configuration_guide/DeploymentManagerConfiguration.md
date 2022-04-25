---
sidebar_position: 3
---
# Deployment Manager configuration

Configuration of Deployment Manager, which is component of the Designer that deploys scenario to given Engine (e.g. Streaming-Lite runtime or Flink in Streaming-Flink mode). 
Type of Deployment Manager is defined with `type` parameter, e.g. for running scenarios with Flink streaming job we would configure: 
```
deploymentConfig {     
  type: "flinkStreaming"
  restUrl: "http://localhost:8081"
}
```

Look at [configuration areas](./#configuration-areas) to understand where Deployment Manager configuration should be placed in Nussknacker configuration.

## Streaming-Lite on Kubernetes        
                                                                                
Please remember, that K8s Deployment Manager has to be run with properly configured K8s access. If you install the Designer
in K8s cluster (e.g. via Helm chart) this comes out of the box. If you want to run the Designer outside the cluster, you 
have to configure `.kube/config` properly.

`streaming-lite-k8s` Deployment Manager has the following configuration options:                 

| Parameter                | Type                                                 | Default value                       | Description                                                                              |
|--------------------------|------------------------------------------------------|-------------------------------------|------------------------------------------------------------------------------------------|
| dockerImageName          | string                                               | touk/nussknacker-lite-kafka-runtime | Runtime image (please note that it's **not** touk/nussknacker - which is designer image) |
| dockerImageTag           | string                                               | current nussknacker version         |                                                                                          |
| scalingConfig            | {fixedReplicasCount: int} or {tasksPerReplica: int}  | { tasksPerReplica: 4 }              | see [below](#configuring-replicas-count)                                                 |
| configExecutionOverrides | config                                               | {}                                  | see [below](#overriding-configuration-passed-to-runtime)                                 |
| k8sDeploymentConfig      | config                                               | {}                                  | see [below](#customizing-k8s-deployment)                                                 |
| nussknackerInstanceName  | string                                               | {?NUSSKNACKER_INSTANCE_NAME}        | see [below](#nussknacker-instance-name)                                                  |
| logbackConfigPath        | string                                               | {}                                  | see [below](#configuring-runtime-logging)                                                |
                                                 
### Customizing K8s deployment

By default, each scenario creates K8s Deployment. By default, Nussknacker will create following deployment:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  annotations:
    nussknacker.io/scenarioVersion: |-   
      {
        "versionId" : 2,
        "processName" : "DetectLargeTransactions",
        "processId" : 7,
        "user" : "jdoe@sample.pl",
        "modelVersion" : 2
      }
  labels:
    nussknacker.io/nussknackerInstanceName: "helm-release-name"
    nussknacker.io/scenarioId: "7"
    nussknacker.io/scenarioName: detectlargetransactions-080df2c5a7
    nussknacker.io/scenarioVersion: "2"
spec:
  minReadySeconds: 10
  selector:
    matchLabels:
      nussknacker.io/scenarioId: "7"
  strategy:
    type: Recreate
  template:
    metadata:
      labels:
        nussknacker.io/scenarioId: "7"
        nussknacker.io/scenarioName: detectlargetransactions-080df2c5a7
        nussknacker.io/scenarioVersion: "2"
      name: scenario-7-detectlargetransactions
    spec:
      containers:
      - env:
        - name: SCENARIO_FILE
          value: /data/scenario.json
        - name: CONFIG_FILE
          value: /opt/nussknacker/conf/application.conf,/data/modelConfig.conf
        - name: DEPLOYMENT_CONFIG_FILE
          value: /data/deploymentConfig.conf
        - name: LOGBACK_FILE
          value: /data/logback.xml
        - name: POD_NAME
          valueFrom:
            fieldRef:
              apiVersion: v1
              fieldPath: metadata.name
        image: touk/nussknacker-lite-kafka-runtime:1.3.0 # filled with dockerImageName/dockerImageTag 
        livenessProbe:
          httpGet:
            path: /alive
            port: 8558
            scheme: HTTP
        name: runtime
        readinessProbe:
          failureThreshold: 60
          httpGet:
            path: /ready
            port: 8558
            scheme: HTTP
          periodSeconds: 1
        volumeMounts:
        - mountPath: /data
          name: configmap
      volumes:
      - configMap:
          defaultMode: 420
          name: scenario-7-detectlargetransactions-ad0834f298
        name: configmap

```
You can customize it adding e.g. own volumes, deployment strategy etc. with `k8sDeploymentConfig` settings,
e.g. add additional custom label environment variable to the container, add custom sidecar container:
```conf
spec {
 metadata: {
    labels: {
      myCustomLabel: addMeToDeployment
    }
 }
 containers: [
    {
      #`runtime` is default container executing scenario 
      name: runtime
      env: [
        CUSTOM_VAR: CUSTOM_VALUE
      ]
    },
    {
      name: sidecar-log-collector
      image: sidecar-log-collector:latest
      command: [ "command-to-upload", "/remote/path/of/flink-logs/" ]
    }
 ] 
}
```
This config will be merged into final deployment. 
Please note that you cannot override names or labels configured by Nussknacker. 
                              
### Overriding configuration passed to runtime. 
                                 
By default, configuration of Streaming-Lite runtime consists of
- `application.conf` from runtime image - see [this](https://github.com/TouK/nussknacker/blob/staging/engine/lite/kafka/runtime/src/universal/conf/application.conf) for default.
- the configuration from `modelConfig` 

In some circumstances you want to change values in `modelConfig` without having to modify base image. E.g. different accounts/credentials should be used in Designer and in Runtime. For those cases you can use `configExecutionOverrides` setting:
```
deploymentConfig {     
  configExecutionOverrides {
    password: "sfd2323afdf" # this will be used in the Runtime
  }
}
modelConfig {
  password: "aaqwmpor909232" # this will be used in the Designer
}
```

### Configuring replicas count

Each scenario has its own, configured parallelism. It describes how many worker threads,
across all replicas, should be used to process events. With `scalingConfig` one can affect replicas count 
(each replica receives the same number of worker threads).
Following options are possible:
- ```{ fixedReplicasCount: x }```  
- ```{ tasksPerReplica: y }```
- by default `tasksPerReplica: 4`

Please note that:
- it's not possible to set both config options at the same time
- due to rounding, exact workers count may be different from parallelism 
  (e.g. for fixedReplicasCount = 3, parallelism = 5, there will be 2 tasks per replica, total workers = 6)  


### Nussknacker instance name
Value of `nussknackerInstanceName` will be passed to scenario runtime pods as a `nussknacker.io/nussknackerInstanceName` Kubernetes label.
In a standard scenario, its value is taken from Nussknacker's pod `app.kubernetes.io/instance` label which, when installed
using helm should be set to [helm release name](https://helm.sh/docs/chart_best_practices/labels/#standard-labels).

It can be used to identify scenario deployments and its resources bound to a specific Nussknacker helm release.

### Configuring runtime logging
With `logbackConfigPath` you can provide path to your own logback config file, which will be used by runtime containers. This configuration is optional, if skipped default logging configuration will be used. 
Please mind, that apart whether you will provide your own logging configuration or use default, you can still modify it in runtime (for each scenario deployment separately) as described [here](../operations_guide/Lite#logging-level)

## Request-Response embedded

`request-response-embedded` Deployment Manager has the following configuration options:

| Parameter                 | Type    | Default value | Description                                              |
|---------------------------|---------|---------------|----------------------------------------------------------|
| interface                 | string  | 0.0.0.0       | Interface on which REST API of scenarios will be exposed |
| port                      | int     | 8181          | Port on which REST API of scenarios will be exposed      | 

## Streaming-Flink 

`flinkStreaming` Deployment Manager has the following configuration options:

| Parameter                 | Type     | Default value | Description                                                                                                                                                                                                                                                                                                          |
| ---------                 | ----     | ------------- | ------------                                                                                                                                                                                                                                                                                                         |
| restUrl                   | string   |               | The only required parameter, REST API endpoint of the Flink cluster                                                                                                                                                                                                                                                  |
| jobManagerTimeout         | duration | 1 minute      | Timeout for communication with FLink cluster. Consider extending if e.g. you have long savepoint times etc.                                                                                                                                                                                                          |
| shouldVerifyBeforeDeploy  | boolean  | true          | By default, before redeployment of scenario with state from savepoint, verification of savepoint compatibility is performed. There are some cases when it can be too time consuming or not possible. Use this flag to disable it.                                                                                    |
| queryableStateProxyUrl    | string   |               | Some Nussknacker extensions require access to Flink queryable state. This should be comma separated list of `host:port` addresses of [queryable state proxies](https://ci.apache.org/projects/flink/flink-docs-stable/docs/dev/datastream/fault-tolerance/queryable_state/#proxy) of all taskmanagers in the cluster |
| shouldCheckAvailableSlots | boolean  | true          | When set to true, Nussknacker checks if there are free slots to run new job. This check should be disabled on Flink Kubernetes Native deployments, where Taskmanager is started on demand.                                                                                                                           |

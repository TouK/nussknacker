---
sidebar_position: 3
---
# Deployment Manager configuration

Configuration of Deployment Manager, which is component of the Designer that deploys scenario to given Engine (e.g. Lite or Flink). 
Type of Deployment Manager is defined with `type` parameter, e.g. for running scenarios with Flink streaming job we would configure: 
```
deploymentConfig {     
  type: "flinkStreaming"
  restUrl: "http://localhost:8081"
}
```

Look at [configuration areas](./#configuration-areas) to understand where Deployment Manager configuration should be placed in Nussknacker configuration.

## Lite engine based on Kubernetes
                                                                                
Please remember, that K8s Deployment Manager has to be run with properly configured K8s access. If you install the Designer
in K8s cluster (e.g. via Helm chart) this comes out of the box. If you want to run the Designer outside the cluster, you 
have to configure `.kube/config` properly.

Both processing modes: `streaming` and `request-response` share the majority of configuration.

`lite-k8s` Deployment Manager has the following configuration options:                 

| Parameter                 | Type                                                | Default value                       | Description                                                                              |
|---------------------------|-----------------------------------------------------|-------------------------------------|------------------------------------------------------------------------------------------|
| mode                      | string                                              |                                     | Either streaming or request-response                                                     |
| dockerImageName           | string                                              | touk/nussknacker-lite-runtime -app| Runtime image (please note that it's **not** touk/nussknacker - which is designer image) |
| dockerImageTag            | string                                              | current nussknacker version         |                                                                                          |
| scalingConfig             | {fixedReplicasCount: int} or {tasksPerReplica: int} | { tasksPerReplica: 4 }              | see [below](#configuring-replicas-count)                                                 |
| configExecutionOverrides  | config                                              | {}                                  | see [below](#overriding-configuration-passed-to-runtime)                                 |
| k8sDeploymentConfig       | config                                              | {}                                  | see [below](#customizing-k8s-deployment)                                                 |
| nussknackerInstanceName   | string                                              | {?NUSSKNACKER_INSTANCE_NAME}        | see [below](#nussknacker-instance-name)                                                  |
| logbackConfigPath         | string                                              | {}                                  | see [below](#configuring-runtime-logging)                                                |
| commonConfigMapForLogback | string                                              | {}                                  | see [below](#configuring-runtime-logging)                                                |
| servicePort               | int                                                 | 80                                  | Port of service exposed in request-response processing mode                              |
                                                 
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
          value: /opt/nussknacker/conf/application.conf,/runtime-config/runtimeConfig.conf
        - name: DEPLOYMENT_CONFIG_FILE
          value: /data/deploymentConfig.conf
        - name: LOGBACK_FILE
          value: /data/logback.xml
        - name: POD_NAME
          valueFrom:
            fieldRef:
              apiVersion: v1
              fieldPath: metadata.name
        image: touk/nussknacker-lite-runtime-app:1.3.0 # filled with dockerImageName/dockerImageTag 
        livenessProbe:
          httpGet:
            path: /alive
            port: 8080
            scheme: HTTP
        name: runtime
        readinessProbe:
          failureThreshold: 60
          httpGet:
            path: /ready
            port: 8080
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

```hocon
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

In most cases, the model configuration values passed to the Lite Engine runtime are the ones from 
the `modelConfig` section of [main configuration file](https://docs.nussknacker.io/documentation/docs/installation_configuration_guide/#configuration-areas).
However, there are two exception to this rule:
- there is [application.conf](https://github.com/TouK/nussknacker/blob/staging/engine/lite/kafka/runtime/src/universal/conf/application.conf) file in the runtime image, which is used as additional source of certain defaults.
- you can override the configuration from the main configuration file. The paragraph below describes how to use this mechanism. 

In some circumstances you want to have different configuration values used by the Designer, and different used by the runtime. 
E.g. different accounts/credentials should be used in Designer (for schema discovery, tests from file) and in Runtime (for the production use). 
For those cases you can use `configExecutionOverrides` setting:
```hocon
deploymentConfig {     
  configExecutionOverrides {
    special_password: "sfd2323afdf" # this will be used in the Runtime
  }
}
modelConfig {
  special_password: "aaqwmpor909232" # this will be used in the Designer
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
- for `request-response` processing mode only `fixedReplicasCount` is available
- due to rounding, exact workers count may be different from parallelism 
  (e.g. for fixedReplicasCount = 3, parallelism = 5, there will be 2 tasks per replica, total workers = 6)  


### Nussknacker instance name
Value of `nussknackerInstanceName` will be passed to scenario runtime pods as a `nussknacker.io/nussknackerInstanceName` Kubernetes label.
In a standard scenario, its value is taken from Nussknacker's pod `app.kubernetes.io/instance` label which, when installed
using helm should be set to [helm release name](https://helm.sh/docs/chart_best_practices/labels/#standard-labels).

It can be used to identify scenario deployments and its resources bound to a specific Nussknacker helm release.

### Configuring runtime logging
With `logbackConfigPath` you can provide path to your own logback config file, which will be used by runtime containers. This configuration is optional, if skipped default logging configuration will be used. 
Please mind, that apart whether you will provide your own logging configuration or use default, you can still modify it in runtime (for each scenario deployment separately*) as described [here](../operations_guide/Lite#logging-level)

*By default, every scenario runtime has its own separate configMap with logback configuration. By setting `commonConfigMapForLogback` you can enforce usage of single configMap (with such name as configured) with logback.xml for all your runtime containers. Take into account, that DeploymentManager relinquishes control over lifecycle of this ConfigMap (with one exception - it will create it, if not exist).

### Configuring Prometheus metrics
Just like in [Designer installation](./Installation.md#Basic environment variables), you can attach [JMX Exporter for Prometheus](https://github.com/prometheus/jmx_exporter) to your runtime pods. Pass `PROMETHEUS_METRICS_PORT` environment variable to enable agent, and simultaneously define port on which metrics will be exposed. By default, agent is configured to expose basic jvm metrics, but you can provide your own configuration file by setting `PROMETHEUS_AGENT_CONFIG_FILE` environment, which has to point to it.   

## Request-Response embedded

Deployment Manager of type `request-response-embedded` has the following configuration options:

| Parameter                                                 | Type   | Default value   | Description                                                                                                                      |
|-----------------------------------------------------------|--------|-----------------|----------------------------------------------------------------------------------------------------------------------------------|
| http.interface                                            | string | 0.0.0.0         | Interface on which REST API of scenarios will be exposed                                                                         |
| http.port                                                 | int    | 8181            | Port on which REST API of scenarios will be exposed                                                                              | 
| request-response.definitionMetadata.servers               | string | [{"url": "./"}] | Configuration of exposed servers in scenario's OpenApi definition. When not configured, will be used server with ./ relative url | 
| request-response.definitionMetadata.servers[].url         | string |                 | Url of server in scenario's OpenApi definition                                                                                   | 
| request-response.definitionMetadata.servers[].description | string |                 | (Optional) description of server in scenario's OpenApi definition                                                                | 
| request-response.security.basicAuth.user                  | string |                 | (Optional) Basic auth user                                                                                                       | 
| request-response.security.basicAuth.password              | string |                 | (Optional) Basic auth password                                                                                                   | 

## Flink engine 

Deployment Manager of type `flinkStreaming` has the following configuration options:

| Parameter                 | Type     | Default value | Description                                                                                                                                                                                                                                                                                                          |
|---------------------------|----------|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| restUrl                   | string   |               | The only required parameter, REST API endpoint of the Flink cluster                                                                                                                                                                                                                                                  |
| jobManagerTimeout         | duration | 1 minute      | Timeout for communication with FLink cluster. Consider extending if e.g. you have long savepoint times etc.                                                                                                                                                                                                          |
| shouldVerifyBeforeDeploy  | boolean  | true          | By default, before redeployment of scenario with state from savepoint, verification of savepoint compatibility is performed. There are some cases when it can be too time consuming or not possible. Use this flag to disable it.                                                                                    |
| queryableStateProxyUrl    | string   |               | Some Nussknacker extensions require access to Flink queryable state. This should be comma separated list of `host:port` addresses of [queryable state proxies](https://ci.apache.org/projects/flink/flink-docs-stable/docs/dev/datastream/fault-tolerance/queryable_state/#proxy) of all taskmanagers in the cluster |
| shouldCheckAvailableSlots | boolean  | true          | When set to true, Nussknacker checks if there are free slots to run new job. This check should be disabled on Flink Kubernetes Native deployments, where Taskmanager is started on demand.                                                                                                                           |

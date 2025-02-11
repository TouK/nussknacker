---
title: Deployment
sidebar_position: 2
---

# Scenario Deployment configuration

Deployment of a scenario onto the [Engine](../about/engines/Engines.md) is managed by the Designer's extension
called [Deployment Manager](../about/GLOSSARY.md#deployment-manager).
To enable a given [Deployment Manager](../about/GLOSSARY.md#deployment-manager) its jar package has to be placed in the
Designer's classpath. Nussknacker is distributed with three
default [Deployment Managers](../about/GLOSSARY.md#deployment-manager) (`flinkStreaming`, `lite-k8s`, `lite-embedded`).
Their jars are located in the `managers` directory.

Deployment specific configuration is provided in the `deploymentConfig` section of the configuration file -
check [configuration areas](./index.mdx#configuration-areas) to understand the structure of the configuration file.

Below is a snippet of scenario deployment configuration.

```
deploymentConfig {     
  type: "flinkStreaming"
  engineSetupName: "My Flink Cluster"
  
  # Deployment Manager's specific parameters
  restUrl: "http://localhost:8081"
}
```

Parameters:

- `type` parameter determines the type of the [Deployment Manager](../about/GLOSSARY.md#deployment-manager). Possible
  options are: `flinkStreaming`, `lite-k8s`, `lite-embedded`
- `engineSetupName` parameter is optional. It specifies how the engine will be displayed in the GUI. If not specified,
  default name will be used instead (e.g. `Flink` for `flinkStreaming` Deployment Manager).

## Kubernetes native Lite engine configuration

Please check high level [Lite engine description](../about/engines/LiteArchitecture.md#scenario-deployment) before
proceeding to configuration details.

Please note, that K8s Deployment Manager has to be run with properly configured K8s access. If you install the Designer
in K8s cluster (e.g. via Helm chart) this comes out of the box. If you want to run the Designer outside the cluster, you
have to configure `.kube/config` properly.

Except the `servicePort` configuration option, all remaining configuration options apply to
both `streaming` and `request-response` processing modes.

The table below contains configuration options for the Lite engine. If you install Designer with Helm, you can use Helm
values override mechanism to supply your own values for
these [options](https://artifacthub.io/packages/helm/touk/nussknacker#configuration-in-values-yaml). As the the result
of the Helm template rendering "classic" Nussknacker configuration file will be generated.

&nbsp;
If you install Designer outside the K8s cluster then the required changes should be applied under the `deploymentConfig`
key as any other Nussknacker non K8s configuration.

| Parameter                                            | Type                      | Default value                     | Description                                                                              |
|------------------------------------------------------|---------------------------|-----------------------------------|------------------------------------------------------------------------------------------|
| mode                                                 | string                    |                                   | Processing mode: either streaming or request-response                                    |
| dockerImageName                                      | string                    | touk/nussknacker-lite-runtime-app | Runtime image (please note that it's **not** touk/nussknacker - which is designer image) |
| dockerImageTag                                       | string                    | current nussknacker version       |                                                                                          |
| scalingConfig *(Streaming processing mode)*          | {tasksPerReplica: int}    | { tasksPerReplica: 4 }            | see [below](#configuring-replicas-count)                                                 |
| scalingConfig *(Request - Response processing mode)* | {fixedReplicasCount: int} | { fixedReplicasCount: 2 }         | see [below](#configuring-replicas-count)                                                 |
| configExecutionOverrides                             | config                    | {}                                | see [below](#overriding-configuration-passed-to-runtime)                                 |
| k8sDeploymentConfig                                  | config                    | {}                                | see [below](#customizing-k8s-deployment-resource-definition)                             |
| nussknackerInstanceName                              | string                    | {?NUSSKNACKER_INSTANCE_NAME}      | see [below](#nussknacker-instance-name)                                                  |
| logbackConfigPath                                    | string                    | {}                                | see [below](#configuring-runtime-logging)                                                |
| commonConfigMapForLogback                            | string                    | {}                                | see [below](#configuring-runtime-logging)                                                |
| ingress                                              | config                    | {enabled: false}                  | (Request-Response only) see [below](#configuring-runtime-ingress)                        |
| servicePort                                          | int                       | 80                                | (Request-Response only) Port of service exposed                                          |
| scenarioStateCaching.enabled                         | boolean                   | true                              | Enables scenario state caching in scenario list view                                     |
| scenarioStateCaching.cacheTTL                        | duration                  | 10 seconds                        | TimeToLeave for scenario state cache entries                                             |
| scenarioStateIdleTimeout                             | duration                  | 3 seconds                         | Idle timeout for fetching scenario state from K8s                                        |

### Customizing K8s deployment resource definition

By default, each scenario is deployed as the following K8s deployment:

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
      command: ["command-to-upload", "/remote/path/of/flink-logs/"]
    }
  ]
}
```

This config will be merged into the final K8s deployment resource definition.
Please note that you cannot override names or labels configured by Nussknacker.

### Overriding configuration passed to runtime.

In most cases, the Model configuration values passed to the Lite Engine runtime are the ones from
the `modelConfig` section
of [main configuration file](./index.mdx#configuration-areas).
However, there are two exception to this rule:

- there
  is [application.conf](https://github.com/TouK/nussknacker/blob/staging/engine/lite/runtime-app/src/universal/conf/application.conf)
  file in the runtime image, which is used as additional source of certain defaults.
- you can override the configuration coming from the main configuration file. The paragraph below describes how to use
  this mechanism.

In some circumstances you want to have different configuration values used by the Designer, and different used by the
runtime.
E.g. different accounts/credentials should be used in Designer (for schema discovery, tests from file) and in Runtime (
for the production use).
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

Replicas count is configured under `scalingConfig` configuration key.
&nbsp;

​In the **Request-Response** processing mode you can affect the count of scenario pods (replicas) by setting
fixedReplicasCount configuration key; its default value is 2:

`{ fixedReplicasCount: x }`.

​In the  **Streaming** processing mode the scenario parallelism is set in the scenario properties; it determines the
minimal number of tasks used to process events. The count of replicas, scenario parallelism and number of tasks per
replica are connected with a simple formula:

*scenarioParallelism = replicasCount \* tasksPerReplica*

If you do not change any settings, the number of replicas in the K8s deployment will be set to the ceiling (
scenarioParallelism / tasksPerReplica); the default value of 4 will be used for tasksPerReplica.
Alternatively, you can affect the number of replicas in the following ways:

- modify the default value of tasksPerReplica by setting { tasksPerReplica: y }; the number of replicas will be computed
  as before as ceiling (scenarioParallelism / tasksPerReplica)
- set fixedReplicasCount directly. The number of tasksPerReplica will be set to the ceiling (scenarioParallelism /
  fixedReplicaCounts. You cannot use this setting together with tasksPerReplica setting.

Due to rounding, the number of tasks may be different from scenario parallelism (e.g. for `fixedReplicasCount = 3`,
scenario parallelism = 5, there will be 2 tasks per replica, total tasks = 6)

### Nussknacker instance name

Value of `nussknackerInstanceName` will be passed to scenario runtime pods as a `nussknacker.io/nussknackerInstanceName`
Kubernetes label.
In a standard scenario, its value is taken from Nussknacker's pod `app.kubernetes.io/instance` label which, when
installed
using helm should be set to [helm release name](https://helm.sh/docs/chart_best_practices/labels/#standard-labels).

It can be used to identify scenario deployments and its resources bound to a specific Nussknacker helm release.

### Configuring runtime logging

With `logbackConfigPath` you can provide path to your own logback config file, which will be used by runtime containers.
This configuration is optional, if skipped default logging configuration will be used.
Please mind, that apart whether you will provide your own logging configuration or use default, you can still modify it
in runtime (for each scenario deployment separately*) as described [here](../operations_guide/Lite.md#logging-level)

*By default, every scenario runtime has its own separate configMap with logback configuration. By
setting `commonConfigMapForLogback` you can enforce usage of single configMap (with such name as configured) with
logback.xml for all your runtime containers. Take into account, that DeploymentManager relinquishes control over
lifecycle of this ConfigMap (with one exception - it will create it, if not exist).

### Configuring runtime ingress

In Request-Response processing mode additional ingress resource can be created when `enabled` flag is turned on.
For now only [nginx based](https://docs.nginx.com/nginx-ingress-controller/) K8s ingress controller is supported.
It can be configured with following options.

| Parameter | Type    | Default value | Description                                                                                                                   |
|-----------|---------|---------------|-------------------------------------------------------------------------------------------------------------------------------|
| enabled   | boolean | false         | Either streaming or request-response                                                                                          |
| host      | string  |               | Name of the [ingress host](https://kubernetes.io/docs/concepts/services-networking/ingress/#ingress-rules)                    |
| rootPath  | string  | "/"           | Root path for the ingress path, by default ingress path is rootPath + [slug](../scenarios_authoring/RRDataSourcesAndSinks.md) |
| config    | config  | {}            | Additional ingress config customization                                                                                       |

#### Configuring custom ingress class

By default, ingress resource will be created without any ingress class. If you want to use different class, you can set

```hocon
ingress {
  enabled: true,
  config: {
    metadata: {
      annotations: {
        "kubernetes.io/ingress.class": "ingress-className"
      }
    }
  }
}
```

### Configuring Prometheus metrics

Just like in [Designer installation](../configuration/Common.md#basic-environment-variables), you can
attach [JMX Exporter for Prometheus](https://github.com/prometheus/jmx_exporter) to your runtime pods.
Pass `PROMETHEUS_METRICS_PORT` environment variable to enable agent, and simultaneously define port on which metrics
will be exposed. By default, agent is configured to expose basic jvm metrics, but you can provide your own configuration
file by setting `PROMETHEUS_AGENT_CONFIG_FILE` environment, which has to point to it.

## Embedded Lite engine

Deployment Manager of type `lite-embedded` has the following configuration options:

| Parameter                                                 | Type   | Default value   | Description                                                                                                                                              |
|-----------------------------------------------------------|--------|-----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| mode                                                      | string |                 | Processing mode: either  streaming-lite or request-response                                                                                              |
| http.interface                                            | string | 0.0.0.0         | (Request-Response only) Interface on which REST API of scenarios will be exposed                                                                         |
| http.port                                                 | int    | 8181            | (Request-Response only) Port on which REST API of scenarios will be exposed                                                                              | 
| request-response.definitionMetadata.servers               | string | [{"url": "./"}] | (Request-Response only) Configuration of exposed servers in scenario's OpenAPI definition. When not configured, will be used server with ./ relative url | 
| request-response.definitionMetadata.servers[].url         | string |                 | (Request-Response only) Url of server in scenario's OpenAPI definition                                                                                   | 
| request-response.definitionMetadata.servers[].description | string |                 | (Request-Response only) (Optional) description of server in scenario's OpenAPI definition                                                                | 
| request-response.security.basicAuth.user                  | string |                 | (Request-Response only) (Optional) Basic auth user                                                                                                       | 
| request-response.security.basicAuth.password              | string |                 | (Request-Response only) (Optional) Basic auth password                                                                                                   | 

## Flink engine

Deployment Manager of type `flinkStreaming` has the following configuration options:

| Parameter                                            | Type           | Default value | Description                                                                                                                                                                                                                                 |
|------------------------------------------------------|----------------|---------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| restUrl                                              | string         |               | REST API endpoint of the Flink cluster. This parameter is required if `useMiniClusterForDeployment` is `false` (default value)                                                                                                              |
| useMiniClusterForDeployment                          | boolean        | false         | Uses Flink MiniCluster configured in `miniCluster` instead of remote Flink for scenario deployment. This option doesn't provide High Availability so it shouldn't be used on the production environment                                     |
| jobManagerTimeout                                    | duration       | 1 minute      | Timeout for communication with FLink cluster. Consider extending if e.g. you have long savepoint times etc.                                                                                                                                 |
| shouldCheckAvailableSlots                            | boolean        | true          | When set to true, Nussknacker checks if there are free slots to run new job. This check should be disabled on Flink Kubernetes Native deployments, where Taskmanager is started on demand.                                                  |
| waitForDuringDeployFinish.enabled                    | boolean        | true          | When set to true, after Flink job execution, we check if tasks were successfully started on TaskMangers, before marking version as deployed. Otherwise version is marked as deployed immediately after successful response from JobManager. |
| waitForDuringDeployFinish.maxChecks                  | boolean        | 180           | It works when `waitForDuringDeployFinish.enabled` option is set to `true`. This parameter describe how many times we should check if tasks were successfully started on TaskMangers before notifying about deployment failure.              |
| waitForDuringDeployFinish.delay                      | boolean        | 1 second      | It works when `waitForDuringDeployFinish.enabled` option is set to `true`. This parameter describe how long should be delay between checks.                                                                                                 |
| scenarioStateCaching.enabled                         | boolean        | true          | Enables scenario state caching in scenario list view                                                                                                                                                                                        |
| scenarioStateCaching.cacheTTL                        | duration       | 10 seconds    | TimeToLeave for scenario state cache entries                                                                                                                                                                                                |
| scenarioStateRequestTimeout                          | duration       | 3 seconds     | Request timeout for fetching scenario state from Flink                                                                                                                                                                                      |
| jobConfigsCacheSize                                  | int            | 1000          | Maximum number of cached job configuration elements.                                                                                                                                                                                        |
| miniCluster.config                                   | map of strings | [:]           | Configuration that will be passed to shared `MiniCluster`                                                                                                                                                                                   |
| miniCluster.streamExecutionEnvConfig                 | map of strings | [:]           | Configuration that will be passed to shared `StreamExecutionEnvironment` used along with  `MiniCluster`                                                                                                                                     |                                                                                                                                                               |
| miniCluster.waitForJobManagerRestAPIAvailableTimeout | duration       | 10 seconds    | How long Nussknacker should wait fo Flink Mini Cluster REST endpoint. It is only used when `useMiniClusterForDeployment` is enabled                                                                                                         |                                                                                                                                                               |
| scenarioTesting.reuseSharedMiniCluster               | boolean        | true          | Reuses shared mini cluster for each scenario testing attempt                                                                                                                                                                                |
| scenarioTesting.timeout                              | duration       | 55 seconds    | Timeout for scenario testing. When scenario test is not finished during this time, testing job will be canceled. This property should be configured along with `akka.http.server.request-timeout` for proper effect.                        |
| scenarioTesting.parallelism                          | int            | 1             | Parallelism that will be used for scenario testing job                                                                                                                                                                                      |
| scenarioStateVerification.enabled                    | boolean        | true          | By default, before redeployment of scenario with state from savepoint, verification of savepoint compatibility is performed. There are some cases when it can be too time consuming or not possible. Use this flag to disable it.           |
| scenarioStateVerification.reuseSharedMiniCluster     | boolean        | true          | Reuses shared mini cluster for each scenario state verification                                                                                                                                                                             |
| scenarioStateVerification.timeout                    | duration       | 55 seconds    | Timeout for scenario state verification. When scenario test is not finished during this time, testing job will be canceled. This property should be configured along with `akka.http.server.request-timeout` for proper effect.             |

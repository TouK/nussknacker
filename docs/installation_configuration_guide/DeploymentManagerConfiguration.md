---
sidebar_position: 3
---

# Deployment Manager configuration

Deployment Manager deploys scenarios from the Designer to the engine on which scenarios are processed.
Check [configuration areas](./#configuration-areas) to understand where Deployment Manager configuration should be
placed in Nussknacker configuration.

Below you can find a snippet of Deployment Manager configuration.

```
deploymentConfig {     
  type: "flinkStreaming"
  restUrl: "http://localhost:8081"
  
  # additional configuration goes here
}
```

`type` parameter determines engine to which the scenario is deployed.The `type` parameter is set in both the minimal
working configuration file (Docker image and binary distribution) and Helm chart - you will not need to set it on your
own.

## Kubernetes native Lite engine configuration

Please note, that K8s Deployment Manager has to be run with properly configured K8s access. If you install the Designer
in K8s cluster (e.g. via Helm chart) this comes out of the box. If you want to run the Designer outside the cluster, you
have to configure `.kube/config` properly.

Except the `servicePort` configuration option, all remaining configuration options apply to
both `streaming` and `request-response` processing modes.

The table below contains configuration options for the Lite engine. If you install Designer with Helm, you can customize
the Helm chart to use different values for
those [options](https://artifacthub.io/packages/helm/touk/nussknacker#configuration-in-values-yaml). If you install
Designer outside of the K8s cluster then the required changes should be applied under the `deploymentConfig` key as any
other Nussknacker non K8s configuration.

| Parameter                 | Type                                                | Default value                     | Description                                                                              |
|---------------------------|-----------------------------------------------------|-----------------------------------|------------------------------------------------------------------------------------------|
| mode                      | string                                              |                                   | Either streaming or request-response                                                     |
| dockerImageName           | string                                              | touk/nussknacker-lite-runtime-app | Runtime image (please note that it's **not** touk/nussknacker - which is designer image) |
| dockerImageTag            | string                                              | current nussknacker version       |                                                                                          |
| scalingConfig             | {fixedReplicasCount: int} or {tasksPerReplica: int} | { tasksPerReplica: 4 }            | see [below](#configuring-replicas-count)                                                 |
| configExecutionOverrides  | config                                              | {}                                | see [below](#overriding-configuration-passed-to-runtime)                                 |
| k8sDeploymentConfig       | config                                              | {}                                | see [below](#customizing-k8s-deployment-resource-definition)                             |
| nussknackerInstanceName   | string                                              | {?NUSSKNACKER_INSTANCE_NAME}      | see [below](#nussknacker-instance-name)                                                  |
| logbackConfigPath         | string                                              | {}                                | see [below](#configuring-runtime-logging)                                                |
| commonConfigMapForLogback | string                                              | {}                                | see [below](#configuring-runtime-logging)                                                |
| ingress                   | config                                              | {enabled: false}                  | (Request-Response only) see [below](#configuring-runtime-ingress)                        |
| servicePort               | int                                                 | 80                                | (Request-Response only) Port of service exposed                                          |

### Customizing K8s deployment resource definition

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
      command: ["command-to-upload", "/remote/path/of/flink-logs/"]
    }
  ]
}
```

This config will be merged into the final K8s deployment resource definition.
Please note that you cannot override names or labels configured by Nussknacker.

### Overriding configuration passed to runtime.

In most cases, the model configuration values passed to the Lite Engine runtime are the ones from
the `modelConfig` section
of [main configuration file](https://docs.nussknacker.io/documentation/docs/installation_configuration_guide/#configuration-areas).
However, there are two exception to this rule:

- there
  is [application.conf](https://github.com/TouK/nussknacker/blob/staging/engine/lite/kafka/runtime/src/universal/conf/application.conf)
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

​In the **Request-Response** processing mode you can affect the count of scenario pods (replicas) by setting
fixedReplicasCount configuration key; its default value is 2:

`{ fixedReplicasCount: x }`.

​In the  **Streaming** processing mode the scenario parallelism is set in the scenario properties; it determines the
minimal number of tasks used to process events. The count of replicas, scenario parallelism and number of tasks per
replica are connected with a simple formula:

*scenarioParallelism = replicasCount * tasksPerReplica*

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
in runtime (for each scenario deployment separately*) as described [here](../operations_guide/Lite#logging-level)

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

### Configuring Prometheus metrics

Just like in [Designer installation](./Installation.md#Basic environment variables), you can
attach [JMX Exporter for Prometheus](https://github.com/prometheus/jmx_exporter) to your runtime pods.
Pass `PROMETHEUS_METRICS_PORT` environment variable to enable agent, and simultaneously define port on which metrics
will be exposed. By default, agent is configured to expose basic jvm metrics, but you can provide your own configuration
file by setting `PROMETHEUS_AGENT_CONFIG_FILE` environment, which has to point to it.

## Embedded Lite engine

Deployment Manager of type `lite-embedded` has the following configuration options:

| Parameter                                                 | Type   | Default value   | Description                                                                                                                                              |
|-----------------------------------------------------------|--------|-----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| mode                                                      | string |                 | Either streaming or request-response                                                                                                                     |
| http.interface                                            | string | 0.0.0.0         | (Request-Response only) Interface on which REST API of scenarios will be exposed                                                                         |
| http.port                                                 | int    | 8181            | (Request-Response only) Port on which REST API of scenarios will be exposed                                                                              | 
| request-response.definitionMetadata.servers               | string | [{"url": "./"}] | (Request-Response only) Configuration of exposed servers in scenario's OpenAPI definition. When not configured, will be used server with ./ relative url | 
| request-response.definitionMetadata.servers[].url         | string |                 | (Request-Response only) Url of server in scenario's OpenAPI definition                                                                                   | 
| request-response.definitionMetadata.servers[].description | string |                 | (Request-Response only) (Optional) description of server in scenario's OpenAPI definition                                                                | 
| request-response.security.basicAuth.user                  | string |                 | (Request-Response only) (Optional) Basic auth user                                                                                                       | 
| request-response.security.basicAuth.password              | string |                 | (Request-Response only) (Optional) Basic auth password                                                                                                   | 

## Flink engine

Deployment Manager of type `flinkStreaming` has the following configuration options:

| Parameter                 | Type     | Default value | Description                                                                                                                                                                                                                       |
|---------------------------|----------|---------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| restUrl                   | string   |               | The only required parameter, REST API endpoint of the Flink cluster                                                                                                                                                               |
| jobManagerTimeout         | duration | 1 minute      | Timeout for communication with FLink cluster. Consider extending if e.g. you have long savepoint times etc.                                                                                                                       |
| shouldVerifyBeforeDeploy  | boolean  | true          | By default, before redeployment of scenario with state from savepoint, verification of savepoint compatibility is performed. There are some cases when it can be too time consuming or not possible. Use this flag to disable it. |
| shouldCheckAvailableSlots | boolean  | true          | When set to true, Nussknacker checks if there are free slots to run new job. This check should be disabled on Flink Kubernetes Native deployments, where Taskmanager is started on demand.                                        |

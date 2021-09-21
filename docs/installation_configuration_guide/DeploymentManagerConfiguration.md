---
sidebar_position: 3
---
# Deployment manager configuration

Configuration of Deployment Manager, which is responsible for communication with scenario Executor (e.g. FLink). 
Type of Deployment Manager is defined with `type` parameter, e.g. for running scenarios with Flink streaming job we would configure: 
```
deploymentConfig {     
  type: "flinkStreaming"
  restUrl: "http://localhost:8081"
}
```

`flinkStreaming` Deployment Manager has following configuration options:

| Parameter | Type | Default value | Description  |
| --------- | ---- | ------------- | ------------ |
| restUrl   | string |             | The only required parameter, REST API endpoint of the Flink cluster |       
| jobManagerTimeout | duration | 1 minute | Timeout for communication with FLink cluster. Consider extending if e.g. you have long savepoint times etc. |
| shouldVerifyBeforeDeploy | boolean | true | By default, before redeployment of scenario with state from savepoint, verification of savepoint compatibility is performed. There are some cases when it can be too time consuming or not possible. Use this flag to disable it. | 
| queryableStateProxyUrl | string | | Some Nussknacker extensions require access to Flink queryable state. This should be comma separated list of `host:port` addresses of [queryable state proxies](https://ci.apache.org/projects/flink/flink-docs-stable/docs/dev/datastream/fault-tolerance/queryable_state/#proxy) of all taskmanagers in the cluster |                                                                                           



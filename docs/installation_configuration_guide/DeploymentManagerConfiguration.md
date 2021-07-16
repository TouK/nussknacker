# Deployment manager configuration

Configuration of communication with processing engine. Below we present Flink engine configuration as an example:

```
engingConfig {     
  type: "flinkStreaming"
  restUrl: "http://localhost:8081"
  parallelism: 4
  jobManagerTimeout: 1m
}
```
In this section you can put all configuration values for Flink client. We are using Flink REST API so the only
required parameter is `restUrl` - which defines location of Flink JobManager
* `jobManagerTimeout` (e.g. 1m) - timeout used in communication with Flink cluster

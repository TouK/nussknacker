---
sidebar_label: "Lite"
---

# Streaming-Lite specific model configuration
                 
## Common configuration

| Name                  | Importance | Type       | Default value | Description     |
|-----------------------|------------|------------|---------------|-----------------|
| pollDuration          | Low        | duration   | 100ms         | [Poll duration](https://kafka.apache.org/30/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html#poll(java.time.Duration)) of Kafka consumer             | 
| shutdownTimeout       | Low        | duration   | 10s           | How long to wait for graceful shutdown |
| interpreterTimeout    | Low        | duration   | 10s           | Timeout of invocation of scenario (including enrichers) for events consumed in one poll  |
| publishTimeout        | Low        | duration   | 10s           | Timeout on producing resulting event to Kafka |
| waitAfterFailureDelay | Low        | duration   | 10s           | Processing delay after unexpected, transient error (does not include e.g. expression errors or 500 codes from OpenAPI) |

## Kafka configuration

Currently, it's only possible to use one Kafka cluster for one model configuration. This configuration is used for all
Kafka based sources and sinks. See [common config](../ModelConfiguration#kafka-connection-configuration) for the details.
```
modelConfig {
  kafka {
    kafkaAddress: "broker1:9092,broker2:9092"
    kafkaProperties {
      "schema.registry.url": "http://schemaregistry:8081"
    }
  }
}  
```
      

### Exception handling

Errors are sent to Kafka, to a dedicated topic: 
```
modelConfig {
  exceptionHandlingConfig: {
    topic: "errors"
    createTopicIfNotExists: "true"
  }
}
```
please look at [common cofiguration](../ModelConfiguration#kafka-exception-handling) for the details of the configuration.
                                  

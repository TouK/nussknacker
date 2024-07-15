---
sidebar_label: "Lite"
---

# Lite Model configuration

## Streaming processing mode

### Configuration

| Name                     | Importance | Type     | Default value                                 | Description                                                                                                                                          |
|--------------------------|------------|----------|-----------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| kafkaTransactionsEnabled | High       | boolean  | true (in general), false for Azure Event Hubs | Define if records should be processed in transaction (exactly-once semantics) or not (at-least-once semantics).                                      |
| pollDuration             | Low        | duration | 100ms                                         | [Poll duration](https://kafka.apache.org/30/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html#poll(java.time.Duration)) of Kafka consumer | 
| shutdownTimeout          | Low        | duration | 10s                                           | How long to wait for graceful shutdown                                                                                                               |
| interpreterTimeout       | Low        | duration | 10s                                           | Timeout of invocation of scenario (including enrichers) for events consumed in one poll                                                              |
| publishTimeout           | Low        | duration | 5s                                            | Timeout on producing resulting event to Kafka                                                                                                        |
| waitAfterFailureDelay    | Low        | duration | 10s                                           | Processing delay after unexpected, transient error (does not include e.g. expression errors or 500 codes from OpenAPI)                               |

### Exception handling

 Errors are sent to Kafka, to a dedicated topic: 
 ```
 modelConfig {
   exceptionHandlingConfig: {
     topic: "errors"
   }
 }
 ```
 please look at [common cofiguration](../../integration/KafkaIntegration.md/#exception-handling) for the details of the configuration.

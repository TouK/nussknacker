package pl.touk.nussknacker.engine.schemedkafka.source.flink

import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.schemedkafka.helpers.SimpleKafkaJsonSerializer
import pl.touk.nussknacker.engine.schemedkafka.schema._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.ExistingSchemaVersion

class DelayedUniversalKafkaSourceJsonPayloadIntegrationSpec extends DelayedUniversalKafkaSourceIntegrationMixinSpec {

  override protected def keySerializer: Serializer[Any] = SimpleKafkaJsonSerializer

  override protected def valueSerializer: Serializer[Any] = SimpleKafkaJsonSerializer

  test("properly process data using kafka-generic-delayed source") {
    val inputTopic = "simple-topic-with-long-field-input"
    registerJsonSchema(inputTopic, LongFieldV1.jsonSchema, isKey = false)
    val process = createProcessWithDelayedSource(inputTopic, ExistingSchemaVersion(1), "'field'", "1L")
    runAndVerify(inputTopic, process, LongFieldV1.exampleData)
  }

  test("handle timestamp field in Int format") {
    val inputTopic = "simple-topic-with-int-field-input"
    registerJsonSchema(inputTopic, IntFieldV1.jsonSchema, isKey = false)
    val process = createProcessWithDelayedSource(inputTopic, ExistingSchemaVersion(1), "'field'", "1L")
    runAndVerify(inputTopic, process, IntFieldV1.exampleData)
  }

  test("handle timestamp field in ZonedDateTime format") {
    val inputTopic = "simple-topic-with-zoned-date-time-field-input"
    registerJsonSchema(inputTopic, ZoneDateTimeFieldJsonV1.jsonSchema, isKey = false)
    val process = createProcessWithDelayedSource(inputTopic, ExistingSchemaVersion(1), "'field'", "1L")
    runAndVerify(inputTopic, process, ZoneDateTimeFieldJsonV1.exampleData)
  }

  test("handle timestamp field in OffsetDateTime format") {
    val inputTopic = "simple-topic-with-offset-date-time-field-input"
    registerJsonSchema(inputTopic, OffsetDateTimeFieldJsonV1.jsonSchema, isKey = false)
    val process = createProcessWithDelayedSource(inputTopic, ExistingSchemaVersion(1), "'field'", "1L")
    runAndVerify(inputTopic, process, OffsetDateTimeFieldJsonV1.exampleData)
  }

}

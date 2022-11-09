package pl.touk.nussknacker.engine.schemedkafka.source.flink

import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.schemedkafka.helpers.SimpleKafkaJsonSerializer
import pl.touk.nussknacker.engine.schemedkafka.schema.{LongFieldV1, NullableLongFieldV1}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.ExistingSchemaVersion

class DelayedUniversalKafkaSourceJsonPayloadIntegrationSpec extends DelayedUniversalKafkaSourceIntegrationMixinSpec  {

  override protected def keySerializer: Serializer[Any] = SimpleKafkaJsonSerializer

  override protected def valueSerializer: Serializer[Any] = SimpleKafkaJsonSerializer

  test("properly process data using kafka-generic-delayed source") {
    val inputTopic = "simple-topic-with-long-field-input"
    registerJsonSchema(inputTopic, LongFieldV1.jsonSchema, isKey = false)
    val process = createProcessWithDelayedSource(inputTopic, ExistingSchemaVersion(1), "'field'", "1L")
    runAndVerify(inputTopic, process, LongFieldV1.exampleData)
  }

}

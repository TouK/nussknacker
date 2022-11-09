package pl.touk.nussknacker.engine.schemedkafka.source.flink

import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.schemedkafka.schema.{LongFieldV1, NullableLongFieldV1}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.ExistingSchemaVersion

class DelayedUniversalKafkaSourceAvroPayloadIntegrationSpec extends DelayedUniversalKafkaSourceIntegrationMixinSpec  {

  test("properly process data using kafka-generic-delayed source") {
    val topicConfig = createAndRegisterTopicConfig("simple-topic-with-long-field", LongFieldV1.schema)
    val process = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "'field'", "1L")
    runAndVerify(topicConfig, process, LongFieldV1.record)
  }

  test("properly process data delaying by nullable field") {
    val topicConfig = createAndRegisterTopicConfig("simple-topic-with-nullable-long-field", NullableLongFieldV1.schema)
    val process = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "'field'", "1L")
    runAndVerify(topicConfig, process, NullableLongFieldV1.encodeData(timestamp = None))
  }

  test("timestampField and delay param are null") {
    val topicConfig = createAndRegisterTopicConfig("simple-topic-with-null-params", LongFieldV1.schema)
    val process = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "null", "null")
    runAndVerify(topicConfig, process, LongFieldV1.record)
  }

  test("handle not exist timestamp field param") {
    val topicConfig = createAndRegisterTopicConfig("simple-topic-with-unknown-field", LongFieldV1.schema)
    val process = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "'unknownField'", "null")
    intercept[IllegalArgumentException] {
      runAndVerify(topicConfig, process, LongFieldV1.record)
    }.getMessage should include ("Field: 'unknownField' doesn't exist in definition: field.")
  }

  test("handle invalid negative param") {
    val topicConfig = createAndRegisterTopicConfig("simple-topic-with-negative-delay", LongFieldV1.schema)
    val process = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "null", "-10L")
    intercept[IllegalArgumentException] {
      runAndVerify(topicConfig, process, LongFieldV1.record)
    }.getMessage should include ("LowerThanRequiredParameter(This field value has to be a number greater than or equal to 0,Please fill field with proper number,delayInMillis,start)")
  }

  private def runAndVerify(topicConfig: TopicConfig, process: CanonicalProcess, givenObj: AnyRef): Unit = {
    runAndVerify(topicConfig.input, process, givenObj)
  }

}

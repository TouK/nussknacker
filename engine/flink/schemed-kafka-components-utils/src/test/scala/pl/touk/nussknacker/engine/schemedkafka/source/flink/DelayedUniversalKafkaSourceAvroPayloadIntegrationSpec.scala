package pl.touk.nussknacker.engine.schemedkafka.source.flink

import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder
import pl.touk.nussknacker.engine.schemedkafka.schema._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.ExistingSchemaVersion

class DelayedUniversalKafkaSourceAvroPayloadIntegrationSpec extends DelayedUniversalKafkaSourceIntegrationMixinSpec {

  test("properly process data using kafka-generic-delayed source") {
    val topicConfig = createAndRegisterTopicConfig("simple-topic-with-long-field", LongFieldV1.schema)
    val process     = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "'field'", "1L")
    runAndVerify(topicConfig, process, LongFieldV1.record)
  }

  test("properly process data delaying by null nullable field") {
    val topicConfig =
      createAndRegisterTopicConfig("simple-topic-with-null-nullable-long-field", NullableLongFieldV1.schema)
    val process = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "'field'", "1L")
    runAndVerify(topicConfig, process, NullableLongFieldV1.encodeData(timestamp = None))
  }

  test("properly process data delaying by non-null nullable field") {
    val topicConfig =
      createAndRegisterTopicConfig("simple-topic-with-non-null-nullable-long-field", NullableLongFieldV1.schema)
    val process = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "'field'", "1L")
    runAndVerify(topicConfig, process, NullableLongFieldV1.encodeData(timestamp = Some(10000)))
  }

  test("timestampField and delay param are null") {
    val topicConfig = createAndRegisterTopicConfig("simple-topic-with-null-params", LongFieldV1.schema)
    val process     = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "null", "null")
    runAndVerify(topicConfig, process, LongFieldV1.record)
  }

  test("handle not exist timestamp field param") {
    val topicConfig = createAndRegisterTopicConfig("simple-topic-with-unknown-field", LongFieldV1.schema)
    val process = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "'unknownField'", "null")
    intercept[IllegalArgumentException] {
      runAndVerify(topicConfig, process, LongFieldV1.record)
    }.getMessage should include("Field: 'unknownField' doesn't exist in definition: field.")
  }

  test("handle timestamp field in Int format") {
    val topicConfig = createAndRegisterTopicConfig("simple-topic-with-int-field", IntFieldV1.schema)
    val process     = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "'field'", "123L")
    runAndVerify(topicConfig, process, IntFieldV1.record)
  }

  test("handle timestamp field in TimestampMillis format") {
    val topicConfig =
      createAndRegisterTopicConfig("simple-topic-with-timestamp-millis-field", TimestampMillisFieldV1.schema)
    val process = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "'field'", "123L")
    runAndVerify(topicConfig, process, TimestampMillisFieldV1.record)
  }

  test("handle timestamp field in LocalTimestampMillis format") {
    val topicConfig =
      createAndRegisterTopicConfig("simple-topic-with-local-timestamp-millis-field", LocalTimestampMillisFieldV1.schema)
    val process = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "'field'", "123L")
    runAndVerify(topicConfig, process, LocalTimestampMillisFieldV1.record)
  }

  test("handle timestamp field in TimestampMicros format") {
    val topicConfig =
      createAndRegisterTopicConfig("simple-topic-with-timestamp-micros-field", TimestampMicrosFieldV1.schema)
    val process = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "'field'", "123L")
    runAndVerify(topicConfig, process, TimestampMicrosFieldV1.record)
  }

  test("handle timestamp field in LocalTimestampMicros format") {
    val topicConfig =
      createAndRegisterTopicConfig("simple-topic-with-local-timestamp-micros-field", LocalTimestampMicrosFieldV1.schema)
    val process = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "'field'", "123L")
    runAndVerify(topicConfig, process, LocalTimestampMicrosFieldV1.record)
  }

  test("handle invalid negative param") {
    val topicConfig = createAndRegisterTopicConfig("simple-topic-with-negative-delay", LongFieldV1.schema)
    val process     = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "null", "-10L")
    intercept[IllegalArgumentException] {
      runAndVerify(topicConfig, process, LongFieldV1.record)
    }.getMessage should include(
      "LowerThanRequiredParameter(This field value has to be a number greater than or equal to 0,Please fill field with proper number,delayInMillis,start)"
    )
  }

  private def runAndVerify(topicConfig: TopicConfig, process: CanonicalProcess, givenObj: AnyRef): Unit = {
    runAndVerify(topicConfig.input, process, givenObj)
  }

  override protected val sinkForLongsResultsHolder: () => TestResultsHolder[java.lang.Long] =
    () => DelayedUniversalKafkaSourceAvroPayloadIntegrationSpec.sinkForLongsResultsHolder

  override protected val sinkForInputMetaResultsHolder: () => TestResultsHolder[InputMeta[_]] =
    () => DelayedUniversalKafkaSourceAvroPayloadIntegrationSpec.sinkForInputMetaResultsHolder
}

object DelayedUniversalKafkaSourceAvroPayloadIntegrationSpec extends Serializable {

  private val sinkForLongsResultsHolder     = new TestResultsHolder[java.lang.Long]
  private val sinkForInputMetaResultsHolder = new TestResultsHolder[InputMeta[_]]

}

package pl.touk.nussknacker.engine.schemedkafka.source.flink

import org.apache.avro.Schema
import org.scalatest.LoneElement
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition.{
  DualParameterEditor,
  FixedExpressionValue,
  FixedValuesParameterEditor,
  StringParameterEditor
}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.FragmentResolver
import pl.touk.nussknacker.engine.compile.nodecompilation.{NodeDataValidator, ValidationPerformed}
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder
import pl.touk.nussknacker.engine.schemedkafka.schema._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.ExistingSchemaVersion

class DelayedUniversalKafkaSourceAvroPayloadIntegrationSpec
    extends DelayedUniversalKafkaSourceIntegrationMixinSpec
    with LoneElement {

  test("timestampField editor should be set to simple if schema does not contain eligible fields") {
    val timestampFieldParameter =
      prepareTestForTimestampField("simple-topic-without-timestamp-fields", FullNameV1.schema)

    timestampFieldParameter.editor shouldBe Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW))
  }

  test("timestampField editor should contain long field") {
    val timestampFieldParameter =
      prepareTestForTimestampField("simple-topic-with-single-timestamp-fields", LongFieldV1.schema)

    timestampFieldParameter.editor shouldBe Some(
      DualParameterEditor(
        FixedValuesParameterEditor(
          List(
            FixedExpressionValue("", ""),
            FixedExpressionValue("'field'", "field")
          )
        ),
        DualEditorMode.SIMPLE
      )
    )
  }

  test("timestampField editor should contain all eligible fields") {
    val timestampFieldParameter =
      prepareTestForTimestampField("simple-topic-with-multiple-timestamp-fields", PaymentDate.schema)

    timestampFieldParameter.editor shouldBe Some(
      DualParameterEditor(
        FixedValuesParameterEditor(
          List(
            FixedExpressionValue("", ""),
            FixedExpressionValue("'dateTime'", "dateTime"),
            FixedExpressionValue("'vat'", "vat")
          )
        ),
        DualEditorMode.SIMPLE
      )
    )
  }

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

  test("raise an error when timestamp field set to string field") {
    val topicConfig = createAndRegisterTopicConfig("simple-topic-with-Fullname-schema", FullNameV1.schema)
    val process     = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "'first'", "123L")
    intercept[IllegalArgumentException] {
      runAndVerify(topicConfig, process, FullNameV1.record)
    }.getMessage should include("'first' has invalid type: String.")
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
      "LowerThanRequiredParameter(This field value has to be a number greater than or equal to 0,Please fill field with proper number,ParameterName(delayInMillis),start)"
    )
  }

  private def prepareTestForTimestampField(topicName: String, schema: Schema) = {
    val topicConfig   = createAndRegisterTopicConfig(topicName, schema)
    val process       = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "'field'", "1L")
    val nodeValidator = new NodeDataValidator(modelData)

    val result = nodeValidator.validate(
      process.nodes.head.data,
      ValidationContext(),
      Map.empty,
      List.empty,
      FragmentResolver(_ => None)
    )(process.metaData)

    result
      .asInstanceOf[ValidationPerformed]
      .parameters
      .getOrElse(Nil)
      .filter(_.name.value == "timestampField")
      .loneElement
  }

  private def runAndVerify(topicConfig: TopicConfig, process: CanonicalProcess, givenObj: AnyRef): Unit = {
    runAndVerify(topicConfig.input, process, givenObj)
  }

  override protected val sinkForLongsResultsHolder: () => TestResultsHolder[java.lang.Long] =
    () => DelayedUniversalKafkaSourceAvroPayloadIntegrationSpec.sinkForLongsResultsHolder

  override protected val sinkForInputMetaResultsHolder: () => TestResultsHolder[java.util.Map[String @unchecked, _]] =
    () => DelayedUniversalKafkaSourceAvroPayloadIntegrationSpec.sinkForInputMetaResultsHolder
}

object DelayedUniversalKafkaSourceAvroPayloadIntegrationSpec extends Serializable {

  private val sinkForLongsResultsHolder     = new TestResultsHolder[java.lang.Long]
  private val sinkForInputMetaResultsHolder = new TestResultsHolder[java.util.Map[String @unchecked, _]]

}

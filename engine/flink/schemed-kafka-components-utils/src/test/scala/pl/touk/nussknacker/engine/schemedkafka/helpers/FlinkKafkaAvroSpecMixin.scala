package pl.touk.nussknacker.engine.schemedkafka.helpers

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.Suite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner
import pl.touk.nussknacker.engine.graph.expression
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  ExistingSchemaVersion,
  LatestSchemaVersion,
  SchemaRegistryClientFactory,
  SchemaVersionOption
}
import pl.touk.nussknacker.test.{NuScalaTestAssertions, VeryPatientScalaFutures}

trait FlinkKafkaAvroSpecMixin
    extends FlinkSpec
    with KafkaSchemaRegistryMixin
    with Matchers
    with LazyLogging
    with NuScalaTestAssertions
    with VeryPatientScalaFutures
    with Serializable { self: Suite =>

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  override protected val kafkaComponentsConfigPrefix: String = "components.kafka.config"

  protected var testScenarioRunner: FlinkTestScenarioRunner = _

  protected def schemaRegistryClientFactory: SchemaRegistryClientFactory

  protected def createAvroProcess(
      source: SourceAvroParam,
      sink: UniversalSinkParam,
      filterExpression: Option[String] = None,
      sourceTopicParamValue: String => String = topic => s"'$topic'"
  ): CanonicalProcess = {
    val sourceParams = List(topicParamName -> sourceTopicParamValue(source.topic).spel) ++ (source match {
      case UniversalSourceParam(_, version) =>
        List(schemaVersionParamName -> formatVersionParam(version).spel)
      case UniversalSourceWithKeySupportParam(_, version) =>
        List(schemaVersionParamName -> formatVersionParam(version).spel)
    })

    val baseSinkParams: List[(String, expression.Expression)] = List(
      topicParamName.value         -> s"'${sink.topic.name}'".spel,
      schemaVersionParamName.value -> formatVersionParam(sink.versionOption).spel,
      sinkKeyParamName.value       -> sink.key.spel
    )

    val editorParams: List[(String, expression.Expression)] = List(
      sinkRawEditorParamName.value -> s"${sink.rawEditor}".spel
    )

    val validationParams: List[(String, expression.Expression)] =
      sink.validationMode.map(validation => sinkValidationModeParamName.value -> s"'${validation.name}'".spel).toList

    val builder = ScenarioBuilder
      .streaming(s"avro-test")
      .parallelism(1)
      .source(
        "start",
        source.sourceType,
        sourceParams.map { case (paramName, expr) => (paramName.value, expr) }: _*
      )

    val filteredBuilder = filterExpression
      .map(filter => builder.filter("filter", filter.spel))
      .getOrElse(builder)

    filteredBuilder
      .split(
        "split",
        GraphBuilder.emptySink(
          "end",
          "kafka",
          baseSinkParams ++ editorParams ++ validationParams ++ sink.valueParams: _*
        ),
        GraphBuilder.emptySink("outputInputMeta", "sinkForInputMeta", "Value" -> "#inputMeta".spel)
      )
  }

  protected def formatVersionParam(versionOption: SchemaVersionOption): String =
    versionOption match {
      case LatestSchemaVersion            => s"'${SchemaVersionOption.LatestOptionName}'"
      case ExistingSchemaVersion(version) => s"'$version'"
    }

  protected def runAndVerifyResultSingleEvent(
      process: CanonicalProcess,
      topic: TopicConfig,
      event: Any,
      expected: AnyRef,
      additionalVerificationBeforeScenarioCancel: => Unit = {}
  ): Unit =
    runAndVerifyResult(process, topic, List(event), List(expected), additionalVerificationBeforeScenarioCancel)

  protected def runAndVerifyResult(
      process: CanonicalProcess,
      topic: TopicConfig,
      events: List[Any],
      expected: AnyRef,
      additionalVerificationBeforeScenarioCancel: => Unit = {}
  ): Unit =
    runAndVerifyResult(process, topic, events, List(expected), additionalVerificationBeforeScenarioCancel)

  private def runAndVerifyResult(
      process: CanonicalProcess,
      topic: TopicConfig,
      events: List[Any],
      expected: List[AnyRef],
      additionalVerificationBeforeScenarioCancel: => Unit
  ): Unit = {
    kafkaClient.createTopic(topic.input.name, partitions = 1)
    events.foreach(obj => pushMessage(obj, topic.input))
    kafkaClient.createTopic(topic.output.name, partitions = 1)

    testScenarioRunner.withRunningScenario(process) { _ =>
      consumeAndVerifyMessages(topic.output, expected)
      additionalVerificationBeforeScenarioCancel
    }
  }

  sealed trait SourceAvroParam {
    def topic: String

    def sourceType: String
  }

  case class UniversalSourceParam(topic: String, versionOption: SchemaVersionOption) extends SourceAvroParam {
    override def sourceType: String = "kafka"
  }

  case class UniversalSourceWithKeySupportParam(topic: String, versionOption: SchemaVersionOption)
      extends SourceAvroParam {
    override def sourceType: String = "kafka-key-value"
  }

  object SourceAvroParam {
    def forUniversal(topicConfig: TopicConfig, versionOption: SchemaVersionOption): SourceAvroParam =
      UniversalSourceParam(topicConfig.input.name, versionOption)

    def forUniversalWithKeySchemaSupport(
        topicConfig: TopicConfig,
        versionOption: SchemaVersionOption
    ): SourceAvroParam =
      UniversalSourceWithKeySupportParam(topicConfig.input.name, versionOption)

  }

  case class UniversalSinkParam(
      topic: TopicName.ForSink,
      versionOption: SchemaVersionOption,
      valueParams: List[(String, expression.Expression)],
      key: String,
      validationMode: Option[ValidationMode],
      rawEditor: Boolean
  )

  object UniversalSinkParam {

    def apply(
        topicConfig: TopicConfig,
        version: SchemaVersionOption,
        value: String,
        key: String = "",
        validationMode: Option[ValidationMode] = Some(ValidationMode.strict)
    ): UniversalSinkParam =
      new UniversalSinkParam(
        topicConfig.output,
        version,
        (sinkValueParamName.value -> value.spel) :: Nil,
        key,
        validationMode,
        rawEditor = validationMode.isDefined
      )

    def form(
        topic: TopicName.ForSink,
        version: SchemaVersionOption,
        valueParams: List[(String, expression.Expression)],
        key: String = "",
    ): UniversalSinkParam = {
      new UniversalSinkParam(
        topic,
        version,
        valueParams,
        key,
        validationMode = None,
        rawEditor = false
      )
    }

    def nonRecordForm(
        topic: TopicName.ForSink,
        version: SchemaVersionOption,
        value: expression.Expression,
        key: String = "",
    ): UniversalSinkParam = {
      form(
        topic,
        version,
        (sinkValueParamName.value -> value) :: Nil,
        key
      )
    }

  }

}

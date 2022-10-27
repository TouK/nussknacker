package pl.touk.nussknacker.engine.schemedkafka.helpers

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.specific.SpecificRecord
import org.apache.flink.api.common.ExecutionConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, OutputVariableNameValue, TypedNodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.TestData
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceTestSupport
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.expression
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer.{ProcessSettingsPreparer, UnoptimizedSerializationPreparer}
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.kryo.AvroSerializersRegistrar
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ExistingSchemaVersion, LatestSchemaVersion, SchemaBasedSerdeProvider, SchemaVersionOption}
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory
import pl.touk.nussknacker.engine.schemedkafka.sink.flink.FlinkKafkaUniversalSinkImplFactory
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.{NussknackerAssertions, PatientScalaFutures}

import java.nio.charset.StandardCharsets
import scala.reflect.ClassTag

trait KafkaAvroSpecMixin extends AnyFunSuite with KafkaWithSchemaRegistryOperations with FlinkSpec with SchemaRegistryMixin with Matchers with LazyLogging with NussknackerAssertions with PatientScalaFutures with Serializable {

  type KafkaSource = SourceFactory with KafkaUniversalComponentTransformer[Source]

  import spel.Implicits._

  protected var registrar: FlinkProcessRegistrar = _

  protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory

  private lazy val avroPayload: SchemaBasedSerdeProvider = ConfluentSchemaBasedSerdeProvider.avroPayload(confluentClientFactory)
  private lazy val universalPayload = ConfluentSchemaBasedSerdeProvider.universal(confluentClientFactory)

  protected def executionConfigPreparerChain(modelData: LocalModelData): ExecutionConfigPreparer =
    ExecutionConfigPreparer.chain(
      ProcessSettingsPreparer(modelData),
      new UnoptimizedSerializationPreparer(modelData),
      new ExecutionConfigPreparer {
        override def prepareExecutionConfig(config: ExecutionConfig)(jobData: JobData, deploymentData: DeploymentData): Unit = {
          AvroSerializersRegistrar.registerGenericRecordSchemaIdSerializationIfNeed(config, confluentClientFactory, kafkaConfig)
        }
      }
    )

  protected lazy val metaData: MetaData = MetaData("mock-id", StreamMetaData())

  protected lazy val nodeId: NodeId = NodeId("mock-node-id")

  protected def universalSourceFactory(useStringForKey: Boolean): KafkaSource = {
    new UniversalKafkaSourceFactory[Any, Any](confluentClientFactory, universalPayload, testProcessObjectDependencies, new FlinkKafkaSourceImplFactory(None)) {
      override protected def prepareKafkaConfig: KafkaConfig = super.prepareKafkaConfig.copy(useStringForKey = useStringForKey)
    }
  }

  protected lazy val universalSinkFactory: UniversalKafkaSinkFactory = {
    new UniversalKafkaSinkFactory(confluentClientFactory, universalPayload, testProcessObjectDependencies, FlinkKafkaUniversalSinkImplFactory)
  }

  protected def validationModeParam(validationMode: ValidationMode): expression.Expression = s"'${validationMode.name}'"

  protected def createAvroProcess(source: SourceAvroParam,
                                  sink: UniversalSinkParam,
                                  filterExpression: Option[String] = None,
                                  sourceTopicParamValue: String => String = topic => s"'${topic}'"): CanonicalProcess = {
    import spel.Implicits._
    val sourceParams = List(TopicParamName -> asSpelExpression(sourceTopicParamValue(source.topic))) ++ (source match {
      case UniversalSourceParam(_, version) => List(SchemaVersionParamName -> asSpelExpression(formatVersionParam(version)))
      case UniversalSourceWithKeySupportParam(_, version) => List(SchemaVersionParamName -> asSpelExpression(formatVersionParam(version)))
      case SpecificSourceAvroParam(_) => List.empty
      case SpecificWithLogicalTypesSourceAvroParam(_) => List.empty
    })

    val baseSinkParams: List[(String, expression.Expression)] = List(
      TopicParamName -> s"'${sink.topic}'",
      SchemaVersionParamName -> formatVersionParam(sink.versionOption),
      SinkKeyParamName -> sink.key)

    val editorParams: List[(String, expression.Expression)] = List(SinkRawEditorParamName -> s"${sink.validationMode.isDefined}")

    val validationParams: List[(String, expression.Expression)] =
      sink.validationMode.map(validation => SinkValidationModeParameterName -> validationModeParam(validation)).toList

    val builder = ScenarioBuilder
      .streaming(s"avro-test")
      .parallelism(1)
      .source(
        "start",
        source.sourceType,
        sourceParams: _*
      )

    val filteredBuilder = filterExpression
      .map(filter => builder.filter("filter", filter))
      .getOrElse(builder)

    filteredBuilder
      .split("split",
        GraphBuilder.emptySink(
          "end",
          "kafka",
          baseSinkParams ++ editorParams ++ validationParams ++ sink.valueParams: _*
        ),
        GraphBuilder.emptySink("outputInputMeta", "sinkForInputMeta", "value" -> "#inputMeta")
      )
  }

  protected def formatVersionParam(versionOption: SchemaVersionOption): String =
    versionOption match {
      case LatestSchemaVersion => s"'${SchemaVersionOption.LatestOptionName}'"
      case ExistingSchemaVersion(version) => s"'$version'"
    }

  protected def runAndVerifyResult(process: CanonicalProcess, topic: TopicConfig, event: Any, expected: AnyRef, useSpecificAvroReader: Boolean = false): Unit =
    runAndVerifyResult(process, topic, List(event), List(expected), useSpecificAvroReader)

  protected def runAndVerifyResult(process: CanonicalProcess, topic: TopicConfig, events: List[Any], expected: AnyRef): Unit =
    runAndVerifyResult(process, topic, events, List(expected), useSpecificAvroReader = false)

  private def runAndVerifyResult(process: CanonicalProcess, topic: TopicConfig, events: List[Any], expected: List[AnyRef], useSpecificAvroReader: Boolean): Unit = {
    kafkaClient.createTopic(topic.input, partitions = 1)
    events.foreach(obj => pushMessage(obj, topic.input))
    kafkaClient.createTopic(topic.output, partitions = 1)

    run(process) {
      consumeAndVerifyMessages(topic.output, expected, useSpecificAvroReader)
    }
  }

  protected def run(process: CanonicalProcess)(action: => Unit): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    registrar.register(env, process, ProcessVersion.empty, DeploymentData.empty)
    env.withJobRunning(process.id)(action)
  }

  sealed trait SourceAvroParam {
    def topic: String

    def sourceType: String
  }

  case class UniversalSourceParam(topic: String, versionOption: SchemaVersionOption) extends SourceAvroParam {
    override def sourceType: String = "kafka"
  }

  case class UniversalSourceWithKeySupportParam(topic: String, versionOption: SchemaVersionOption) extends SourceAvroParam {
    override def sourceType: String = "kafka-key-value"
  }

  case class SpecificSourceAvroParam(topic: String) extends SourceAvroParam {
    override def sourceType: String = "kafka-avro-specific"
  }

  case class SpecificWithLogicalTypesSourceAvroParam(topic: String) extends SourceAvroParam {
    override def sourceType: String = "kafka-avro-specific-with-logical-types"
  }

  object SourceAvroParam {
    def forUniversal(topicConfig: TopicConfig, versionOption: SchemaVersionOption): SourceAvroParam =
      UniversalSourceParam(topicConfig.input, versionOption)

    def forUniversalWithKeySchemaSupport(topicConfig: TopicConfig, versionOption: SchemaVersionOption): SourceAvroParam =
      UniversalSourceWithKeySupportParam(topicConfig.input, versionOption)

    def forSpecific(topicConfig: TopicConfig): SourceAvroParam =
      SpecificSourceAvroParam(topicConfig.input)

    def forSpecificWithLogicalTypes(topicConfig: TopicConfig): SourceAvroParam =
      SpecificWithLogicalTypesSourceAvroParam(topicConfig.input)
  }

  case class UniversalSinkParam(topic: String,
                                versionOption: SchemaVersionOption,
                                valueParams: List[(String, expression.Expression)],
                                key: String,
                                validationMode: Option[ValidationMode]) //todo: improve it, but now - if defined we use 'raw editor' otherwise 'value editor'

  object UniversalSinkParam {

    import spel.Implicits.asSpelExpression

    def apply(topicConfig: TopicConfig, version: SchemaVersionOption, value: String, key: String = "", validationMode: Option[ValidationMode] = Some(ValidationMode.strict)): UniversalSinkParam =
      new UniversalSinkParam(topicConfig.output, version, (SinkValueParamName -> asSpelExpression(value)) :: Nil, key, validationMode)
  }

  protected def roundTripKeyValueObject(sourceFactory: Boolean => KafkaSource, useStringForKey: Boolean, topic: String, versionOption: SchemaVersionOption, givenKey: Any, givenValue: Any):
  Validated[NonEmptyList[ProcessCompilationError], Assertion] = {
    pushMessageWithKey(givenKey, givenValue, topic, useStringForKey = useStringForKey)
    readLastMessageAndVerify(sourceFactory(useStringForKey), topic, versionOption, givenKey, givenValue)
  }

  protected def readLastMessageAndVerify(sourceFactory: KafkaSource, topic: String, versionOption: SchemaVersionOption, givenKey: Any, givenValue: Any):
  Validated[NonEmptyList[ProcessCompilationError], Assertion] = {
    val parameterValues = Map(
      KafkaUniversalComponentTransformer.TopicParamName -> topic,
      KafkaUniversalComponentTransformer.SchemaVersionParamName -> versionOptionToString(versionOption)
    )

    createValidatedSource(sourceFactory, parameterValues)
      .map(source => {
        val bytes = source.generateTestData(1)
        info("test object: " + new String(bytes, StandardCharsets.UTF_8))
        val deserializedObj = source.testDataParser.parseTestData(TestData(bytes, 1)).head.asInstanceOf[ConsumerRecord[Any, Any]]

        deserializedObj.key() shouldEqual givenKey
        deserializedObj.value() shouldEqual givenValue
      })
  }

  protected def versionOptionToString(versionOption: SchemaVersionOption): String = {
    versionOption match {
      case LatestSchemaVersion => SchemaVersionOption.LatestOptionName
      case ExistingSchemaVersion(v) => v.toString
    }
  }

  private def createValidatedSource(sourceFactory: KafkaSource, parameterValues: Map[String, Any]):
  Validated[NonEmptyList[ProcessCompilationError], Source with TestDataGenerator with FlinkSourceTestSupport[AnyRef]] = {
    val validatedState = validateParamsAndInitializeState(sourceFactory, parameterValues)
    validatedState.map(state => {
      sourceFactory
        .implementation(
          parameterValues,
          List(TypedNodeDependencyValue(metaData), TypedNodeDependencyValue(nodeId)),
          Some(state))
        .asInstanceOf[Source with TestDataGenerator with FlinkSourceTestSupport[AnyRef]]
    })
  }

  // Use final contextTransformation to 1) validate parameters and 2) to calculate the final state.
  // This transformation can return
  // - the state that contains information on runtime key-value schemas, which is required in createSource.
  // - validation errors
  private def validateParamsAndInitializeState(sourceFactory: KafkaSource, parameterValues: Map[String, Any]):
  Validated[NonEmptyList[ProcessCompilationError], sourceFactory.State] = {
    implicit val nodeId: NodeId = NodeId("dummy")
    val parameters = parameterValues.mapValues(value => DefinedEagerParameter(value, null)).toList
    val definition = sourceFactory.contextTransformation(ValidationContext(), List(OutputVariableNameValue("dummy")))
    val stepResult = definition(sourceFactory.TransformationStep(parameters, None))
    stepResult match {
      case sourceFactory.FinalResults(_, Nil, state) => Valid(state.get.asInstanceOf[sourceFactory.State])
      case result: sourceFactory.FinalResults => Invalid(NonEmptyList.fromListUnsafe(result.errors))
      case _ => Invalid(NonEmptyList(CustomNodeError("Unexpected result of contextTransformation", None), Nil))
    }
  }

}

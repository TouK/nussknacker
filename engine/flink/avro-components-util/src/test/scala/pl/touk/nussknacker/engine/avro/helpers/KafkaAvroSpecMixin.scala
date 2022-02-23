package pl.touk.nussknacker.engine.avro.helpers

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.specific.SpecificRecord
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.scalatest.{Assertion, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, OutputVariableNameValue, TypedNodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.process.{Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.{JobData, MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer._
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.kryo.AvroSerializersRegistrar
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, LatestSchemaVersion, SchemaRegistryProvider, SchemaVersionOption}
import pl.touk.nussknacker.engine.avro.sink.KafkaAvroSinkFactory
import pl.touk.nussknacker.engine.avro.sink.flink.FlinkKafkaAvroSinkImplFactory
import pl.touk.nussknacker.engine.avro.source.{KafkaAvroSourceFactory, SpecificRecordKafkaAvroSourceFactory}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceTestSupport
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.test.TestData
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.graph.{EspProcess, expression}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer.{ProcessSettingsPreparer, UnoptimizedSerializationPreparer}
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.{NussknackerAssertions, PatientScalaFutures}

import java.nio.charset.StandardCharsets
import scala.reflect.ClassTag

trait KafkaAvroSpecMixin extends FunSuite with KafkaWithSchemaRegistryOperations with FlinkSpec with SchemaRegistryMixin with Matchers with LazyLogging with NussknackerAssertions with PatientScalaFutures with Serializable {

  import spel.Implicits._

  protected var registrar: FlinkProcessRegistrar = _

  protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory

  // In default test scenario we use avro payload.
  protected lazy val schemaRegistryProvider: SchemaRegistryProvider =
    ConfluentSchemaRegistryProvider.avroPayload(confluentClientFactory)

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

  protected def avroSourceFactory(useStringForKey: Boolean): KafkaAvroSourceFactory[Any, Any] = {
    new KafkaAvroSourceFactory[Any, Any](schemaRegistryProvider, testProcessObjectDependencies, new FlinkKafkaSourceImplFactory(None)) {
      override protected def prepareKafkaConfig: KafkaConfig = super.prepareKafkaConfig.copy(useStringForKey = useStringForKey)
    }
  }

  // For now SpecificRecord source factory requires KafkaConfig with useStringForKey=true. Parameter is used to test scenario with wrong configuration.
  protected def specificSourceFactory[V <: SpecificRecord : ClassTag](useStringForKey: Boolean = true): KafkaAvroSourceFactory[Any, Any] = {
    val factory = new SpecificRecordKafkaAvroSourceFactory[V](schemaRegistryProvider, testProcessObjectDependencies, new FlinkKafkaSourceImplFactory(None)) {
      override protected def prepareKafkaConfig: KafkaConfig = super.prepareKafkaConfig.copy(useStringForKey = useStringForKey) // TODO: check what happens with false
    }
    factory.asInstanceOf[KafkaAvroSourceFactory[Any, Any]]
  }

  protected lazy val avroSinkFactory: KafkaAvroSinkFactory = {
    new KafkaAvroSinkFactory(schemaRegistryProvider, testProcessObjectDependencies, FlinkKafkaAvroSinkImplFactory)
  }

  protected def validationModeParam(validationMode: ValidationMode): expression.Expression = s"'${validationMode.name}'"

  protected def createAvroProcess(source: SourceAvroParam,
                                  sink: SinkAvroParam,
                                  filterExpression: Option[String] = None,
                                  sourceTopicParamValue: String => String = topic => s"'${topic}'"): EspProcess = {
    import spel.Implicits._
    val sourceParams = List(TopicParamName -> asSpelExpression(sourceTopicParamValue(source.topic))) ++ (source match {
      case GenericSourceAvroParam(_, version) => List(SchemaVersionParamName -> asSpelExpression(formatVersionParam(version)))
      case GenericSourceWithKeySupportAvroParam(_, version) => List(SchemaVersionParamName -> asSpelExpression(formatVersionParam(version)))
      case SpecificSourceAvroParam(_) => List.empty
      case SpecificWithLogicalTypesSourceAvroParam(_) => List.empty
    })

    val baseSinkParams: List[(String, expression.Expression)] = List(
      TopicParamName -> s"'${sink.topic}'",
      SchemaVersionParamName -> formatVersionParam(sink.versionOption),
      SinkKeyParamName -> sink.key)

    val validationParams: List[(String, expression.Expression)] =
      sink.validationMode.map(validation => SinkValidationModeParameterName -> validationModeParam(validation)).toList

    val builder = EspProcessBuilder
      .id(s"avro-test")
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
          sink.sinkId,
          baseSinkParams ++ validationParams ++ sink.valueParams: _*
        ),
        GraphBuilder.emptySink("outputInputMeta", "sinkForInputMeta", "value" -> "#inputMeta")
      )
  }

  protected def formatVersionParam(versionOption: SchemaVersionOption): String =
    versionOption match {
      case LatestSchemaVersion => s"'${SchemaVersionOption.LatestOptionName}'"
      case ExistingSchemaVersion(version) => s"'$version'"
    }

  protected def runAndVerifyResult(process: EspProcess, topic: TopicConfig, event: Any, expected: AnyRef, useSpecificAvroReader: Boolean = false): Unit =
    runAndVerifyResult(process, topic, List(event), List(expected), useSpecificAvroReader)

  protected def runAndVerifyResult(process: EspProcess, topic: TopicConfig, events: List[Any], expected: AnyRef): Unit =
    runAndVerifyResult(process, topic, events, List(expected), useSpecificAvroReader = false)

  private def runAndVerifyResult(process: EspProcess, topic: TopicConfig, events: List[Any], expected: List[AnyRef], useSpecificAvroReader: Boolean): Unit = {
    kafkaClient.createTopic(topic.input, partitions = 1)
    events.foreach(obj => pushMessage(obj, topic.input))
    kafkaClient.createTopic(topic.output, partitions = 1)

    run(process) {
      consumeAndVerifyMessages(topic.output, expected, useSpecificAvroReader)
    }
  }

  protected def run(process: EspProcess)(action: => Unit): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    registrar.register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty, DeploymentData.empty)
    env.withJobRunning(process.id)(action)
  }

  sealed trait SourceAvroParam {
    def topic: String

    def sourceType: String
  }

  case class GenericSourceAvroParam(topic: String, versionOption: SchemaVersionOption) extends SourceAvroParam {
    override def sourceType: String = "kafka-avro"
  }

  case class GenericSourceWithKeySupportAvroParam(topic: String, versionOption: SchemaVersionOption) extends SourceAvroParam {
    override def sourceType: String = "kafka-avro-key-value"
  }

  case class SpecificSourceAvroParam(topic: String) extends SourceAvroParam {
    override def sourceType: String = "kafka-avro-specific"
  }

  case class SpecificWithLogicalTypesSourceAvroParam(topic: String) extends SourceAvroParam {
    override def sourceType: String = "kafka-avro-specific-with-logical-types"
  }

  object SourceAvroParam {

    def forGeneric(topicConfig: TopicConfig, versionOption: SchemaVersionOption): SourceAvroParam =
      GenericSourceAvroParam(topicConfig.input, versionOption)

    def forGenericWithKeySchemaSupport(topicConfig: TopicConfig, versionOption: SchemaVersionOption): SourceAvroParam =
      GenericSourceWithKeySupportAvroParam(topicConfig.input, versionOption)

    def forSpecific(topicConfig: TopicConfig): SourceAvroParam =
      SpecificSourceAvroParam(topicConfig.input)

    def forSpecificWithLogicalTypes(topicConfig: TopicConfig): SourceAvroParam =
      SpecificWithLogicalTypesSourceAvroParam(topicConfig.input)

  }

  case class SinkAvroParam(topic: String,
                           versionOption: SchemaVersionOption,
                           valueParams: List[(String, expression.Expression)],
                           key: String,
                           validationMode: Option[ValidationMode],
                           sinkId: String)

  object SinkAvroParam {

    import spel.Implicits.asSpelExpression

    def apply(topicConfig: TopicConfig, version: SchemaVersionOption, value: String, key: String = "", validationMode: Option[ValidationMode] = Some(ValidationMode.strict)): SinkAvroParam =
      new SinkAvroParam(topicConfig.output, version, (SinkValueParamName -> asSpelExpression(value)) :: Nil, key, validationMode, "kafka-avro-raw")
  }

  protected def roundTripKeyValueObject(sourceFactory: Boolean => KafkaAvroSourceFactory[Any, Any], useStringForKey: Boolean, topic: String, versionOption: SchemaVersionOption, givenKey: Any, givenValue: Any):
  Validated[NonEmptyList[ProcessCompilationError], Assertion] = {
    pushMessageWithKey(givenKey, givenValue, topic, useStringForKey = useStringForKey)
    readLastMessageAndVerify(sourceFactory(useStringForKey), topic, versionOption, givenKey, givenValue)
  }

  protected def readLastMessageAndVerify(sourceFactory: KafkaAvroSourceFactory[Any, Any], topic: String, versionOption: SchemaVersionOption, givenKey: Any, givenValue: Any):
  Validated[NonEmptyList[ProcessCompilationError], Assertion] = {
    val parameterValues = sourceFactory match {
      case _: SpecificRecordKafkaAvroSourceFactory[_] => Map(KafkaAvroBaseComponentTransformer.TopicParamName -> topic)
      case _ => Map(KafkaAvroBaseComponentTransformer.TopicParamName -> topic, KafkaAvroBaseComponentTransformer.SchemaVersionParamName -> versionOptionToString(versionOption))
    }
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

  private def createValidatedSource(sourceFactory: KafkaAvroSourceFactory[Any, Any], parameterValues: Map[String, Any]):
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
  private def validateParamsAndInitializeState(sourceFactory: KafkaAvroSourceFactory[Any, Any], parameterValues: Map[String, Any]):
  Validated[NonEmptyList[ProcessCompilationError], sourceFactory.State] = {
    implicit val nodeId: NodeId = NodeId("dummy")
    val parameters = parameterValues.mapValues(value => DefinedEagerParameter(value, null)).toList
    val definition = sourceFactory.contextTransformation(ValidationContext(), List(OutputVariableNameValue("dummy")))
    val stepResult = definition(sourceFactory.TransformationStep(parameters, None))
    stepResult match {
      case sourceFactory.FinalResults(_, Nil, state) => Valid(state.get)
      case result: sourceFactory.FinalResults => Invalid(NonEmptyList.fromListUnsafe(result.errors))
      case _ => Invalid(NonEmptyList(CustomNodeError("Unexpected result of contextTransformation", None), Nil))
    }
  }

}

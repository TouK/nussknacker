package pl.touk.nussknacker.engine.schemedkafka.helpers

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  OutputVariableNameValue,
  TypedNodeDependencyValue
}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory, TestDataGenerator}
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
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer.{
  ProcessSettingsPreparer,
  UnoptimizedSerializationPreparer
}
import pl.touk.nussknacker.engine.process.runner.UnitTestsFlinkRunner
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.kryo.AvroSerializersRegistrar
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  ExistingSchemaVersion,
  LatestSchemaVersion,
  SchemaRegistryClientFactory,
  SchemaVersionOption
}
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory
import pl.touk.nussknacker.engine.schemedkafka.sink.flink.FlinkKafkaUniversalSinkImplFactory
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.{ModelData, spel}
import pl.touk.nussknacker.test.{NuScalaTestAssertions, VeryPatientScalaFutures}

trait KafkaAvroSpecMixin
    extends AnyFunSuite
    with KafkaWithSchemaRegistryOperations
    with FlinkSpec
    with SchemaRegistryMixin
    with Matchers
    with LazyLogging
    with NuScalaTestAssertions
    with VeryPatientScalaFutures
    with Serializable {

  type KafkaSource = SourceFactory with KafkaUniversalComponentTransformer[Source]

  import spel.Implicits._

  protected var modelData: ModelData = _

  protected def schemaRegistryClientFactory: SchemaRegistryClientFactory

  private lazy val universalPayload = UniversalSchemaBasedSerdeProvider.create(schemaRegistryClientFactory)

  protected def executionConfigPreparerChain(modelData: LocalModelData): ExecutionConfigPreparer =
    ExecutionConfigPreparer.chain(
      ProcessSettingsPreparer(modelData),
      new UnoptimizedSerializationPreparer(modelData),
      new ExecutionConfigPreparer {

        override def prepareExecutionConfig(
            config: ExecutionConfig
        )(jobData: JobData, deploymentData: DeploymentData): Unit = {
          AvroSerializersRegistrar.registerGenericRecordSchemaIdSerializationIfNeed(
            config,
            schemaRegistryClientFactory,
            kafkaConfig
          )
        }

      }
    )

  protected lazy val metaData: MetaData = MetaData("mock-id", StreamMetaData())

  protected lazy val nodeId: NodeId = NodeId("mock-node-id")

  protected def universalSourceFactory(useStringForKey: Boolean): KafkaSource = {
    new UniversalKafkaSourceFactory[Any, Any](
      schemaRegistryClientFactory,
      universalPayload,
      testModelDependencies,
      new FlinkKafkaSourceImplFactory(None)
    ) {
      override protected def prepareKafkaConfig: KafkaConfig =
        super.prepareKafkaConfig.copy(useStringForKey = useStringForKey)
    }
  }

  protected lazy val universalSinkFactory: UniversalKafkaSinkFactory = {
    new UniversalKafkaSinkFactory(
      schemaRegistryClientFactory,
      universalPayload,
      testModelDependencies,
      FlinkKafkaUniversalSinkImplFactory
    )
  }

  protected def validationModeParam(validationMode: ValidationMode): expression.Expression = s"'${validationMode.name}'"

  protected def createAvroProcess(
      source: SourceAvroParam,
      sink: UniversalSinkParam,
      filterExpression: Option[String] = None,
      sourceTopicParamValue: String => String = topic => s"'${topic}'"
  ): CanonicalProcess = {
    import spel.Implicits._
    val sourceParams = List(TopicParamName -> asSpelExpression(sourceTopicParamValue(source.topic))) ++ (source match {
      case UniversalSourceParam(_, version) =>
        List(SchemaVersionParamName -> asSpelExpression(formatVersionParam(version)))
      case UniversalSourceWithKeySupportParam(_, version) =>
        List(SchemaVersionParamName -> asSpelExpression(formatVersionParam(version)))
    })

    val baseSinkParams: List[(String, expression.Expression)] = List(
      TopicParamName         -> s"'${sink.topic}'",
      SchemaVersionParamName -> formatVersionParam(sink.versionOption),
      SinkKeyParamName       -> sink.key
    )

    val editorParams: List[(String, expression.Expression)] = List(
      SinkRawEditorParamName -> s"${sink.validationMode.isDefined}"
    )

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
      .split(
        "split",
        GraphBuilder.emptySink(
          "end",
          "kafka",
          baseSinkParams ++ editorParams ++ validationParams ++ sink.valueParams: _*
        ),
        GraphBuilder.emptySink("outputInputMeta", "sinkForInputMeta", "Value" -> "#inputMeta")
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
    kafkaClient.createTopic(topic.input, partitions = 1)
    events.foreach(obj => pushMessage(obj, topic.input))
    kafkaClient.createTopic(topic.output, partitions = 1)

    run(process) {
      consumeAndVerifyMessages(topic.output, expected)
      additionalVerificationBeforeScenarioCancel
    }
  }

  protected def run(process: CanonicalProcess)(action: => Unit): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    UnitTestsFlinkRunner.registerInEnvironmentWithModel(env, modelData)(process)
    env.withJobRunning(process.name.value)(action)
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
      UniversalSourceParam(topicConfig.input, versionOption)

    def forUniversalWithKeySchemaSupport(
        topicConfig: TopicConfig,
        versionOption: SchemaVersionOption
    ): SourceAvroParam =
      UniversalSourceWithKeySupportParam(topicConfig.input, versionOption)

  }

  case class UniversalSinkParam(
      topic: String,
      versionOption: SchemaVersionOption,
      valueParams: List[(String, expression.Expression)],
      key: String,
      validationMode: Option[ValidationMode]
  ) // TODO: improve it, but now - if defined we use 'raw editor' otherwise 'value editor'

  object UniversalSinkParam {

    import spel.Implicits.asSpelExpression

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
        (SinkValueParamName -> asSpelExpression(value)) :: Nil,
        key,
        validationMode
      )

  }

  protected def roundTripKeyValueObject(
      sourceFactory: Boolean => KafkaSource,
      useStringForKey: Boolean,
      topic: String,
      versionOption: SchemaVersionOption,
      givenKey: Any,
      givenValue: Any
  ): Validated[NonEmptyList[ProcessCompilationError], Assertion] = {
    pushMessageWithKey(givenKey, givenValue, topic, useStringForKey = useStringForKey)
    readLastMessageAndVerify(sourceFactory(useStringForKey), topic, versionOption, givenKey, givenValue)
  }

  protected def readLastMessageAndVerify(
      sourceFactory: KafkaSource,
      topic: String,
      versionOption: SchemaVersionOption,
      givenKey: Any,
      givenValue: Any
  ): Validated[NonEmptyList[ProcessCompilationError], Assertion] = {
    val parameterValues = Map(
      KafkaUniversalComponentTransformer.TopicParamName         -> topic,
      KafkaUniversalComponentTransformer.SchemaVersionParamName -> versionOptionToString(versionOption)
    )
    createValidatedSource(sourceFactory, parameterValues)
      .map(source => {
        val testData = source.generateTestData(1)
        info("test object: " + testData)
        val deserializedObj =
          source.testRecordParser.parse(testData.testRecords.head).asInstanceOf[ConsumerRecord[Any, Any]]

        deserializedObj.key() shouldEqual givenKey
        deserializedObj.value() shouldEqual givenValue
      })
  }

  protected def versionOptionToString(versionOption: SchemaVersionOption): String = {
    versionOption match {
      case LatestSchemaVersion      => SchemaVersionOption.LatestOptionName
      case ExistingSchemaVersion(v) => v.toString
    }
  }

  private def createValidatedSource(
      sourceFactory: KafkaSource,
      parameterValues: Map[String, Any]
  ): Validated[NonEmptyList[
    ProcessCompilationError
  ], Source with TestDataGenerator with FlinkSourceTestSupport[AnyRef]] = {
    val validatedState = validateParamsAndInitializeState(sourceFactory, parameterValues)
    validatedState.map(state => {
      sourceFactory
        .implementation(
          parameterValues,
          List(TypedNodeDependencyValue(metaData), TypedNodeDependencyValue(nodeId)),
          Some(state)
        )
        .asInstanceOf[Source with TestDataGenerator with FlinkSourceTestSupport[AnyRef]]
    })
  }

  // Use final contextTransformation to 1) validate parameters and 2) to calculate the final state.
  // This transformation can return
  // - the state that contains information on runtime key-value schemas, which is required in createSource.
  // - validation errors
  private def validateParamsAndInitializeState(
      sourceFactory: KafkaSource,
      parameterValues: Map[String, Any]
  ): Validated[NonEmptyList[ProcessCompilationError], sourceFactory.State] = {
    implicit val nodeId: NodeId = NodeId("dummy")
    val parameters              = parameterValues.mapValuesNow(value => DefinedEagerParameter(value, null)).toList
    val definition = sourceFactory.contextTransformation(ValidationContext(), List(OutputVariableNameValue("dummy")))
    val stepResult = definition(sourceFactory.TransformationStep(parameters, None))
    stepResult match {
      case sourceFactory.FinalResults(_, Nil, state) => Valid(state.get.asInstanceOf[sourceFactory.State])
      case result: sourceFactory.FinalResults        => Invalid(NonEmptyList.fromListUnsafe(result.errors))
      case _ => Invalid(NonEmptyList(CustomNodeError("Unexpected result of contextTransformation", None), Nil))
    }
  }

}

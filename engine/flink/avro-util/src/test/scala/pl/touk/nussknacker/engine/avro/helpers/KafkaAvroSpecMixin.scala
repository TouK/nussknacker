package pl.touk.nussknacker.engine.avro.helpers

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.{JobData, MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer._
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.kryo.AvroSerializersRegistrar
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, LatestSchemaVersion, SchemaVersionOption}
import pl.touk.nussknacker.engine.avro.sink.KafkaAvroSinkFactory
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.{EspProcess, expression}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer.{ProcessSettingsPreparer, UnoptimizedSerializationPreparer}
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.{NussknackerAssertions, PatientScalaFutures}

trait KafkaAvroSpecMixin extends FunSuite with KafkaWithSchemaRegistryOperations with FlinkSpec with SchemaRegistryMixin with Matchers with LazyLogging with NussknackerAssertions with PatientScalaFutures {

  import spel.Implicits._

  protected var registrar: FlinkProcessRegistrar = _

  protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory

  protected lazy val schemaRegistryProvider: ConfluentSchemaRegistryProvider =
    ConfluentSchemaRegistryProvider(confluentClientFactory)

  protected def executionConfigPreparerChain(modelData: LocalModelData): ExecutionConfigPreparer =
    ExecutionConfigPreparer.chain(
      ProcessSettingsPreparer(modelData),
      new UnoptimizedSerializationPreparer(modelData),
      new ExecutionConfigPreparer {
        override def prepareExecutionConfig(config: ExecutionConfig)(jobData: JobData): Unit = {
          AvroSerializersRegistrar.registerGenericRecordSchemaIdSerializationIfNeed(config, confluentClientFactory, kafkaConfig)
        }
      }
    )

  protected lazy val metaData: MetaData = MetaData("mock-id", StreamMetaData())

  protected lazy val nodeId: NodeId = NodeId("mock-node-id")

  protected def avroSourceFactory(useStringForKey: Boolean): KafkaAvroSourceFactory[Any, Any] = {
    new KafkaAvroSourceFactory[Any, Any](schemaRegistryProvider, testProcessObjectDependencies, None) {
      override protected def prepareKafkaConfig: KafkaConfig = super.prepareKafkaConfig.copy(useStringForKey = useStringForKey)
    }
  }

  protected lazy val avroSinkFactory: KafkaAvroSinkFactory = {
    new KafkaAvroSinkFactory(schemaRegistryProvider, testProcessObjectDependencies)
  }

  protected def validationModeParam(validationMode: ValidationMode): expression.Expression = s"'${validationMode.name}'"

  protected def createAvroProcess(source: SourceAvroParam, sink: SinkAvroParam, filterExpression: Option[String] = None): EspProcess = {
    import spel.Implicits._
    val sourceParams = List(TopicParamName -> asSpelExpression(s"'${source.topic}'")) ++ (source match {
      case GenericSourceAvroParam(_, version) => List(SchemaVersionParamName -> asSpelExpression(formatVersionParam(version)))
      case GenericSourceWithKeySupportAvroParam(_, version) => List(SchemaVersionParamName -> asSpelExpression(formatVersionParam(version)))
      case SpecificSourceAvroParam(_) => List.empty
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
      .exceptionHandler()
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
        GraphBuilder.sink("outputInputMeta", "#inputMeta", "sinkForInputMeta")
      )
  }

  protected def formatVersionParam(versionOption: SchemaVersionOption): String =
    versionOption match {
      case LatestSchemaVersion => s"'${SchemaVersionOption.LatestOptionName}'"
      case ExistingSchemaVersion(version) =>s"'$version'"
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

  case class SpecificSourceAvroParam(topic: String) extends SourceAvroParam {
    override def sourceType: String = "kafka-avro-specific"
  }

  case class GenericSourceWithKeySupportAvroParam(topic: String, versionOption: SchemaVersionOption) extends SourceAvroParam {
    override def sourceType: String = "kafka-avro-key-value"
  }

  object SourceAvroParam {

    def forGeneric(topicConfig: TopicConfig, versionOption: SchemaVersionOption): SourceAvroParam =
      GenericSourceAvroParam(topicConfig.input, versionOption)

    def forSpecific(topicConfig: TopicConfig): SourceAvroParam =
      SpecificSourceAvroParam(topicConfig.input)

    def forGenericWithKeySchemaSupport(topicConfig: TopicConfig, versionOption: SchemaVersionOption): SourceAvroParam =
      GenericSourceWithKeySupportAvroParam(topicConfig.input, versionOption)

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
}

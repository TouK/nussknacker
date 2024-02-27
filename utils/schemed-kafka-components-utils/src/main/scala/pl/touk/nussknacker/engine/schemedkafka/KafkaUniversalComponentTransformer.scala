package pl.touk.nussknacker.engine.schemedkafka

import cats.data.Validated.{Invalid, Valid}
import cats.data.Writer
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, SingleInputDynamicComponent}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, Parameter}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.TopicParamName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry._
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import FixedExpressionValue.nullFixedValue
import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.kafka.validator.WithCachedTopicsExistenceValidator
import pl.touk.nussknacker.engine.kafka.{KafkaComponentsUtils, KafkaConfig, PreparedKafkaTopic}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaSupportDispatcher

object KafkaUniversalComponentTransformer {
  final val SchemaVersionParamName          = "Schema version"
  final val TopicParamName                  = "Topic"
  final val SinkKeyParamName                = "Key"
  final val SinkValueParamName              = "Value"
  final val SinkValidationModeParameterName = "Value validation mode"
  final val SinkRawEditorParamName          = "Raw editor"

  def extractValidationMode(value: String): ValidationMode =
    ValidationMode.fromString(value, SinkValidationModeParameterName)

}

trait KafkaUniversalComponentTransformer[T]
    extends SingleInputDynamicComponent[T]
    with WithCachedTopicsExistenceValidator { self: Component =>

  type WithError[V] = Writer[List[ProcessCompilationError], V]

  def schemaRegistryClientFactory: SchemaRegistryClientFactory

  def modelDependencies: ProcessObjectDependencies

  @transient protected lazy val schemaRegistryClient: SchemaRegistryClient =
    schemaRegistryClientFactory.create(kafkaConfig)

  protected def topicSelectionStrategy: TopicSelectionStrategy = new AllTopicsSelectionStrategy

  @transient protected lazy val kafkaConfig: KafkaConfig = prepareKafkaConfig

  @transient protected lazy val schemaSupportDispatcher: UniversalSchemaSupportDispatcher =
    UniversalSchemaSupportDispatcher(
      kafkaConfig
    )

  protected def prepareKafkaConfig: KafkaConfig = {
    KafkaConfig.parseConfig(modelDependencies.config)
  }

  protected def getTopicParam(implicit nodeId: NodeId): WithError[Parameter] = {
    val topics = topicSelectionStrategy.getTopics(schemaRegistryClient)

    (topics match {
      case Valid(topics) => Writer[List[ProcessCompilationError], List[String]](Nil, topics)
      case Invalid(e) =>
        Writer[List[ProcessCompilationError], List[String]](
          List(CustomNodeError(e.getMessage, Some(topicParamName))),
          Nil
        )
    }).map { topics =>
      getTopicParam(topics)
    }
  }

  private def getTopicParam(topics: List[String]): Parameter = {
    Parameter[String](topicParamName).copy(editor =
      Some(
        FixedValuesParameterEditor(
          // Initially we don't want to select concrete topic by user so we add null topic on the beginning of select box.
          // TODO: add addNullOption feature flag to FixedValuesParameterEditor
          nullFixedValue +: topics
            .flatMap(topic => modelDependencies.namingStrategy.decodeName(topic))
            .sorted
            .map(v => FixedExpressionValue(s"'$v'", v))
        )
      )
    )
  }

  protected def getVersionParam(preparedTopic: PreparedKafkaTopic)(implicit nodeId: NodeId): WithError[Parameter] = {
    val versions = schemaRegistryClient.getAllVersions(preparedTopic.prepared, isKey = false)
    (versions match {
      case Valid(versions) => Writer[List[ProcessCompilationError], List[Integer]](Nil, versions)
      case Invalid(e) =>
        Writer[List[ProcessCompilationError], List[Integer]](
          List(CustomNodeError(e.getMessage, Some(topicParamName))),
          Nil
        )
    }).map(getVersionParam)
  }

  protected def getVersionParam(versions: List[Integer]): Parameter = {
    val versionValues =
      FixedExpressionValue(s"'${SchemaVersionOption.LatestOptionName}'", "Latest version") :: versions.sorted.map(v =>
        FixedExpressionValue(s"'$v'", v.toString)
      )
    Parameter[String](KafkaUniversalComponentTransformer.SchemaVersionParamName)
      .copy(editor = Some(FixedValuesParameterEditor(versionValues)))
  }

  protected def extractPreparedTopic(params: Params): PreparedKafkaTopic =
    prepareTopic(params.extractUnsafe(topicParamName))

  protected def prepareTopic(topic: String): PreparedKafkaTopic =
    KafkaComponentsUtils.prepareKafkaTopic(topic, modelDependencies)

  protected def parseVersionOption(versionOptionName: String): SchemaVersionOption =
    SchemaVersionOption.byName(versionOptionName)

  protected def prepareValueSchemaDeterminer(
      preparedTopic: PreparedKafkaTopic,
      version: SchemaVersionOption
  ): AvroSchemaDeterminer = {
    new BasedOnVersionAvroSchemaDeterminer(schemaRegistryClient, preparedTopic.prepared, version, isKey = false)
  }

  // TODO: add schema versioning for key schemas
  protected def prepareKeySchemaDeterminer(preparedTopic: PreparedKafkaTopic): AvroSchemaDeterminer = {
    new BasedOnVersionAvroSchemaDeterminer(
      schemaRegistryClient,
      preparedTopic.prepared,
      LatestSchemaVersion,
      isKey = true
    )
  }

  protected def prepareUniversalValueSchemaDeterminer(
      preparedTopic: PreparedKafkaTopic,
      version: SchemaVersionOption
  ): ParsedSchemaDeterminer = {
    new ParsedSchemaDeterminer(schemaRegistryClient, preparedTopic.prepared, version, isKey = false)
  }

  // TODO: add schema versioning for key schemas
  protected def prepareUniversalKeySchemaDeterminer(preparedTopic: PreparedKafkaTopic): ParsedSchemaDeterminer = {
    new ParsedSchemaDeterminer(schemaRegistryClient, preparedTopic.prepared, LatestSchemaVersion, isKey = true)
  }

  protected def topicParamStep(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val topicParam = getTopicParam.map(List(_))
      NextParameters(parameters = topicParam.value, errors = topicParam.written)
  }

  protected def schemaParamStep(nextParams: List[Parameter])(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep((topicParamName, DefinedEagerParameter(topic: String, _)) :: Nil, _) =>
      val preparedTopic = prepareTopic(topic)
      val versionParam  = getVersionParam(preparedTopic)
      val topicValidationErrors =
        validateTopic(preparedTopic.prepared).swap.toList.map(_.toCustomNodeError(nodeId.id, Some(topicParamName)))
      NextParameters(
        versionParam.value :: nextParams,
        errors = versionParam.written ++ topicValidationErrors
      )
    case TransformationStep((topicParamName, _) :: Nil, _) =>
      NextParameters(parameters = fallbackVersionOptionParam :: nextParams)
  }

  def paramsDeterminedAfterSchema: List[Parameter]

  // edge case - for some reason Topic is not defined
  @transient protected lazy val fallbackVersionOptionParam: Parameter = getVersionParam(Nil)

  // override it if you use other parameter name for topic
  @transient protected lazy val topicParamName: String = TopicParamName

}

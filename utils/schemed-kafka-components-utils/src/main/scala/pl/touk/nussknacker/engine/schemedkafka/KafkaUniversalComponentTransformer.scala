package pl.touk.nussknacker.engine.schemedkafka

import cats.data.Validated.{Invalid, Valid}
import cats.data.{Validated, Writer}
import org.apache.kafka.clients.admin.ListTopicsOptions
import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, SingleInputDynamicComponent}
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue.nullFixedValue
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, TopicName}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.kafka.validator.WithCachedTopicsExistenceValidator
import pl.touk.nussknacker.engine.kafka.{
  KafkaComponentsUtils,
  KafkaConfig,
  KafkaUtils,
  PreparedKafkaTopic,
  UnspecializedTopicName
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaSupportDispatcher
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName._
import pl.touk.nussknacker.engine.kafka.validator.TopicsExistenceValidator.TopicValidationType

import scala.jdk.CollectionConverters.CollectionHasAsScala

object KafkaUniversalComponentTransformer {
  final val schemaVersionParamName      = ParameterName("Schema version")
  final val topicParamName              = ParameterName("Topic")
  final val sinkKeyParamName            = ParameterName("Key")
  final val sinkValueParamName          = ParameterName("Value")
  final val sinkValidationModeParamName = ParameterName("Value validation mode")
  final val sinkRawEditorParamName      = ParameterName("Raw editor")

  def extractValidationMode(value: String): ValidationMode =
    ValidationMode.fromString(value, sinkValidationModeParamName)

}

abstract class KafkaUniversalComponentTransformer[T, TN <: TopicName: TopicValidationType]
    extends SingleInputDynamicComponent[T]
    with WithCachedTopicsExistenceValidator {
  self: Component =>

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

  protected def getTopicParam(
      implicit nodeId: NodeId
  ): WithError[ParameterCreatorWithNoDependency with ParameterExtractor[String]] = {
    val allTopics = getAllTopics

    // TODO: previously schemaRegistryClient made validation
    val topics: Validated[SchemaRegistryError, List[UnspecializedTopicName]] = Valid(allTopics)

    (topics match {
      case Valid(topics) => Writer[List[ProcessCompilationError], List[UnspecializedTopicName]](Nil, topics)
      case Invalid(e) =>
        Writer[List[ProcessCompilationError], List[UnspecializedTopicName]](
          List(CustomNodeError(e.getMessage, Some(topicParamName))),
          Nil
        )
    }).map { topics =>
      getTopicParam(topics)
    }
  }

  private def getTopicParam(topics: List[UnspecializedTopicName]) = {
    ParameterDeclaration
      .mandatory[String](topicParamName)
      .withCreator(
        modify = _.copy(editor =
          Some(
            FixedValuesParameterEditor(
              // Initially we don't want to select concrete topic by user so we add null topic on the beginning of select box.
              // TODO: add addNullOption feature flag to FixedValuesParameterEditor
              nullFixedValue +: topics
                .flatMap(topic => modelDependencies.namingStrategy.decodeName(topic.name))
                .sorted
                .map(v => FixedExpressionValue(s"'$v'", v))
            )
          )
        )
      )
  }

  protected def getVersionParam(
      preparedTopic: PreparedKafkaTopic[TN],
  )(implicit nodeId: NodeId): WithError[ParameterCreatorWithNoDependency with ParameterExtractor[String]] = {
    val topicsWithSchema = topicSelectionStrategy.getTopics(schemaRegistryClient)
    if (topicsWithSchema.exists(topics => topics.contains(preparedTopic.prepared.topicName.toUnspecialized))) {
      val versions = schemaRegistryClient.getAllVersions(preparedTopic.prepared.toUnspecialized, isKey = false)
      (versions match {
        case Valid(versions) => Writer[List[ProcessCompilationError], List[Integer]](Nil, versions)
        case Invalid(e) =>
          Writer[List[ProcessCompilationError], List[Integer]](
            List(CustomNodeError(e.getMessage, Some(topicParamName))),
            Nil
          )
      }).map(getVersionParam)
    } else {
      val versionValues = List(
        FixedExpressionValue("'Json'", "Json"),
        FixedExpressionValue("'Plain'", "Plain")
      )

      Writer[List[ProcessCompilationError], List[FixedExpressionValue]](Nil, versionValues).map(versions =>
        ParameterDeclaration
          .mandatory[String](KafkaUniversalComponentTransformer.schemaVersionParamName)
          .withCreator(
            modify = _.copy(editor = Some(FixedValuesParameterEditor(versions)))
          )
      )
    }
  }

  protected def getVersionParam(
      versions: List[Integer]
  ): ParameterCreatorWithNoDependency with ParameterExtractor[String] = {
    val versionValues =
      FixedExpressionValue(s"'${SchemaVersionOption.LatestOptionName}'", "Latest version") :: versions.sorted.map(v =>
        FixedExpressionValue(s"'$v'", v.toString)
      )

    ParameterDeclaration
      .mandatory[String](KafkaUniversalComponentTransformer.schemaVersionParamName)
      .withCreator(
        modify = _.copy(editor = Some(FixedValuesParameterEditor(versionValues)))
      )
  }

  protected def extractPreparedTopic(params: Params): PreparedKafkaTopic[TN] =
    prepareTopic(params.extractUnsafe(topicParamName))

  protected def prepareTopic(topicString: String): PreparedKafkaTopic[TN] =
    KafkaComponentsUtils.prepareKafkaTopic(topicFrom(topicString), modelDependencies)

  protected def topicFrom(value: String): TN

  protected def parseVersionOption(versionOptionName: String): SchemaVersionOption =
    SchemaVersionOption.byName(versionOptionName)

  protected def prepareValueSchemaDeterminer(
      preparedTopic: PreparedKafkaTopic[TN],
      version: SchemaVersionOption
  ): AvroSchemaDeterminer = {
    new BasedOnVersionAvroSchemaDeterminer(
      schemaRegistryClient,
      preparedTopic.prepared.toUnspecialized,
      version,
      isKey = false
    )
  }

  // TODO: add schema versioning for key schemas
  protected def prepareKeySchemaDeterminer(preparedTopic: PreparedKafkaTopic[TN]): AvroSchemaDeterminer = {
    new BasedOnVersionAvroSchemaDeterminer(
      schemaRegistryClient,
      preparedTopic.prepared.toUnspecialized,
      LatestSchemaVersion,
      isKey = true
    )
  }

  protected def prepareUniversalValueSchemaDeterminer(
      preparedTopic: PreparedKafkaTopic[TN],
      version: SchemaVersionOption
  ): ParsedSchemaDeterminer = {
    new ParsedSchemaDeterminer(
      schemaRegistryClient,
      preparedTopic.prepared.toUnspecialized,
      version,
      isKey = false
    )
  }

  // TODO: add schema versioning for key schemas
  protected def prepareUniversalKeySchemaDeterminer(preparedTopic: PreparedKafkaTopic[TN]): ParsedSchemaDeterminer = {
    new ParsedSchemaDeterminer(
      schemaRegistryClient,
      preparedTopic.prepared.toUnspecialized,
      LatestSchemaVersion,
      isKey = true
    )
  }

  protected def topicParamStep(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val topicParam = getTopicParam.map(List(_))
      NextParameters(parameters = topicParam.value.map(_.createParameter()), errors = topicParam.written)
  }

  protected def schemaParamStep(
      nextParams: List[Parameter]
  )(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep((topicParamName, DefinedEagerParameter(topic: String, _)) :: Nil, _) =>
      val preparedTopic = prepareTopic(topic)
      val versionParam  = getVersionParam(preparedTopic)
      val topicValidationErrors =
        validateTopic(preparedTopic.prepared).swap.toList.map(_.toCustomNodeError(nodeId.id, Some(topicParamName)))
      NextParameters(
        versionParam.value.createParameter() :: nextParams,
        errors = versionParam.written ++ topicValidationErrors
      )
    case TransformationStep((`topicParamName`, _) :: Nil, _) =>
      NextParameters(parameters = fallbackVersionOptionParam.createParameter() :: nextParams)
  }

  def paramsDeterminedAfterSchema: List[Parameter]

  // edge case - for some reason Topic is not defined
  @transient protected lazy val fallbackVersionOptionParam
      : ParameterCreatorWithNoDependency with ParameterExtractor[String] =
    getVersionParam(Nil)

  // override it if you use other parameter name for topic
  @transient protected lazy val topicParamName: ParameterName = KafkaUniversalComponentTransformer.topicParamName

  protected def getAllTopics: List[UnspecializedTopicName] = {
    val validatorConfig = kafkaConfig.topicsExistenceValidationConfig.validatorConfig

    KafkaUtils
      .usingAdminClient(kafkaConfig) {
        _.listTopics(new ListTopicsOptions().timeoutMs(validatorConfig.adminClientTimeout.toMillis.toInt))
          .names()
          .get()
          .asScala
          .toSet
          .map(UnspecializedTopicName.apply)
          .filterNot(topic => topic.name.startsWith("_"))
      }
      .toList
  }

}

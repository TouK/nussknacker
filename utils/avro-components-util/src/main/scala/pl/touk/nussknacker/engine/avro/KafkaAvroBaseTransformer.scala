package pl.touk.nussknacker.engine.avro

import cats.data.Validated.{Invalid, Valid}
import cats.data.Writer
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, Parameter}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer.TopicParamName
import pl.touk.nussknacker.engine.avro.schemaregistry._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.kafka.validator.WithCachedTopicsExistenceValidator
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils, PreparedKafkaTopic}

trait KafkaAvroBaseTransformer[T] extends SingleInputGenericNodeTransformation[T] with WithCachedTopicsExistenceValidator {

  // Initially we don't want to select concrete topic by user so we add null topic on the beginning of select box.
  // TODO: add addNullOption feature flag to FixedValuesParameterEditor
  val nullTopicOption: FixedExpressionValue = FixedExpressionValue("", "")

  type WithError[V] = Writer[List[ProcessCompilationError], V]

  def schemaRegistryProvider: SchemaRegistryProvider

  def processObjectDependencies: ProcessObjectDependencies

  @transient protected lazy val schemaRegistryClient: SchemaRegistryClient =
    schemaRegistryProvider.schemaRegistryClientFactory.create(kafkaConfig)

  protected def topicSelectionStrategy: TopicSelectionStrategy = new AllTopicsSelectionStrategy

  protected val kafkaConfig: KafkaConfig = prepareKafkaConfig

  protected def prepareKafkaConfig: KafkaConfig = {
    KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)
  }

  protected def getTopicParam(implicit nodeId: NodeId): WithError[Parameter] = {
    val topics = topicSelectionStrategy.getTopics(schemaRegistryClient)

    (topics match {
      case Valid(topics) => Writer[List[ProcessCompilationError], List[String]](Nil, topics)
      case Invalid(e) => Writer[List[ProcessCompilationError], List[String]](List(CustomNodeError(e.getMessage, Some(topicParamName))), Nil)
    }).map { topics =>
      getTopicParam(topics)
    }
  }

  private def getTopicParam(topics: List[String]): Parameter = {
    Parameter[String](topicParamName).copy(editor = Some(FixedValuesParameterEditor(
      nullTopicOption +: topics
        .flatMap(topic => processObjectDependencies.objectNaming.decodeName(topic, processObjectDependencies.config, KafkaUtils.KafkaTopicUsageKey))
        .sorted
        .map(v => FixedExpressionValue(s"'$v'", v))
    )))
  }

  protected def getVersionParam(preparedTopic: PreparedKafkaTopic)(implicit nodeId: NodeId): WithError[Parameter] = {
    val versions = schemaRegistryClient.getAllVersions(preparedTopic.prepared, isKey = false)
    (versions match {
      case Valid(versions) => Writer[List[ProcessCompilationError], List[Integer]](Nil, versions)
      case Invalid(e) => Writer[List[ProcessCompilationError], List[Integer]](List(CustomNodeError(e.getMessage, Some(topicParamName))), Nil)
    }).map(getVersionParam)
  }

  protected def getVersionParam(versions: List[Integer]): Parameter = {
    val versionValues = FixedExpressionValue(s"'${SchemaVersionOption.LatestOptionName}'", "Latest version") :: versions.sorted.map(v => FixedExpressionValue(s"'$v'", v.toString))
    Parameter[String](KafkaAvroBaseComponentTransformer.SchemaVersionParamName).copy(editor = Some(FixedValuesParameterEditor(versionValues)))
  }

  protected def extractPreparedTopic(params: Map[String, Any]): PreparedKafkaTopic = prepareTopic(
    params(topicParamName).asInstanceOf[String]
  )

  protected def extractVersionOption(params: Map[String, Any]): SchemaVersionOption = {
    val optionName = params(KafkaAvroBaseComponentTransformer.SchemaVersionParamName).asInstanceOf[String]
    SchemaVersionOption.byName(optionName)
  }

  protected def prepareTopic(topic: String): PreparedKafkaTopic =
    KafkaUtils.prepareKafkaTopic(topic, processObjectDependencies)

  protected def parseVersionOption(versionOptionName: String): SchemaVersionOption =
    SchemaVersionOption.byName(versionOptionName)

  protected def prepareValueSchemaDeterminer(preparedTopic: PreparedKafkaTopic, version: SchemaVersionOption): AvroSchemaDeterminer = {
    new BasedOnVersionAvroSchemaDeterminer(schemaRegistryClient, preparedTopic.prepared, version, isKey = false)
  }

  //TODO: add schema versioning for key schemas
  protected def prepareKeySchemaDeterminer(preparedTopic: PreparedKafkaTopic): AvroSchemaDeterminer = {
    new BasedOnVersionAvroSchemaDeterminer(schemaRegistryClient, preparedTopic.prepared, LatestSchemaVersion, isKey = true)
  }

  protected def topicParamStep(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val topicParam = getTopicParam.map(List(_))
      NextParameters(parameters = topicParam.value, errors = topicParam.written)
  }

  protected def schemaParamStep(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep((topicParamName, DefinedEagerParameter(topic: String, _)) :: Nil, _) =>
      val preparedTopic = prepareTopic(topic)
      val versionParam = getVersionParam(preparedTopic)
      val topicValidationErrors = validateTopic(preparedTopic.prepared).swap.toList.map(_.toCustomNodeError(nodeId.id, Some(topicParamName)))
      NextParameters(versionParam.value :: paramsDeterminedAfterSchema, errors = versionParam.written ++ topicValidationErrors)
    case TransformationStep((topicParamName, _) :: Nil, _) =>
      NextParameters(parameters = fallbackVersionOptionParam :: paramsDeterminedAfterSchema)
  }

  def paramsDeterminedAfterSchema: List[Parameter]

  //edge case - for some reason Topic is not defined
  protected val fallbackVersionOptionParam: Parameter = getVersionParam(Nil)

  // override it if you use other parameter name for topic
  protected val topicParamName: String = TopicParamName

}

package pl.touk.nussknacker.engine.avro

import cats.data.Validated.{Invalid, Valid}
import cats.data.Writer
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue, SingleInputGenericNodeTransformation, TypedNodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, Parameter}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.TopicParamName
import pl.touk.nussknacker.engine.avro.schemaregistry._
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils, PreparedKafkaTopic}

import scala.reflect.ClassTag

trait KafkaAvroBaseTransformer[T] extends SingleInputGenericNodeTransformation[T] {

  // Initially we don't want to select concrete topic by user so we add null topic on the beginning of select box.
  // TODO: add addNullOption feature flag to FixedValuesParameterEditor
  val nullTopicOption: FixedExpressionValue = FixedExpressionValue("", "")

  type WithError[V] = Writer[List[ProcessCompilationError], V]

  def schemaRegistryProvider: SchemaRegistryProvider


  def processObjectDependencies: ProcessObjectDependencies

  @transient protected lazy val schemaRegistryClient: SchemaRegistryClient = schemaRegistryProvider.createSchemaRegistryClient

  protected def topicSelectionStrategy: TopicSelectionStrategy = new AllTopicsSelectionStrategy

  protected val kafkaConfig: KafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)

  protected def getTopicParam(implicit nodeId: NodeId): WithError[Parameter] = {
    val topics = topicSelectionStrategy.getTopics(schemaRegistryClient)

    (topics match {
      case Valid(topics) => Writer[List[ProcessCompilationError], List[String]](Nil, topics)
      case Invalid(e) => Writer[List[ProcessCompilationError], List[String]](List(CustomNodeError(e.getMessage, Some(KafkaAvroBaseTransformer.TopicParamName))), Nil)
    }).map { topics =>
      getTopicParam(topics)
    }
  }

  private def getTopicParam(topics: List[String]): Parameter = {
    Parameter[String](KafkaAvroBaseTransformer.TopicParamName).copy(editor = Some(FixedValuesParameterEditor(
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
      case Invalid(e) => Writer[List[ProcessCompilationError], List[Integer]](List(CustomNodeError(e.getMessage, Some(KafkaAvroBaseTransformer.TopicParamName))), Nil)
    }).map(getVersionParam)
  }

  protected def getVersionParam(versions: List[Integer]): Parameter = {
    val versionValues = FixedExpressionValue(s"'${SchemaVersionOption.LatestOptionName}'", "Latest version") :: versions.sorted.map(v => FixedExpressionValue(s"'$v'", v.toString))
    Parameter[String](KafkaAvroBaseTransformer.SchemaVersionParamName).copy(editor = Some(FixedValuesParameterEditor(versionValues)))
  }

  protected def typedDependency[C:ClassTag](list: List[NodeDependencyValue]): C = list.collectFirst {
    case TypedNodeDependencyValue(value:C) => value
  }.getOrElse(throw new CustomNodeValidationException(s"No node dependency: ${implicitly[ClassTag[C]].runtimeClass}", None, null))

  protected def extractPreparedTopic(params: Map[String, Any]): PreparedKafkaTopic = prepareTopic(
    params(KafkaAvroBaseTransformer.TopicParamName).asInstanceOf[String]
  )

  protected def extractVersionOption(params: Map[String, Any]): SchemaVersionOption = {
    val optionName = params(KafkaAvroBaseTransformer.SchemaVersionParamName).asInstanceOf[String]
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
    val fallbackSchema = Schema.create(Schema.Type.STRING)
    new BasedOnVersionWithFallbackAvroSchemaDeterminer(schemaRegistryClient, preparedTopic.prepared, LatestSchemaVersion, isKey = true, fallbackSchema)
  }

  protected def topicParamStep(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val topicParam = getTopicParam.map(List(_))
      NextParameters(parameters = topicParam.value, errors = topicParam.written)
  }

  protected def schemaParamStep(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep((TopicParamName, DefinedEagerParameter(topic: String, _)) :: Nil, _) =>
      val preparedTopic = prepareTopic(topic)
      val versionParam = getVersionParam(preparedTopic)
      NextParameters(versionParam.value :: paramsDeterminedAfterSchema, errors = versionParam.written)
    case TransformationStep((TopicParamName, _) :: Nil, _) =>
      NextParameters(parameters = fallbackVersionOptionParam :: paramsDeterminedAfterSchema)
  }

  override def initialParameters: List[Parameter] = {
    implicit val nodeId: NodeId = NodeId("")
    val topic = getTopicParam.value
    topic :: getVersionParam(Nil) :: paramsDeterminedAfterSchema
  }

  def paramsDeterminedAfterSchema: List[Parameter]

  //edge case - for some reason Topic is not defined
  protected val fallbackVersionOptionParam: Parameter = getVersionParam(Nil)

}

object KafkaAvroBaseTransformer {

  final val SchemaVersionParamName = "Schema version"
  final val TopicParamName = "Topic"
  final val SinkKeyParamName = "Key"
  final val SinkValueParamName = "Value"
  final val SinkValidationModeParameterName = "Value validation mode"

}

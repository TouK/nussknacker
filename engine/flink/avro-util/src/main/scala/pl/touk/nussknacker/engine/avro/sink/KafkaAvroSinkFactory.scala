package pl.touk.nussknacker.engine.avro.sink

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.transformation.{BaseDefinedParameter, DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.{KafkaAvroBaseTransformer, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.{SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, SinkValueParamName, TopicParamName}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSink

object KafkaAvroSinkFactory {
  private val paramsDeterminedAfterSchema = List(
    Parameter[String](SinkValidationModeParameterName)
      .copy(editor = Some(FixedValuesParameterEditor(ValidationMode.values.map(ep => FixedExpressionValue(s"'${ep.name}'", ep.label))))),
    Parameter.optional[CharSequence](SinkKeyParamName).copy(isLazyParameter = true),
    Parameter[AnyRef](SinkValueParamName).copy(isLazyParameter = true)
  )
}

class KafkaAvroSinkFactory(val schemaRegistryProvider: SchemaRegistryProvider, val processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaAvroSinkFactory with KafkaAvroBaseTransformer[FlinkSink] {
  import KafkaAvroSinkFactory._

  protected def topicParamStep(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val topicParam = getTopicParam.map(List(_))
      NextParameters(topicParam.value, topicParam.written)
  }

  protected def schemaParamStep(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep((TopicParamName, DefinedEagerParameter(topic: String, _)) :: Nil, _) =>
      val preparedTopic = prepareTopic(topic)
      val version = getVersionParam(preparedTopic)
      NextParameters(List(version.value) ++ paramsDeterminedAfterSchema, version.written, None)
    case TransformationStep((TopicParamName, _) :: Nil, _) =>
      NextParameters(List(fallbackVersionOptionParam) ++ paramsDeterminedAfterSchema)
  }

  protected def finalParamStep(context: ValidationContext)(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(
    (TopicParamName, DefinedEagerParameter(topic: String, _)) ::
      (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::
      (SinkValidationModeParameterName, DefinedEagerParameter(mode: String, _)) ::
      (SinkKeyParamName, _: BaseDefinedParameter) ::
      (SinkValueParamName, value: BaseDefinedParameter) :: Nil, _
    ) =>
      //we cast here, since null will not be matched in case...
      val preparedTopic = prepareTopic(topic)
      val versionOption = parseVersionOption(version)
      val schemaDeterminer = prepareSchemaDeterminer(preparedTopic, versionOption)
      val validationResult = schemaDeterminer.determineSchemaUsedInTyping
        .leftMap(SchemaDeterminerErrorHandler.handleSchemaRegistryError)
        .andThen(schemaData => validateValueType(value.returnType, schemaData.schema, extractValidationMode(mode))).swap.toList
      FinalResults(context, validationResult)
    //edge case - for some reason Topic/Version is not defined
    case TransformationStep(
    (TopicParamName, _) ::
      (SchemaVersionParamName, _) ::
      (SinkValidationModeParameterName, _) ::
      (SinkKeyParamName, _) ::
      (SinkValueParamName, _) :: Nil, _
    ) => FinalResults(context, Nil)
  }

  override def initialParameters: List[Parameter] = {
    implicit val nodeId: NodeId = NodeId("")
    val topic = getTopicParam.value
    List(topic, getVersionParam(Nil)) ++ paramsDeterminedAfterSchema
  }

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: NodeId): NodeTransformationDefinition =
    topicParamStep orElse
      schemaParamStep orElse
        finalParamStep(context)

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): FlinkSink = {
    val preparedTopic = extractPreparedTopic(params)
    val versionOption = extractVersionOption(params)
    val key = params(SinkKeyParamName).asInstanceOf[LazyParameter[CharSequence]]
    val value = params(SinkValueParamName).asInstanceOf[LazyParameter[AnyRef]]
    val validationMode = extractValidationMode(params(SinkValidationModeParameterName).asInstanceOf[String])

    createSink(preparedTopic, versionOption, key, value,
      kafkaConfig, schemaRegistryProvider.serializationSchemaFactory, prepareSchemaDeterminer(preparedTopic, versionOption), validationMode)(
      typedDependency[MetaData](dependencies), typedDependency[NodeId](dependencies))
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]), TypedNodeDependency(classOf[NodeId]))

  private def extractValidationMode(value: String): ValidationMode
    = ValidationMode.byName(value).getOrElse(throw CustomNodeValidationException(s"Unknown validation mode: $value", Some(SinkValidationModeParameterName)))
}

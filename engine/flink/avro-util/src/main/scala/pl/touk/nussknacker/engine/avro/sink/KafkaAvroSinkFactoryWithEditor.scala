package pl.touk.nussknacker.engine.avro.sink

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory}
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.{KafkaAvroBaseTransformer, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.{SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, TopicParamName}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.flink.api.process.FlinkSink
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.sink.KafkaAvroSinkFactory.extractValidationMode


object KafkaAvroSinkFactoryWithEditor {

  private val paramsDeterminedAfterSchema = List(
    Parameter[String](KafkaAvroBaseTransformer.SinkValidationModeParameterName)
      .copy(editor = Some(FixedValuesParameterEditor(ValidationMode.values.map(ep => FixedExpressionValue(s"'${ep.name}'", ep.label))))),
    Parameter.optional[CharSequence](KafkaAvroBaseTransformer.SinkKeyParamName).copy(isLazyParameter = true)
  )
}

class KafkaAvroSinkFactoryWithEditor(val schemaRegistryProvider: SchemaRegistryProvider, val processObjectDependencies: ProcessObjectDependencies)
  extends SinkFactory with KafkaAvroBaseTransformer[FlinkSink] {
  import KafkaAvroSinkFactoryWithEditor._

  private var sinkValueParameter: Option[AvroSinkValueParameter] = None

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

  private def valueParamStep(context: ValidationContext)(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep
      (
        (TopicParamName, DefinedEagerParameter(topic: String, _)) ::
        (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::
        (SinkValidationModeParameterName, _) ::
        (SinkKeyParamName, _) :: Nil, _
      ) =>
      val preparedTopic = prepareTopic(topic)
      val versionOption = parseVersionOption(version)
      val schemaDeterminer = prepareSchemaDeterminer(preparedTopic, versionOption)
      val determinedSchema = schemaDeterminer
        .determineSchemaUsedInTyping
        .leftMap(SchemaDeterminerErrorHandler.handleSchemaRegistryError(_))

      (determinedSchema andThen validateSchema).andThen { runtimeSchema =>
        val typing = AvroSchemaTypeDefinitionExtractor.typeDefinition(runtimeSchema.schema)
        AvroSinkValueParameter(typing).map { valueParam =>
          sinkValueParameter = Some(valueParam)
          NextParameters(valueParam.toParameters)
        }
      }.valueOr(e => FinalResults(context, e :: Nil))
  }

  protected def finalParamStep(context: ValidationContext)(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(
      (TopicParamName, _) :: (SchemaVersionParamName, _) :: (SinkValidationModeParameterName, _) :: (SinkKeyParamName, _) ::
      valueParams, _) if valueParams.nonEmpty => FinalResults(context, Nil)
  }

  override def initialParameters: List[Parameter] = {
    implicit val nodeId: NodeId = NodeId("")
    val topic = getTopicParam.value
    topic :: getVersionParam(Nil) :: paramsDeterminedAfterSchema
  }

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: NodeId): NodeTransformationDefinition =
    topicParamStep orElse
      schemaParamStep orElse
        valueParamStep(context) orElse
          finalParamStep(context)

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): FlinkSink = {
    implicit val nodeId: NodeId = typedDependency[NodeId](dependencies)
    val preparedTopic = extractPreparedTopic(params)
    val versionOption = extractVersionOption(params)
    val key = params(SinkKeyParamName).asInstanceOf[LazyParameter[CharSequence]]
    val validationMode = extractValidationMode(params(SinkValidationModeParameterName).asInstanceOf[String])
    val sinkValue = AvroSinkValue.applyUnsafe(sinkValueParameter.get, parameterValues = params)
    val schemaDeterminer = prepareSchemaDeterminer(preparedTopic, versionOption)
    val schemaData = schemaDeterminer.determineSchemaUsedInTyping.valueOr(SchemaDeterminerErrorHandler.handleSchemaRegistryErrorAndThrowException)
    val schemaUsedInRuntime = schemaDeterminer.toRuntimeSchema(schemaData)
    val processMetaData = typedDependency[NodeId](dependencies)
    val clientId = s"${processMetaData.id}-${preparedTopic.prepared}"

    new KafkaAvroSink(preparedTopic, versionOption, key, sinkValue, kafkaConfig, schemaRegistryProvider.serializationSchemaFactory,
      schemaData.serializableSchema, schemaUsedInRuntime.map(_.serializableSchema), clientId, validationMode)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]), TypedNodeDependency(classOf[NodeId]))

  override def requiresOutput: Boolean = false
}

package pl.touk.nussknacker.engine.schemedkafka.sink

import cats.data.NonEmptyList
import io.confluent.kafka.schemaregistry.ParsedSchema
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, DefinedLazyParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData, NodeId}
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.UniversalSchemaSupport
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaBasedSerdeProvider, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory.TransformationState
import pl.touk.nussknacker.engine.schemedkafka.{KafkaUniversalComponentTransformer, RuntimeSchemaData, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValue
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.{SinkSingleValueParameter, SinkValueParameter}

/**
 * This is universal kafka sink - it will handle both avro and json
 * TODO: Move it to some other module when json schema handling will be available
 */
object UniversalKafkaSinkFactory {


  private val paramsDeterminedAfterSchema = List(
    Parameter.optional[CharSequence](SinkKeyParamName).copy(isLazyParameter = true),
    Parameter[Boolean](SinkRawEditorParamName).copy(defaultValue = Some("false"), editor = Some(BoolParameterEditor), validators = List(MandatoryParameterValidator))
  )

  case class TransformationState(schema: RuntimeSchemaData[ParsedSchema], sinkValueParameter: SinkValueParameter)
}

class UniversalKafkaSinkFactory(val schemaRegistryClientFactory: SchemaRegistryClientFactory,
                                val schemaBasedMessagesSerdeProvider: SchemaBasedSerdeProvider,
                                val processObjectDependencies: ProcessObjectDependencies,
                                implProvider: UniversalKafkaSinkImplFactory)
  extends KafkaUniversalComponentTransformer[Sink] with SinkFactory {

  override type State = TransformationState

  override def paramsDeterminedAfterSchema: List[Parameter] = UniversalKafkaSinkFactory.paramsDeterminedAfterSchema

  private val rawValueParam: Parameter = Parameter[AnyRef](SinkValueParamName).copy(isLazyParameter = true)
  private val validationModeParam = Parameter[String](SinkValidationModeParameterName).copy(editor = Some(FixedValuesParameterEditor(ValidationMode.values.map(ep => FixedExpressionValue(s"'${ep.name}'", ep.label)))))

  protected def rawEditorParameterStep(context: ValidationContext)
                                      (implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(
    (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
      (SchemaVersionParamName, DefinedEagerParameter(_: String, _)) ::
      (SinkKeyParamName, _) ::
      (SinkRawEditorParamName, DefinedEagerParameter(true, _)) :: Nil, _) =>
        //TODO: use resultType here to show type hints on UI (breaks some tests...)
        NextParameters(validationModeParam :: rawValueParam :: Nil)
    case TransformationStep(
    (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
      (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::
      (SinkKeyParamName, _) ::
      (SinkRawEditorParamName, DefinedEagerParameter(true, _)) ::
      (SinkValidationModeParameterName, DefinedEagerParameter(mode: String, _)) ::
      (SinkValueParamName, value: DefinedLazyParameter) :: Nil, _
    ) =>
      getSchema(topic, version)
        .andThen(schemaBasedMessagesSerdeProvider.validateSchema(_).leftMap(_.map(e => CustomNodeError(nodeId.id, e.getMessage, None))))
        .map { schema =>
          val validationMode = ValidationMode.fromString(mode, SinkValidationModeParameterName)
          val validator = UniversalSchemaSupport.forSchemaType(schema.schema.schemaType()).parameterValidator(schema.schema, validationMode)
          val valueParam: SinkValueParameter = SinkSingleValueParameter(rawValueParam, validator)

          val validationResult = valueParam.validateParams((SinkValueParamName, value) :: Nil, Nil)
          val state = TransformationState(schema, valueParam)
          FinalResults.forValidation(context, validationResult, Option(state))
        }.valueOr(e => FinalResults(context, e.toList))
    //edge case - for some reason Topic/Version is not defined
    case TransformationStep((`topicParamName`, _) :: (SchemaVersionParamName, _) :: (SinkKeyParamName, _) :: (SinkRawEditorParamName, _) ::
      (SinkValidationModeParameterName, _) :: (SinkValueParamName, _) :: Nil, _) => FinalResults(context, Nil)
  }

  private def valueEditorParamStep(context: ValidationContext)(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep
      (
      (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
        (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::
        (SinkKeyParamName, _) ::
        (SinkRawEditorParamName, DefinedEagerParameter(false, _)) :: Nil, _
      ) =>
      val determinedSchema = getSchema(topic, version)
      val validatedSchema = determinedSchema.andThen { s =>
        schemaBasedMessagesSerdeProvider.validateSchema(s)
          .leftMap(_.map(e => CustomNodeError(nodeId.id, e.getMessage, None)))
      }
      validatedSchema.andThen { schemaData =>
        UniversalSchemaSupport.forSchemaType(schemaData.schema.schemaType())
          .extractSinkValueParameter(schemaData.schema)
          .map { valueParam =>
          val state = TransformationState(schemaData, valueParam)
          //shouldn't happen except for empty schema, but it can lead to infinite loop...
          if (valueParam.toParameters.isEmpty) {
            FinalResults(context, Nil, Some(state))
          } else {
            NextParameters(valueParam.toParameters, state = Option(state))
          }
        }
      }.valueOr(e => FinalResults(context, e.toList))
    case TransformationStep((`topicParamName`, _) :: (SchemaVersionParamName, _) :: (SinkKeyParamName, _) :: (SinkRawEditorParamName, DefinedEagerParameter(false, _)) :: valueParams, state) =>
      val validationErrors = state.get.sinkValueParameter.validateParams(valueParams, Nil)
      FinalResults.forValidation(context, validationErrors, state)
  }

  private def getSchema(topic: String, version: String)(implicit nodeId: NodeId) = {
    val preparedTopic = prepareTopic(topic)
    val versionOption = parseVersionOption(version)
    val schemaDeterminer = prepareUniversalValueSchemaDeterminer(preparedTopic, versionOption)
    schemaDeterminer
      .determineSchemaUsedInTyping
      .leftMap(SchemaDeterminerErrorHandler.handleSchemaRegistryError(_))
      .leftMap(NonEmptyList.one)
  }

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: NodeId): NodeTransformationDefinition =
    topicParamStep orElse
      schemaParamStep orElse
      rawEditorParameterStep(context) orElse
      valueEditorParamStep(context)

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalStateOpt: Option[State]): Sink = {
    val preparedTopic = extractPreparedTopic(params)
    val key = params(SinkKeyParamName).asInstanceOf[LazyParameter[CharSequence]]
    val finalState = finalStateOpt.getOrElse(throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation"))

    val sinkValue = SinkValue.applyUnsafe(finalState.sinkValueParameter, parameterValues = params)
    val valueLazyParam = sinkValue.toLazyParameter

    val serializationSchema = schemaBasedMessagesSerdeProvider.serializationSchemaFactory.create(preparedTopic.prepared, Option(finalState.schema), kafkaConfig)
    val clientId = s"${TypedNodeDependency[MetaData].extract(dependencies).id}-${preparedTopic.prepared}"
    val validationMode = extractValidationMode(params.getOrElse(SinkValidationModeParameterName, ValidationMode.strict.name).asInstanceOf[String])

    implProvider.createSink(preparedTopic, key, valueLazyParam, kafkaConfig, serializationSchema, clientId, finalState.schema, validationMode)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[MetaData], TypedNodeDependency[NodeId])

}

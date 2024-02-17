package pl.touk.nussknacker.engine.schemedkafka.sink

import cats.data.NonEmptyList
import io.confluent.kafka.schemaregistry.ParsedSchema
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  BaseDefinedParameter,
  DefinedEagerParameter,
  NodeDependencyValue
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData, NodeId, Params}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaBasedSerdeProvider, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory.TransformationState
import pl.touk.nussknacker.engine.schemedkafka.{
  KafkaUniversalComponentTransformer,
  RuntimeSchemaData,
  SchemaDeterminerErrorHandler
}
import pl.touk.nussknacker.engine.util.parameters.SchemaBasedParameter
import pl.touk.nussknacker.engine.util.parameters.SchemaBasedParameter.ParameterName
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValue

/**
 * This is universal kafka sink - it will handle both avro and json
 * TODO: Move it to some other module when json schema handling will be available
 */
object UniversalKafkaSinkFactory {

  private val paramsDeterminedAfterSchema = List(
    Parameter.optional[CharSequence](SinkKeyParamName).copy(isLazyParameter = true),
    Parameter[Boolean](SinkRawEditorParamName).copy(
      defaultValue = Some(Expression.spel("false")),
      editor = Some(BoolParameterEditor),
      validators = List(MandatoryParameterValidator)
    )
  )

  case class TransformationState(schema: RuntimeSchemaData[ParsedSchema], schemaBasedParameter: SchemaBasedParameter)
}

class UniversalKafkaSinkFactory(
    val schemaRegistryClientFactory: SchemaRegistryClientFactory,
    val schemaBasedMessagesSerdeProvider: SchemaBasedSerdeProvider,
    val modelDependencies: ProcessObjectDependencies,
    implProvider: UniversalKafkaSinkImplFactory
) extends KafkaUniversalComponentTransformer[Sink]
    with SinkFactory {

  override type State = TransformationState

  override def paramsDeterminedAfterSchema: List[Parameter] = UniversalKafkaSinkFactory.paramsDeterminedAfterSchema

  private val rawValueParam: Parameter = Parameter[AnyRef](SinkValueParamName).copy(isLazyParameter = true)

  private val validationModeParam = Parameter[String](SinkValidationModeParameterName).copy(editor =
    Some(FixedValuesParameterEditor(ValidationMode.values.map(ep => FixedExpressionValue(s"'${ep.name}'", ep.label))))
  )

  private val restrictedParamNames: Set[ParameterName] = Set(
    topicParamName,
    SchemaVersionParamName,
    SinkKeyParamName,
    SinkRawEditorParamName,
    SinkValidationModeParameterName
  )

  protected def rawEditorParameterStep(
      context: ValidationContext
  )(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep(
          (`topicParamName`, _) :: (SchemaVersionParamName, _) :: (SinkKeyParamName, _) :: (
            SinkRawEditorParamName,
            DefinedEagerParameter(true, _)
          ) :: Nil,
          _
        ) =>
      NextParameters(validationModeParam :: rawValueParam :: Nil)
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::
          (SinkKeyParamName, _) ::
          (SinkRawEditorParamName, _) ::
          (SinkValidationModeParameterName, DefinedEagerParameter(mode: String, _)) ::
          (SinkValueParamName, value: BaseDefinedParameter) :: Nil,
          _
        ) =>
      getSchema(topic, version)
        .andThen { runtimeSchemaData =>
          schemaBasedMessagesSerdeProvider.schemaValidator
            .validateSchema(runtimeSchemaData.schema)
            .map(_ => runtimeSchemaData)
            .leftMap(_.map(e => CustomNodeError(nodeId.id, e.getMessage, None)))
        }
        .andThen { runtimeSchemaData =>
          schemaSupportDispatcher
            .forSchemaType(runtimeSchemaData.schema.schemaType())
            .extractParameter(
              runtimeSchemaData.schema,
              rawMode = true,
              validationMode = extractValidationMode(mode),
              rawParameter = rawValueParam,
              restrictedParamNames
            )
            .map { extractedSinkParameter =>
              val validationAgainstSchemaErrors = extractedSinkParameter
                .validateParams(Map(SinkValueParamName -> value))
                .swap
                .map(_.toList)
                .getOrElse(List.empty)
              FinalResults(
                context,
                validationAgainstSchemaErrors,
                Some(TransformationState(runtimeSchemaData, extractedSinkParameter))
              )
            }
        }
        .valueOr(e => FinalResults(context, e.toList))
    // edge case - for some reason Topic/Version is not defined
    case TransformationStep(
          (`topicParamName`, _) :: (SchemaVersionParamName, _) :: (SinkKeyParamName, _) :: (
            SinkRawEditorParamName,
            _
          ) ::
          (SinkValidationModeParameterName, _) :: (SinkValueParamName, _) :: Nil,
          _
        ) =>
      FinalResults(context, Nil)
  }

  private def valueEditorParamStep(
      context: ValidationContext
  )(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::
          (SinkKeyParamName, _) ::
          (SinkRawEditorParamName, DefinedEagerParameter(false, _)) :: Nil,
          _
        ) =>
      val determinedSchema = getSchema(topic, version)
      val validatedSchema = determinedSchema.andThen { s =>
        schemaBasedMessagesSerdeProvider.schemaValidator
          .validateSchema(s.schema)
          .map(_ => s)
          .leftMap(_.map(e => CustomNodeError(nodeId.id, e.getMessage, None)))
      }
      validatedSchema
        .andThen { schemaData =>
          schemaSupportDispatcher
            .forSchemaType(schemaData.schema.schemaType())
            .extractParameter(
              schemaData.schema,
              rawMode = false,
              validationMode = ValidationMode.lax,
              rawValueParam,
              restrictedParamNames
            )
            .map { valueParam =>
              val state = TransformationState(schemaData, valueParam)
              // shouldn't happen except for empty schema, but it can lead to infinite loop...
              if (valueParam.toParameters.isEmpty) {
                FinalResults(context, Nil, Some(state))
              } else {
                NextParameters(valueParam.toParameters, state = Some(state))
              }
            }
        }
        .valueOr(e => FinalResults(context, e.toList))
    case TransformationStep(
          (`topicParamName`, _) :: (SchemaVersionParamName, _) :: (SinkKeyParamName, _) :: (
            SinkRawEditorParamName,
            DefinedEagerParameter(false, _)
          ) :: valueParams,
          Some(state)
        ) =>
      val errors = state.schemaBasedParameter.validateParams(valueParams.toMap).swap.map(_.toList).getOrElse(Nil)
      FinalResults(context, errors, Some(state))
  }

  private def getSchema(topic: String, version: String)(implicit nodeId: NodeId) = {
    val preparedTopic    = prepareTopic(topic)
    val versionOption    = parseVersionOption(version)
    val schemaDeterminer = prepareUniversalValueSchemaDeterminer(preparedTopic, versionOption)
    schemaDeterminer.determineSchemaUsedInTyping
      .leftMap(SchemaDeterminerErrorHandler.handleSchemaRegistryError(_))
      .leftMap(NonEmptyList.one)
  }

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition =
    topicParamStep orElse
      schemaParamStep orElse
      rawEditorParameterStep(context) orElse
      valueEditorParamStep(context)

  override def runComponentLogic(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalStateOpt: Option[State]
  ): Sink = {
    val preparedTopic = extractPreparedTopic(params)
    val key           = params.extractUnsafe[LazyParameter[CharSequence]](SinkKeyParamName)
    val finalState = finalStateOpt.getOrElse(
      throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation")
    )

    val sinkValue      = SinkValue.applyUnsafe(finalState.schemaBasedParameter, parameterValues = params)
    val valueLazyParam = sinkValue.toLazyParameter

    val serializationSchema = schemaBasedMessagesSerdeProvider.serializationSchemaFactory.create(
      preparedTopic.prepared,
      Option(finalState.schema),
      kafkaConfig
    )
    val clientId = s"${TypedNodeDependency[MetaData].extract(dependencies).name}-${preparedTopic.prepared}"
    val validationMode = extractValidationMode(
      params.extract[String](SinkValidationModeParameterName).getOrElse(ValidationMode.strict.name)
    )

    implProvider.createSink(
      preparedTopic,
      key,
      valueLazyParam,
      kafkaConfig,
      serializationSchema,
      clientId,
      finalState.schema,
      validationMode
    )
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[MetaData], TypedNodeDependency[NodeId])

}

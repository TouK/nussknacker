package pl.touk.nussknacker.engine.schemedkafka.sink

import cats.data.{NonEmptyList, ValidatedNel}
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.flink.formats.avro.typeutils.NkSerializableParsedSchema
import pl.touk.nussknacker.engine.ModelConfig.JsonLikeValuesEnteringMode
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData, NodeId, Params}
import pl.touk.nussknacker.engine.api.Params.ParamExtractionResult
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.transformation.{
  BaseDefinedParameter,
  DefinedEagerParameter,
  FailedToDefineParameter,
  NodeDependencyValue
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory, TopicName}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.KafkaComponentsConfig
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.{
  KafkaUniversalComponentTransformer,
  RuntimeSchemaData,
  SchemaRegistryErrorHandler
}
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  ContentTypes,
  ContentTypesSchemas,
  SchemaRegistryClientFactory
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory.TransformationState
import pl.touk.nussknacker.engine.util.parameters.{SchemaBasedParameter, SingleSchemaBasedParameter}
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValue

/**
 * This is universal kafka sink - it will handle both avro and json
 */
object UniversalKafkaSinkFactory {

  private val paramsDeterminedAfterSchema = List(
    Parameter.optional[CharSequence](sinkKeyParamName).copy(isLazyParameter = true),
  )

  case class TransformationState(schema: RuntimeSchemaData[ParsedSchema], schemaBasedParameter: SchemaBasedParameter) {

    def validateParamsForDynamicForms(params: Map[ParameterName, BaseDefinedParameter])(
        implicit nodeId: NodeId
    ): List[ProcessCompilationError] = {
      validateParams(params, detailedErrorDescriptions = false)
    }

    def validateParamsForRawEditor(params: Map[ParameterName, BaseDefinedParameter])(
        implicit nodeId: NodeId
    ): List[ProcessCompilationError] = {
      validateParams(params, detailedErrorDescriptions = true)
    }

    private def validateParams(params: Map[ParameterName, BaseDefinedParameter], detailedErrorDescriptions: Boolean)(
        implicit nodeId: NodeId
    ): List[ProcessCompilationError] = {
      schemaBasedParameter
        .validateParams(params, detailedErrorDescriptions = detailedErrorDescriptions)
        .swap
        .map(_.toList)
        .getOrElse(List.empty)
    }

  }

}

class UniversalKafkaSinkFactory(
    val schemaRegistryClientFactory: SchemaRegistryClientFactory,
    val schemaBasedMessagesSerdeProvider: UniversalSchemaBasedSerdeProvider,
    val kafkaComponentsConfig: KafkaComponentsConfig,
    val namingStrategy: NamingStrategy,
    jsonLikeValuesEnteringMode: JsonLikeValuesEnteringMode,
    implProvider: UniversalKafkaSinkImplFactory
) extends KafkaUniversalComponentTransformer[TopicName.ForSink]
    with SinkFactory {

  override type Implementation = Sink

  override type State = TransformationState

  override def paramsDeterminedAfterSchema: List[Parameter] = UniversalKafkaSinkFactory.paramsDeterminedAfterSchema

  private val rawValueParamDeclaration = ParameterDeclaration.lazyMandatory[AnyRef](sinkValueParamName).withCreator()

  private val sinkRawEditorParam = Parameter[Boolean](sinkRawEditorParamName).copy(
    defaultValue = Some(Expression.spel("false")),
    editors = List(BoolParameterEditor),
    validators = List(MandatoryParameterValidator)
  )

  private val validationModeParamDeclaration =
    ParameterDeclaration
      .mandatory[String](sinkValidationModeParamName)
      .withCreator(
        modify = _.copy(editors =
          List(
            FixedValuesParameterEditor(ValidationMode.values.map(ep => FixedExpressionValue(s"'${ep.name}'", ep.label)))
          )
        )
      )

  private val restrictedParamNames: Set[ParameterName] = Set(
    topicParamName,
    schemaVersionParamName,
    sinkKeyParamName,
    sinkRawEditorParamName,
    sinkValidationModeParamName,
    contentTypeParamName
  )

  private lazy val jsonSchema =
    RuntimeSchemaData(new NkSerializableParsedSchema[ParsedSchema](ContentTypesSchemas.schemaForJson), None)
  private lazy val plainSchema =
    RuntimeSchemaData(new NkSerializableParsedSchema[ParsedSchema](ContentTypesSchemas.schemaForPlain), None)

  override protected def topicFrom(value: String): TopicName.ForSink = TopicName.ForSink(value)

  private def handleInvalidTopicParam(
      context: ValidationContext
  ): ContextTransformationDefinition = {
    case TransformationStep(
          (`topicParamName`, FailedToDefineParameter(errors)) ::
          (`contentTypeParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkKeyParamName`, _) :: Nil,
          _
        ) =>
      FinalResults(context, errors.toList)
    case TransformationStep(
          (`topicParamName`, FailedToDefineParameter(errors)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkKeyParamName`, _) :: Nil,
          _
        ) =>
      FinalResults(context, errors.toList)
  }

  protected def handleSinkValueForSchemalessTopic(
      context: ValidationContext
  )(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`contentTypeParamName`, DefinedEagerParameter(contentType: String, _)) ::
          (`sinkKeyParamName`, _) :: Nil,
          _
        ) =>
      val schemaData = runtimeSchemaDataForContentType(contentType)
      extractSingleParameterForSchema(
        schemaData = schemaData,
        validationMode = ValidationMode.lax,
      )
        .map { schemaBasedParameter =>
          val state = TransformationState(schemaData, schemaBasedParameter)
          NextParameters(schemaBasedParameter.value :: Nil, state = Some(state))
        }
        .valueOr { errors =>
          FinalResults(context, errors.toList)
        }
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`contentTypeParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkValueParamName`, value: BaseDefinedParameter) :: Nil,
          Some(state)
        ) =>
      val validationAgainstSchemaErrors = state.validateParamsForRawEditor(Map(sinkValueParamName -> value))
      FinalResults(
        context,
        validationAgainstSchemaErrors,
        Some(state)
      )
  }

  private def handleSinkValueForSchemedTopic(
      context: ValidationContext,
  )(implicit nodeId: NodeId): ContextTransformationDefinition = {
    jsonLikeValuesEnteringMode match {
      case JsonLikeValuesEnteringMode.DynamicForms =>
        handleDynamicFormParameters(context)
      case JsonLikeValuesEnteringMode.SingleJsonTemplateParameter =>
        handleSingleJsonTemplateParameter(context)
    }
  }

  private def handleDynamicFormParameters(
      context: ValidationContext
  )(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkKeyParamName`, _) :: Nil,
          _
        ) =>
      NextParameters(sinkRawEditorParam :: Nil)
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(version: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, DefinedEagerParameter(false, _)) :: Nil,
          _
        ) =>
      getSchema(topic, version)
        .andThen { schemaData =>
          extractDynamicParametersForSchema(schemaData)
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
        .valueOr { errors =>
          FinalResults(context, errors.toList)
        }
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, DefinedEagerParameter(false, _)) ::
          valueParams,
          Some(state)
        ) =>
      val errors = state.validateParamsForDynamicForms(valueParams.toMap)
      FinalResults(context, errors, Some(state))
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, DefinedEagerParameter(true, _)) :: Nil,
          _
        ) =>
      NextParameters(validationModeParamDeclaration.createParameter() :: Nil)
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(version: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, DefinedEagerParameter(true, _)) ::
          (`sinkValidationModeParamName`, DefinedEagerParameter(mode: String, _)) :: Nil,
          _
        ) =>
      getSchema(topic, version)
        .andThen(schema =>
          extractSingleParameterForSchema(
            schemaData = schema,
            validationMode = extractValidationMode(mode),
          )
            .map { param =>
              NextParameters(param.value :: Nil, state = Some(TransformationState(schema, param)))
            }
        )
        .valueOr { errors =>
          FinalResults(context, errors.toList)
        }
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, DefinedEagerParameter(true, _)) ::
          (`sinkValidationModeParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkValueParamName`, value: BaseDefinedParameter) :: Nil,
          Some(state)
        ) =>
      val validationAgainstSchemaErrors = state.validateParamsForRawEditor(Map(sinkValueParamName -> value))
      FinalResults(
        context,
        validationAgainstSchemaErrors,
        Some(state)
      )
  }

  private def handleSingleJsonTemplateParameter(
      context: ValidationContext
  )(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkKeyParamName`, _) :: Nil,
          _
        ) =>
      NextParameters(validationModeParamDeclaration.createParameter() :: Nil)
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(version: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkValidationModeParamName`, DefinedEagerParameter(mode: String, _)) :: Nil,
          _
        ) =>
      getSchema(topic, version)
        .andThen(schema =>
          extractSingleParameterForSchema(
            schemaData = schema,
            validationMode = extractValidationMode(mode),
          )
            .map { param =>
              NextParameters(param.value :: Nil, state = Some(TransformationState(schema, param)))
            }
        )
        .valueOr { errors =>
          FinalResults(context, errors.toList)
        }
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkValidationModeParamName`, _) ::
          (`sinkValueParamName`, value: BaseDefinedParameter) :: Nil,
          Some(state)
        ) =>
      val errors = state.validateParamsForRawEditor(Map(sinkValueParamName -> value))
      FinalResults(context, errors, Some(state))
  }

  private def extractSingleParameterForSchema(
      schemaData: RuntimeSchemaData[ParsedSchema],
      validationMode: ValidationMode,
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SingleSchemaBasedParameter] = {
    schemaSupportDispatcher
      .forParsedSchema(schemaData.schema)
      .extractSingleParameterForSink(
        schema = schemaData.schema,
        validationMode = validationMode,
        rawParameter = rawValueParamDeclaration.createParameter(),
      )
  }

  private def extractDynamicParametersForSchema(
      schemaData: RuntimeSchemaData[ParsedSchema],
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] = {
    schemaSupportDispatcher
      .forParsedSchema(schemaData.schema)
      .extractDynamicParametersForSink(
        schema = schemaData.schema,
        restrictedParamNames = restrictedParamNames
      )
  }

  private def getSchema(topic: String, version: String)(implicit nodeId: NodeId) = {
    val preparedTopic = prepareTopic(topic)
    val versionOption = parseVersionOption(version)
    schemaRegistryClient
      .getFreshSchema(preparedTopic.prepared.toUnspecialized, versionOption, isKey = false)
      .map(withMetadata =>
        RuntimeSchemaData(new NkSerializableParsedSchema[ParsedSchema](withMetadata.schema), Some(withMetadata.id))
      )
      .leftMap(SchemaRegistryErrorHandler.handleSchemaRegistryError(_))
      .leftMap(NonEmptyList.one)
  }

  override def contextTransformation(inputContext: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition =
    topicParamStep(inputContext, dependencies) orElse
      schemaParamStep(paramsDeterminedAfterSchema) orElse
      handleInvalidTopicParam(inputContext) orElse
      handleSinkValueForSchemalessTopic(inputContext) orElse
      handleSinkValueForSchemedTopic(inputContext)

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalStateOpt: Option[State]
  ): Sink = {
    val preparedTopic = extractPreparedTopic(params)
    val key           = params.extractDeclaredParamUnsafe[LazyParameter[CharSequence]](sinkKeyParamName)
    val finalState = finalStateOpt.getOrElse(
      throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation")
    )

    val sinkValue      = SinkValue.applyUnsafe(finalState.schemaBasedParameter, parameterValues = params)
    val valueLazyParam = sinkValue.toLazyParameter

    val serializationSchema = schemaBasedMessagesSerdeProvider.serializationSchemaFactory.create(
      preparedTopic.prepared,
      finalState.schema,
    )

    val clientId = s"${TypedNodeDependency[MetaData].extract(dependencies).name}-${preparedTopic.prepared}"
    val validationMode = jsonLikeValuesEnteringMode match {
      case JsonLikeValuesEnteringMode.DynamicForms =>
        params.extractParam[Boolean](sinkRawEditorParamName) match {
          case ParamExtractionResult.Value(true) =>
            val validationModeString = validationModeParamDeclaration.extractValueUnsafe(params)
            extractValidationMode(validationModeString)
          case ParamExtractionResult.Value(false) =>
            ValidationMode.strict
          case ParamExtractionResult.MissingParam =>
            // Sink validation mode does not apply to schemaless topics; in this case, lax validation for an empty schema is sufficient
            ValidationMode.lax
          case ParamExtractionResult.ParamValueIsNone =>
            throw new IllegalArgumentException(
              s"Parameter [${sinkRawEditorParamName.value}] doesn't expect to be null!"
            )
        }
      case JsonLikeValuesEnteringMode.SingleJsonTemplateParameter =>
        params.extractParam[String](sinkValidationModeParamName) match {
          case ParamExtractionResult.Value(validationModeString) =>
            extractValidationMode(validationModeString)
          case ParamExtractionResult.ParamValueIsNone =>
            throw new IllegalArgumentException(
              s"Parameter [${sinkValidationModeParamName.value}] doesn't expect to be null!"
            )
          case ParamExtractionResult.MissingParam =>
            // Sink validation mode does not apply to schemaless topics; in this case, lax validation for an empty schema is sufficient
            ValidationMode.lax
        }
    }

    implProvider.createSink(
      key,
      sinkKeyParamName,
      valueLazyParam,
      kafkaComponentsConfig,
      serializationSchema,
      clientId,
      finalState.schema,
      validationMode
    )
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[MetaData], TypedNodeDependency[NodeId])

  override def allowedProcessingModes: AllowedProcessingModes =
    AllowedProcessingModes.SetOf(ProcessingMode.UnboundedStream, ProcessingMode.BoundedStream)

  private def runtimeSchemaDataForContentType(contentType: String): RuntimeSchemaData[ParsedSchema] = {
    if (contentType.equals(ContentTypes.JSON.toString)) { jsonSchema }
    else if (contentType.equals(ContentTypes.PLAIN.toString)) { plainSchema }
    else { throw new IllegalStateException("Content Type should be JSON or PLAIN, is neither") }
  }

}

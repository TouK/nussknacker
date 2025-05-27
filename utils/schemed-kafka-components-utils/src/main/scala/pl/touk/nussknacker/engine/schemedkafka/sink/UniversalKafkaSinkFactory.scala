package pl.touk.nussknacker.engine.schemedkafka.sink

import cats.data.{NonEmptyList, ValidatedNel}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.avro.generic.GenericRecord
import org.apache.flink.formats.avro.typeutils.NkSerializableParsedSchema
import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData, NodeId, Params}
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{
  BaseDefinedParameter,
  DefinedEagerParameter,
  FailedToDefineParameter,
  NodeDependencyValue
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory, TopicName}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.schemedkafka.{
  KafkaUniversalComponentTransformer,
  RuntimeSchemaData,
  SchemaDeterminerErrorHandler
}
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  ContentTypes,
  ContentTypesSchemas,
  SchemaBasedSerdeProvider,
  SchemaRegistryClientFactory
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaSupport.ParameterExtractionMode
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory.TransformationState
import pl.touk.nussknacker.engine.util.parameters.{SchemaBasedParameter, SingleSchemaBasedParameter}
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValue

/**
 * This is universal kafka sink - it will handle both avro and json
 */
object UniversalKafkaSinkFactory {

  private val genericRecordClass = classOf[GenericRecord]

  private val paramsDeterminedAfterSchema = List(
    Parameter.optional[CharSequence](sinkKeyParamName).copy(isLazyParameter = true),
  )

  case class TransformationState(schema: RuntimeSchemaData[ParsedSchema], schemaBasedParameter: SchemaBasedParameter) {

    def validateParams(params: Map[ParameterName, BaseDefinedParameter])(
        implicit nodeId: NodeId
    ): List[ProcessCompilationError] =
      schemaBasedParameter.validateParams(params).swap.map(_.toList).getOrElse(List.empty)

  }

}

class UniversalKafkaSinkFactory(
    val schemaRegistryClientFactory: SchemaRegistryClientFactory,
    val schemaBasedMessagesSerdeProvider: SchemaBasedSerdeProvider,
    val modelConfig: ModelConfig,
    implProvider: UniversalKafkaSinkImplFactory
) extends KafkaUniversalComponentTransformer[Sink, TopicName.ForSink]
    with SinkFactory {

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

  protected def rawParameterTemplateStep(
      context: ValidationContext
  )(implicit nodeId: NodeId): ContextTransformationDefinition = {
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
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`contentTypeParamName`, DefinedEagerParameter(contentType: String, _)) ::
          (`sinkKeyParamName`, _) :: Nil,
          _
        ) if modelConfig.enableSingleParameterWithTemplateInsteadOfDynamicForm =>
      val schemaData = runtimeSchemaDataForContentType(contentType)
      extractSingleParameterForSchema(
        schemaData = schemaData,
        parameterExtractionMode = ParameterExtractionMode.RawParameterTemplate,
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
          (`sinkKeyParamName`, _) :: Nil,
          _
        ) =>
      NextParameters(sinkRawEditorParam :: Nil)
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkKeyParamName`, _) :: Nil,
          _
        ) if modelConfig.enableSingleParameterWithTemplateInsteadOfDynamicForm =>
      NextParameters(validationModeParamDeclaration.createParameter() :: Nil)
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(version: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkValidationModeParamName`, DefinedEagerParameter(mode: String, _)) :: Nil,
          _
        ) =>
      validateSchema(topic, version)
        .andThen(schema =>
          extractSingleParameterForSchema(
            schema,
            parameterExtractionMode = ParameterExtractionMode.RawParameterTemplate,
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
          (`sinkKeyParamName`, _) :: Nil,
          _
        ) =>
      NextParameters(sinkRawEditorParam :: Nil)
  }

  protected def rawEditorParameterStep(
      context: ValidationContext
  )(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`contentTypeParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, DefinedEagerParameter(true, _)) :: Nil,
          _
        ) =>
      NextParameters(validationModeParamDeclaration.createParameter() :: Nil)
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`contentTypeParamName`, DefinedEagerParameter(contentType: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, DefinedEagerParameter(true, _)) ::
          (`sinkValidationModeParamName`, DefinedEagerParameter(mode: String, _)) :: Nil,
          _
        ) =>
      val runtimeSchemaData = runtimeSchemaDataForContentType(contentType)
      extractSingleParameterForSchema(
        runtimeSchemaData,
        ParameterExtractionMode.RawParameter,
        extractValidationMode(mode),
      ).map { schemaParam =>
        NextParameters(
          schemaParam.value :: Nil,
          state = Some(TransformationState(runtimeSchemaData, schemaParam))
        )
      }.valueOr { errors =>
        FinalResults(context, errors.toList)
      }

    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, DefinedEagerParameter(true, _)) :: Nil,
          _
        ) =>
      NextParameters(
        validationModeParamDeclaration.createParameter() :: Nil
      )
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(version: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, DefinedEagerParameter(true, _)) ::
          (`sinkValidationModeParamName`, DefinedEagerParameter(mode: String, _)) :: Nil,
          _
        ) =>
      validateSchema(topic, version)
        .andThen { runtimeSchemaData =>
          extractSingleParameterForSchema(
            runtimeSchemaData,
            ParameterExtractionMode.RawParameter,
            extractValidationMode(mode),
          )
            .map { param =>
              NextParameters(param.value :: Nil, state = Some(TransformationState(runtimeSchemaData, param)))
            }
        }
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
      val validationAgainstSchemaErrors = state.validateParams(Map(sinkValueParamName -> value))
      FinalResults(
        context,
        validationAgainstSchemaErrors,
        Some(state)
      )

    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`contentTypeParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, DefinedEagerParameter(true, _)) ::
          (`sinkValidationModeParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkValueParamName`, value: BaseDefinedParameter) :: Nil,
          Some(state)
        ) =>
      val validationAgainstSchemaErrors = state.validateParams(Map(sinkValueParamName -> value))
      FinalResults(
        context,
        validationAgainstSchemaErrors,
        Some(state)
      )
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, DefinedEagerParameter(true, _)) ::
          (`sinkValidationModeParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkValueParamName`, _) :: Nil,
          _
        ) =>
      FinalResults(context, Nil)
  }

  private def valueEditorParamStep(
      context: ValidationContext
  )(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(version: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, DefinedEagerParameter(false, _)) :: Nil,
          _
        ) =>
      validateSchema(topic, version)
        .andThen { schemaData =>
          extractParametersForSchema(schemaData)
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
          (`contentTypeParamName`, DefinedEagerParameter(contentType: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, DefinedEagerParameter(false, _)) :: Nil,
          _
        ) =>
      val schemaData = runtimeSchemaDataForContentType(contentType)
      extractParametersForSchema(schemaData)
        .map { valueParam =>
          val state = TransformationState(schemaData, valueParam)
          // shouldn't happen except for empty schema, but it can lead to infinite loop...
          if (valueParam.toParameters.isEmpty) {
            FinalResults(context, Nil, Some(state))
          } else {
            NextParameters(valueParam.toParameters, state = Some(state))
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
      val errors = state.validateParams(valueParams.toMap)
      FinalResults(context, errors, Some(state))
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkValidationModeParamName`, _) ::
          (`sinkValueParamName`, value: BaseDefinedParameter) :: Nil,
          Some(state)
        ) =>
      val errors = state.validateParams(Map(sinkValueParamName -> value))
      FinalResults(context, errors, Some(state))
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`contentTypeParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, DefinedEagerParameter(false, _)) ::
          valueParams,
          Some(state)
        ) =>
      val errors = state.validateParams(valueParams.toMap)
      FinalResults(context, errors, Some(state))
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`contentTypeParamName`, DefinedEagerParameter(_: String, _)) ::
          (`sinkKeyParamName`, _) ::
          valueParams,
          Some(state)
        ) =>
      val errors = state.validateParams(valueParams.toMap)
      FinalResults(context, errors, Some(state))
  }

  private def extractSingleParameterForSchema(
      schemaData: RuntimeSchemaData[ParsedSchema],
      parameterExtractionMode: ParameterExtractionMode,
      validationMode: ValidationMode,
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SingleSchemaBasedParameter] = {
    schemaSupportDispatcher
      .forSchemaType(schemaData.schema.schemaType())
      .extractSingleParameterForSink(
        schema = schemaData.schema,
        parameterExtractionMode = parameterExtractionMode,
        validationMode = validationMode,
        rawParameter = rawValueParamDeclaration.createParameter(),
      )
  }

  private def extractParametersForSchema(
      schemaData: RuntimeSchemaData[ParsedSchema],
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] = {
    schemaSupportDispatcher
      .forSchemaType(schemaData.schema.schemaType())
      .extractParametersForSink(
        schema = schemaData.schema,
        restrictedParamNames = restrictedParamNames
      )
  }

  private def validateSchema(topic: String, version: String)(implicit nodeId: NodeId) = {
    val determinedSchema = getSchema(topic, version)
    determinedSchema.andThen { schema =>
      schemaBasedMessagesSerdeProvider.schemaValidator
        .validateSchema(schema.schema)
        .map(_ => schema)
        .leftMap(_.map(e => CustomNodeError(e.getMessage, None)))
    }
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
      schemaParamStep(paramsDeterminedAfterSchema) orElse
      rawParameterTemplateStep(context) orElse
      rawEditorParameterStep(context) orElse
      valueEditorParamStep(context)

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalStateOpt: Option[State]
  ): Sink = {
    val preparedTopic = extractPreparedTopic(params)
    val key           = params.extractUnsafe[LazyParameter[CharSequence]](sinkKeyParamName)
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
    val validationMode = {
      if (params.isPresent(sinkRawEditorParamName)) {
        if (params.extractUnsafe[Boolean](sinkRawEditorParamName)) {
          validationModeParamDeclaration.extractValue(params) match {
            case Some(validationModeString) => extractValidationMode(validationModeString)
            case None                       => ValidationMode.strict
          }
        } else {
          ValidationMode.strict
        }
      } else if (params.isPresent(sinkValidationModeParamName)) {
        validationModeParamDeclaration.extractValue(params) match {
          case Some(validationModeString) => extractValidationMode(validationModeString)
          case None                       => ValidationMode.strict
        }
      } else {
        ValidationMode.lax
      }
    }

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

  override def allowedProcessingModes: AllowedProcessingModes =
    AllowedProcessingModes.SetOf(ProcessingMode.UnboundedStream, ProcessingMode.BoundedStream)

  private def runtimeSchemaDataForContentType(contentType: String): RuntimeSchemaData[ParsedSchema] = {
    if (contentType.equals(ContentTypes.JSON.toString)) { jsonSchema }
    else if (contentType.equals(ContentTypes.PLAIN.toString)) { plainSchema }
    else { throw new IllegalStateException("Content Type should be JSON or PLAIN, is neither") }
  }

}

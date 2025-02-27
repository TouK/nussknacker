package pl.touk.nussknacker.engine.schemedkafka.sink

import cats.data.NonEmptyList
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.flink.formats.avro.typeutils.NkSerializableParsedSchema
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData, NodeId, Params}
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  BaseDefinedParameter,
  DefinedEagerParameter,
  NodeDependencyValue
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink, SinkFactory, TopicName}
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
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory.TransformationState
import pl.touk.nussknacker.engine.util.parameters.SchemaBasedParameter
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValue

/**
 * This is universal kafka sink - it will handle both avro and json
 * TODO: Move it to some other module when json schema handling will be available
 */
object UniversalKafkaSinkFactory {

  private val paramsDeterminedAfterSchema = List(
    Parameter.optional[CharSequence](sinkKeyParamName).copy(isLazyParameter = true),
    Parameter[Boolean](sinkRawEditorParamName).copy(
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
) extends KafkaUniversalComponentTransformer[Sink, TopicName.ForSink]
    with SinkFactory {

  override type State = TransformationState

  override def paramsDeterminedAfterSchema: List[Parameter] = UniversalKafkaSinkFactory.paramsDeterminedAfterSchema

  private val rawValueParamDeclaration = ParameterDeclaration.lazyMandatory[AnyRef](sinkValueParamName).withCreator()

  private val validationModeParamDeclaration =
    ParameterDeclaration
      .mandatory[String](sinkValidationModeParamName)
      .withCreator(
        modify = _.copy(editor =
          Some(
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

  protected def rawEditorParameterStep(
      context: ValidationContext
  )(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep(
          (`topicParamName`, _) ::
          (`contentTypeParamName`, _) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, DefinedEagerParameter(true, _)) :: Nil,
          _
        ) =>
      NextParameters(
        validationModeParamDeclaration.createParameter() :: rawValueParamDeclaration.createParameter() :: Nil
      )
    case TransformationStep(
          (`topicParamName`, _) ::
          (`schemaVersionParamName`, _) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, DefinedEagerParameter(true, _)) :: Nil,
          _
        ) =>
      NextParameters(
        validationModeParamDeclaration.createParameter() :: rawValueParamDeclaration.createParameter() :: Nil
      )
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(version: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, _) ::
          (`sinkValidationModeParamName`, DefinedEagerParameter(mode: String, _)) ::
          (`sinkValueParamName`, value: BaseDefinedParameter) :: Nil,
          _
        ) =>
      // edge case - for some reason Topic/Version is not defined
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
              rawParameter = rawValueParamDeclaration.createParameter(),
              restrictedParamNames
            )
            .map { extractedSinkParameter =>
              val validationAgainstSchemaErrors = extractedSinkParameter
                .validateParams(Map(sinkValueParamName -> value))
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
        .valueOr { errors =>
          FinalResults(context, errors.toList)
        }
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`contentTypeParamName`, DefinedEagerParameter(contentType: String, _)) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, _) ::
          (`sinkValidationModeParamName`, DefinedEagerParameter(mode: String, _)) ::
          (`sinkValueParamName`, value: BaseDefinedParameter) :: Nil,
          _
        ) =>
      val runtimeSchemaData = runtimeSchemaDataForContentType(contentType)
      schemaSupportDispatcher
        .forSchemaType(runtimeSchemaData.schema.schemaType())
        .extractParameter(
          runtimeSchemaData.schema,
          rawMode = true,
          validationMode = extractValidationMode(mode),
          rawParameter = rawValueParamDeclaration.createParameter(),
          restrictedParamNames
        )
        .map { extractedSinkParameter =>
          val validationAgainstSchemaErrors = extractedSinkParameter
            .validateParams(Map(sinkValueParamName -> value))
            .swap
            .map(_.toList)
            .getOrElse(List.empty)
          FinalResults(
            context,
            validationAgainstSchemaErrors,
            Some(TransformationState(runtimeSchemaData, extractedSinkParameter))
          )
        }
        .valueOr { errors =>
          FinalResults(context, errors.toList)
        }
    case TransformationStep(
          (`topicParamName`, _) ::
          (`schemaVersionParamName`, _) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, _) ::
          (`sinkValidationModeParamName`, _) ::
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
      val determinedSchema = getSchema(topic, version)
      val validatedSchema = determinedSchema.andThen { schema =>
        schemaBasedMessagesSerdeProvider.schemaValidator
          .validateSchema(schema.schema)
          .map(_ => schema)
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
              rawValueParamDeclaration.createParameter(),
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

      schemaSupportDispatcher
        .forSchemaType(schemaData.schema.schemaType())
        .extractParameter(
          schemaData.schema,
          rawMode = false,
          validationMode = ValidationMode.lax,
          rawValueParamDeclaration.createParameter(),
          restrictedParamNames
        )
        .map[TransformationStepResult] { valueParam =>
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
          (`topicParamName`, _) ::
          (`schemaVersionParamName`, _) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, DefinedEagerParameter(false, _)) ::
          valueParams,
          Some(state)
        ) =>
      val errors = state.schemaBasedParameter.validateParams(valueParams.toMap).swap.map(_.toList).getOrElse(Nil)
      FinalResults(context, errors, Some(state))
    case TransformationStep(
          (`topicParamName`, _) ::
          (`contentTypeParamName`, _) ::
          (`sinkKeyParamName`, _) ::
          (`sinkRawEditorParamName`, DefinedEagerParameter(false, _)) ::
          valueParams,
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
      schemaParamStep(paramsDeterminedAfterSchema) orElse
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
    val validationMode = if (params.extractUnsafe[Boolean](sinkRawEditorParamName)) {
      validationModeParamDeclaration.extractValue(params) match {
        case Some(validationModeString) => extractValidationMode(validationModeString)
        case None                       => ValidationMode.strict
      }
    } else {
      ValidationMode.strict
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

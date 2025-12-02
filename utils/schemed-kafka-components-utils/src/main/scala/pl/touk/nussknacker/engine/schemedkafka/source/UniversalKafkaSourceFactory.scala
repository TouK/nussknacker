package pl.touk.nussknacker.engine.schemedkafka.source

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.Valid
import cats.implicits.catsSyntaxTuple2Semigroupal
import io.circe.Json
import io.circe.syntax._
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.avro.generic.GenericData.EnumSymbol
import org.apache.avro.generic.GenericRecord
import org.apache.flink.formats.avro.typeutils.NkSerializableParsedSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, Params}
import pl.touk.nussknacker.engine.api.Params.ParamExtractionResult
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  DefinedSingleParameter,
  NodeDependencyValue
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.TestRecord
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult, Unknown}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.{KafkaComponentsConfig, PreparedKafkaTopic}
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.kafka.consumerrecord.SerializableConsumerRecord
import pl.touk.nussknacker.engine.kafka.source._
import pl.touk.nussknacker.engine.schemedkafka.{KafkaUniversalComponentTransformer, RuntimeSchemaData}
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{
  inputParamName,
  schemaVersionParamName
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.formatter.SchemaBasedSerializableConsumerRecord
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.{
  UniversalSchemaBasedSerdeProvider,
  UniversalSchemaSupport
}
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory._
import pl.touk.nussknacker.engine.schemedkafka.typed.TypingResultFromJsonSampleTypeDeterminer
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.watermarkstrategy.WatermarkStrategyValidationHandler

/**
  * This is universal kafka source - it will handle both avro and json
  * TODO: Move it to some other module when json schema handling will be available
  */
class UniversalKafkaSourceFactory(
    val schemaRegistryClientFactory: SchemaRegistryClientFactory,
    val schemaBasedMessagesSerdeProvider: UniversalSchemaBasedSerdeProvider,
    val kafkaComponentsConfig: KafkaComponentsConfig,
    val namingStrategy: NamingStrategy,
    protected val implProvider: KafkaSourceImplFactory[Any, Any],
) extends KafkaUniversalComponentTransformer[TopicName.ForSource]
    with WatermarkStrategyValidationHandler
    with SourceFactory
    with WithExplicitTypesToExtract
    with UnboundedStreamComponent {

  override type Implementation = Source

  override type State = UniversalKafkaSourceFactoryState

  private val eventTimeDefaultValueExpression: Expression = "#inputMeta.timestamp".spel

  override protected val maxOutOfOrdernessDefaultValueExpression: Expression = {
    val seconds = kafkaComponentsConfig.defaultMaxOutOfOrdernessMillis.toSeconds
    s"T(java.time.Duration).parse('PT${seconds}S')".spel
  }

  override protected val idlenessDefaultValueExpression: Expression =
    kafkaComponentsConfig.idleTimeoutDuration
      .map(_.toSeconds)
      .map(seconds => s"T(java.time.Duration).parse('PT${seconds}S')".spel)
      .getOrElse("".spel)

  override protected val splitName: String = "partition"

  override val typesToExtract: List[TypedClass] =
    Typed.typedClass[GenericRecord] :: Typed.typedClass[TimestampType] :: Typed.typedClass[EnumSymbol] :: Nil

  override def contextTransformation(inputContext: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition =
    topicParamStep(inputContext, dependencies) orElse
      schemaParamStep(Nil) orElse
      afterSchemaParamStep(paramsDeterminedAfterSchema) orElse
      nextSteps(inputContext, dependencies) orElse
      watermarkStrategyParametersStep(inputContext, dependencies)

  protected def nextSteps(inputContext: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case step @ TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (`contentTypeParamName`, DefinedEagerParameter(contentType: String, _)) ::
          (`dataSampleParamName`, DefinedEagerParameter(_, typingResult)) :: Nil,
          _
        ) if contentType == ContentTypes.JSON.toString =>
      val preparedTopic          = prepareTopic(topic)
      val dataSampleTypingResult = TypingResultFromJsonSampleTypeDeterminer(typingResult)
      val valueValidationResult = Valid(
        (
          Some(runtimeDataForJsonSchema),
          dataSampleTypingResult
        )
      )
      val validFinalState = prepareFinalState(
        preparedTopic,
        valueValidationResult,
        Some(dataSampleTypingResult),
        dependencies,
        step.parameters
      )
      NextParameters(
        prepareWatermarkStrategyParameters(
          outputValidationOrEmpty(inputContext, validFinalState),
          eventTimeDefaultValueExpression
        ),
        state = Some(
          BeforeWatermarkStrategyParametersStepKafkaSourceFactoryState(validFinalState)
        )
      )
    case step @ TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (`contentTypeParamName`, DefinedEagerParameter(contentType: String, _)) :: Nil,
          _
        ) if contentType == ContentTypes.JSON.toString =>
      val preparedTopic         = prepareTopic(topic)
      val valueValidationResult = Valid((Some(runtimeDataForJsonSchema), Typed.json))
      val validFinalState = prepareFinalState(preparedTopic, valueValidationResult, None, dependencies, step.parameters)

      NextParameters(
        prepareWatermarkStrategyParameters(
          outputValidationOrEmpty(inputContext, validFinalState),
          eventTimeDefaultValueExpression
        ),
        state = Some(BeforeWatermarkStrategyParametersStepKafkaSourceFactoryState(validFinalState))
      )
    case step @ TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (`contentTypeParamName`, DefinedEagerParameter(contentType: String, _)) :: Nil,
          _
        ) if contentType == ContentTypes.PLAIN.toString =>
      val preparedTopic         = prepareTopic(topic)
      val valueValidationResult = Valid((Some(runtimeDataForPlainSchema), Typed[String]))
      val validFinalState = prepareFinalState(preparedTopic, valueValidationResult, None, dependencies, step.parameters)
      NextParameters(
        prepareWatermarkStrategyParameters(
          outputValidationOrEmpty(inputContext, validFinalState),
          eventTimeDefaultValueExpression
        ),
        state = Some(BeforeWatermarkStrategyParametersStepKafkaSourceFactoryState(validFinalState))
      )
    case step @ TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(version: String, _)) :: Nil,
          state
        ) =>
      val preparedTopic = prepareTopic(topic)
      val versionOption = parseVersionOption(version)
      val valueValidationResult =
        state match {
          case Some(PrecalculatedValueSchemaUniversalKafkaSourceFactoryState(results)) => results
          case _ =>
            determineSchemaAndType(
              preparedTopic,
              versionOption,
              isKey = false,
              Some(schemaVersionParamName)
            )
        }
      val validFinalState = prepareFinalState(preparedTopic, valueValidationResult, None, dependencies, step.parameters)

      NextParameters(
        prepareWatermarkStrategyParameters(
          outputValidationOrEmpty(inputContext, validFinalState),
          eventTimeDefaultValueExpression
        ),
        state = Some(BeforeWatermarkStrategyParametersStepKafkaSourceFactoryState(validFinalState))
      )
    case step @ TransformationStep((`topicParamName`, _) :: (`schemaVersionParamName`, _) :: Nil, _) =>
      // Edge case - for some reason Topic/Version is not defined, e.g. when topic or version does not match DefinedEagerParameter(String, _):
      // 1. FailedToDefineParameter
      // 2. not resolved as a valid String
      // Those errors are identified by parameter validation and handled elsewhere, hence empty list of errors.
      prepareSourceFinalErrors(inputContext, dependencies, step.parameters, errors = Nil)
  }

  private def outputValidationOrEmpty(
      inputContext: ValidationContext,
      validFinalState: ValidatedNel[ProcessCompilationError, ImplementationUniversalKafkaSourceFactoryState]
  )(implicit nodeId: NodeId) = {
    validFinalState.andThen(_.contextInitializer.validationContext(inputContext)).getOrElse(ValidationContext.empty)
  }

  protected def determineSchemaAndType(
      preparedKafkaTopic: PreparedKafkaTopic[TopicName.ForSource],
      versionOption: SchemaVersionOption,
      isKey: Boolean,
      paramName: Option[ParameterName]
  )(
      implicit nodeId: NodeId
  ): Validated[ProcessCompilationError, (Option[RuntimeSchemaData[ParsedSchema]], TypingResult)] = {
    schemaRegistryClient
      .getFreshSchema(preparedKafkaTopic.prepared.toUnspecialized, versionOption, isKey = isKey)
      .map { withMetadata =>
        val schemaData =
          RuntimeSchemaData(new NkSerializableParsedSchema[ParsedSchema](withMetadata.schema), Some(withMetadata.id))
        val schema = schemaData.schema
        (Some(schemaData), schemaSupportDispatcher.forParsedSchema(schema).typeDefinition(schema))
      }
      .leftMap(error => CustomNodeError(error.getMessage, paramName))
  }

  override protected def resultAfterWatermarkStrategyParameters(
      inputContext: ValidationContext,
      dependencies: List[NodeDependencyValue]
  )(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(
          parameters,
          Some(BeforeWatermarkStrategyParametersStepKafkaSourceFactoryState(validFinalState))
        ) =>
      prepareSourceFinalResults(validFinalState, inputContext, dependencies, parameters)
  }

  private def prepareFinalState(
      preparedTopic: PreparedKafkaTopic[TopicName.ForSource],
      valueValidationResult: Validated[
        ProcessCompilationError,
        (Option[RuntimeSchemaData[ParsedSchema]], TypingResult)
      ],
      dataSampleTypingResult: Option[TypingResult],
      dependencies: List[NodeDependencyValue],
      parameters: List[(ParameterName, DefinedParameter)]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ImplementationUniversalKafkaSourceFactoryState] = {
    val keyValidationResult = if (kafkaComponentsConfig.useStringForKey) {
      Valid((None, Typed[String]))
    } else {
      determineSchemaAndType(
        preparedTopic,
        LatestSchemaVersion,
        isKey = true,
        Some(topicParamName)
      )
    }
    (keyValidationResult.toValidatedNel, valueValidationResult.toValidatedNel).mapN {
      case ((keyRuntimeSchema, keyType), (valueRuntimeSchema, valueType)) =>
        val finalInitializer = prepareContextInitializer(dependencies, parameters, keyType, valueType)
        ImplementationUniversalKafkaSourceFactoryState(
          keyRuntimeSchema,
          valueRuntimeSchema,
          finalInitializer,
          dataSampleTypingResult
        )
    }
  }

  // Source specific FinalResults
  protected def prepareSourceFinalResults(
      validFinalState: ValidatedNel[ProcessCompilationError, ImplementationUniversalKafkaSourceFactoryState],
      inputContext: ValidationContext,
      dependencies: List[NodeDependencyValue],
      parameters: List[(ParameterName, DefinedParameter)],
  )(implicit nodeId: NodeId): FinalResults = {
    validFinalState
      .map { finalState =>
        FinalResults.forValidation(inputContext, List.empty, Some(finalState))(
          finalState.contextInitializer.validationContext
        )
      }
      .valueOr { errors =>
        prepareSourceFinalErrors(
          context = inputContext,
          dependencies = dependencies,
          parameters = parameters,
          errors = errors.toList
        )
      }
  }

  // Source specific FinalResults with errors
  protected def prepareSourceFinalErrors(
      context: ValidationContext,
      dependencies: List[NodeDependencyValue],
      parameters: List[(ParameterName, DefinedParameter)],
      errors: List[ProcessCompilationError]
  )(implicit nodeId: NodeId): FinalResults = {
    val initializerWithUnknown = prepareContextInitializer(dependencies, parameters, Unknown, Unknown)
    FinalResults.forValidation(context, errors)(initializerWithUnknown.validationContext)
  }

  // Overwrite this for dynamic type definitions.
  protected def prepareContextInitializer(
      dependencies: List[NodeDependencyValue],
      parameters: List[(ParameterName, DefinedParameter)],
      keyTypingResult: TypingResult,
      valueTypingResult: TypingResult
  ): ContextInitializer[ConsumerRecord[Any, Any]] =
    new KafkaContextInitializer[Any, Any](
      OutputVariableNameDependency.extract(dependencies),
      keyTypingResult,
      valueTypingResult,
      namingStrategy
    )

  override def paramsDeterminedAfterSchema: List[Parameter] = Nil

  override protected def topicFrom(value: String): TopicName.ForSource = TopicName.ForSource(value)

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): Source = {
    implicit val nodeId: NodeId = TypedNodeDependency[NodeId].extract(dependencies)

    val preparedTopic = extractPreparedTopic(params)
    val ImplementationUniversalKafkaSourceFactoryState(
      keySchemaDataUsedInRuntime,
      valueSchemaUsedInRuntime,
      kafkaContextInitializer,
      dataSampleTypingResult
    ) = finalState.get

    // prepare KafkaDeserializationSchema based on given key and value schema (with schema evolution)
    val deserializationSchema = schemaBasedMessagesSerdeProvider.deserializationSchemaFactory
      .create[Any, Any](keySchemaDataUsedInRuntime, valueSchemaUsedInRuntime, dataSampleTypingResult)

    val recordFormatter = schemaBasedMessagesSerdeProvider.recordFormatterFactory.create(schemaRegistryClient)

    val defaultValuesForTestParameters: Map[ParameterName, Expression] = {
      params.extractParam[Json](dataSampleParamName) match {
        case ParamExtractionResult.MissingParam | ParamExtractionResult.ParamValueIsNone => Map.empty
        case ParamExtractionResult.Value(dataSample) => Map(inputParamName -> Expression.json(dataSample.spaces2))
      }
    }

    implProvider.createSource(
      params,
      dependencies,
      finalState.get,
      NonEmptyList.one(preparedTopic),
      kafkaComponentsConfig,
      deserializationSchema,
      recordFormatter,
      kafkaContextInitializer,
      prepareKafkaTestParametersInfo(valueSchemaUsedInRuntime, preparedTopic.original, defaultValuesForTestParameters),
      namingStrategy,
      extractWatermarkStrategyOptions(params)
    )
  }

  private def prepareKafkaTestParametersInfo(
      runtimeSchemaOpt: Option[RuntimeSchemaData[ParsedSchema]],
      topic: TopicName.ForSource,
      defaultValuesForTestParameters: Map[ParameterName, Expression]
  )(
      implicit nodeId: NodeId
  ): KafkaTestParametersInfo = {
    Validated
      .fromOption(
        runtimeSchemaOpt,
        NonEmptyList.one(CustomNodeError("Cannot generate test parameters: no runtime schema found", None))
      )
      .andThen { runtimeSchema =>
        val parsedSchema                                   = runtimeSchema.schema
        val universalSchemaSupport: UniversalSchemaSupport = schemaSupportDispatcher.forParsedSchema(parsedSchema)

        universalSchemaSupport
          .extractParameterForTests(parsedSchema)
          .map(_.toParameters)
          .map { params =>
            val enrichedParams = params.map {
              case param if defaultValuesForTestParameters.contains(param.name) =>
                param.copy(
                  defaultValue = Some(defaultValuesForTestParameters(param.name))
                )
              case other => other
            }
            KafkaTestParametersInfo(enrichedParams, prepareTestRecord(runtimeSchema, universalSchemaSupport, topic))
          }
      }
      .valueOr(e => throw new RuntimeException(e.toList.mkString("")))
  }

  private def prepareTestRecord(
      runtimeSchema: RuntimeSchemaData[ParsedSchema],
      universalSchemaSupport: UniversalSchemaSupport,
      topic: TopicName.ForSource
  ): Any => TestRecord = any => {
    val json = universalSchemaSupport.prepareMessageFormatter(runtimeSchema.schema, schemaRegistryClient)(any)
    val serializedConsumerRecord =
      SerializableConsumerRecord[Json, Json](None, json, Some(topic.name), None, None, None, None, None, None)
    TestRecord(
      SchemaBasedSerializableConsumerRecord[Json, Json](
        None,
        runtimeSchema.schemaIdOpt,
        serializedConsumerRecord
      ).asJson
    )
  }

  @transient protected lazy val dataSampleParamName: ParameterName =
    KafkaUniversalComponentTransformer.dataSampleParamName

  private def afterSchemaParamStep(
      nextParams: List[Parameter]
  ): ContextTransformationDefinition = {
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`contentTypeParamName`, DefinedEagerParameter(contentType: String, _)) :: Nil,
          _
        )
        if contentType == ContentTypes.JSON.toString && kafkaComponentsConfig.useDataSampleParamForSchemalessJsonTopicBasedKafkaSource =>
      val dataSampleParam = getJsonDataSampleParam
      NextParameters(parameters = dataSampleParam.createParameter() :: nextParams)
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`contentTypeParamName`, DefinedEagerParameter(_: String, _)) :: Nil,
          _
        ) if nextParams.nonEmpty =>
      NextParameters(parameters = nextParams)
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(_: String, _)) :: Nil,
          _
        ) if nextParams.nonEmpty =>
      NextParameters(parameters = nextParams)
  }

  private def getJsonDataSampleParam: ParameterCreatorWithNoDependency with ParameterExtractor[Any] = {
    ParameterDeclaration
      .optional[Any](dataSampleParamName)
      .withCreator(
        modify = _.copy(
          typ = Typed.json,
          editors = List(JsonParameterEditor),
          defaultValue = Some(Expression.json("{}")),
          hintText = Some(
            "Provide an example JSON data sample. It will be analyzed to determine field types and generate a schema for easier data access in the subsequent nodes."
          ),
          nonImportantForExecution = true,
          // This is a workaround for "topic" flickering. We should define dependencies between parameters instead
          changesCanReloadParameters = Some(false)
        )
      )
  }

  // TODO: ADT allowing to pass either information about schema or about ContentType
  private def runtimeDataForPlainSchema = {
    RuntimeSchemaData[ParsedSchema](
      new NkSerializableParsedSchema[ParsedSchema](ContentTypesSchemas.schemaForPlain),
      Some(SchemaId.fromString(ContentTypes.PLAIN.toString))
    )
  }

  private def runtimeDataForJsonSchema = {
    RuntimeSchemaData[ParsedSchema](
      new NkSerializableParsedSchema[ParsedSchema](ContentTypesSchemas.schemaForJson),
      Some(SchemaId.fromString(ContentTypes.JSON.toString))
    )
  }

  override def nodeDependencies: List[NodeDependency] =
    List(TypedNodeDependency[MetaData], TypedNodeDependency[NodeId], OutputVariableNameDependency)

}

object UniversalKafkaSourceFactory {

  sealed trait UniversalKafkaSourceFactoryState

  private case class BeforeWatermarkStrategyParametersStepKafkaSourceFactoryState(
      validFinalState: ValidatedNel[ProcessCompilationError, ImplementationUniversalKafkaSourceFactoryState]
  ) extends UniversalKafkaSourceFactoryState

  case class ImplementationUniversalKafkaSourceFactoryState(
      keySchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      valueSchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      contextInitializer: ContextInitializer[ConsumerRecord[Any, Any]],
      dataSampleTypingResult: Option[TypingResult]
  ) extends UniversalKafkaSourceFactoryState

  // Used by external project
  case class PrecalculatedValueSchemaUniversalKafkaSourceFactoryState(
      valueValidationResult: Validated[ProcessCompilationError, (Option[RuntimeSchemaData[ParsedSchema]], TypingResult)]
  ) extends UniversalKafkaSourceFactoryState

}

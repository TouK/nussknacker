package pl.touk.nussknacker.engine.schemedkafka.source

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.Valid
import io.circe.Json
import io.circe.syntax._
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.avro.generic.GenericRecord
import org.apache.flink.formats.avro.typeutils.NkSerializableParsedSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, Params}
import pl.touk.nussknacker.engine.api.Params.ParamExtractionResult
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
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
) extends KafkaUniversalComponentTransformer[Source, TopicName.ForSource]
    with SourceFactory
    with WithExplicitTypesToExtract
    with UnboundedStreamComponent {

  override type State = UniversalKafkaSourceFactoryState

  override def typesToExtract: List[TypedClass] =
    Typed.typedClass[GenericRecord] :: Typed.typedClass[TimestampType] :: Nil

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition =
    topicParamStep orElse
      schemaParamStep(Nil) orElse
      afterSchemaParamStep(paramsDeterminedAfterSchema) orElse
      nextSteps(context, dependencies)

  protected def nextSteps(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case step @ TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (`contentTypeParamName`, DefinedEagerParameter(contentType: String, _)) ::
          (`dataSampleParamName`, DefinedEagerParameter(_, typingResult)) :: _,
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
      prepareSourceFinalResults(
        preparedTopic,
        valueValidationResult,
        Some(dataSampleTypingResult),
        context,
        dependencies,
        step.parameters,
        Nil
      )
    case step @ TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (`contentTypeParamName`, DefinedEagerParameter(contentType: String, _)) :: _,
          _
        ) if contentType == ContentTypes.JSON.toString =>
      val preparedTopic = prepareTopic(topic)
      prepareSourceFinalResults(
        preparedTopic,
        Valid((Some(runtimeDataForJsonSchema), Typed.json)),
        None,
        context,
        dependencies,
        step.parameters,
        Nil
      )
    case step @ TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (`contentTypeParamName`, DefinedEagerParameter(contentType: String, _)) :: _,
          _
        ) if contentType == ContentTypes.PLAIN.toString =>
      val preparedTopic = prepareTopic(topic)
      prepareSourceFinalResults(
        preparedTopic,
        Valid((Some(runtimeDataForPlainSchema), Typed[String])),
        None,
        context,
        dependencies,
        step.parameters,
        Nil
      )
    case step @ TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(version: String, _)) :: _,
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

      prepareSourceFinalResults(preparedTopic, valueValidationResult, None, context, dependencies, step.parameters, Nil)
    case step @ TransformationStep((`topicParamName`, _) :: (`schemaVersionParamName`, _) :: _, _) =>
      // Edge case - for some reason Topic/Version is not defined, e.g. when topic or version does not match DefinedEagerParameter(String, _):
      // 1. FailedToDefineParameter
      // 2. not resolved as a valid String
      // Those errors are identified by parameter validation and handled elsewhere, hence empty list of errors.
      prepareSourceFinalErrors(context, dependencies, step.parameters, errors = Nil)
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

  // Source specific FinalResults
  protected def prepareSourceFinalResults(
      preparedTopic: PreparedKafkaTopic[TopicName.ForSource],
      valueValidationResult: Validated[
        ProcessCompilationError,
        (Option[RuntimeSchemaData[ParsedSchema]], TypingResult)
      ],
      dataSampleTypingResult: Option[TypingResult],
      context: ValidationContext,
      dependencies: List[NodeDependencyValue],
      parameters: List[(ParameterName, DefinedParameter)],
      errors: List[ProcessCompilationError]
  )(implicit nodeId: NodeId): FinalResults = {
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

    (keyValidationResult, valueValidationResult) match {
      case (Valid((keyRuntimeSchema, keyType)), Valid((valueRuntimeSchema, valueType))) =>
        val finalInitializer = prepareContextInitializer(dependencies, parameters, keyType, valueType)
        val finalState =
          ImplementationUniversalKafkaSourceFactoryState(
            keyRuntimeSchema,
            valueRuntimeSchema,
            finalInitializer,
            dataSampleTypingResult
          )
        FinalResults.forValidation(context, errors, Some(finalState))(finalInitializer.validationContext)
      case _ =>
        prepareSourceFinalErrors(
          context = context,
          dependencies = dependencies,
          parameters = parameters,
          errors = keyValidationResult.swap.toList ++ valueValidationResult.swap.toList
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
      namingStrategy
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
          nonImportantForExecution = true
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

  case class ImplementationUniversalKafkaSourceFactoryState(
      keySchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      valueSchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      contextInitializer: ContextInitializer[ConsumerRecord[Any, Any]],
      dataSampleTypingResult: Option[TypingResult]
  ) extends UniversalKafkaSourceFactoryState

  case class PrecalculatedValueSchemaUniversalKafkaSourceFactoryState(
      valueValidationResult: Validated[ProcessCompilationError, (Option[RuntimeSchemaData[ParsedSchema]], TypingResult)]
  ) extends UniversalKafkaSourceFactoryState

}

package pl.touk.nussknacker.engine.schemedkafka.source

import cats.data.Validated.Valid
import cats.data.{NonEmptyList, Validated}
import io.circe.Json
import io.circe.syntax._
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, ProcessObjectDependencies, Source, SourceFactory}
import pl.touk.nussknacker.engine.api.test.TestRecord
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, Params}
import pl.touk.nussknacker.engine.kafka.PreparedKafkaTopic
import pl.touk.nussknacker.engine.kafka.consumerrecord.SerializableConsumerRecord
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.{KafkaSourceImplFactory, KafkaTestParametersInfo}
import pl.touk.nussknacker.engine.kafka.source._
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.SchemaVersionParamName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.formatter.SchemaBasedSerializableConsumerRecord
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaSupport
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory.UniversalKafkaSourceFactoryState
import pl.touk.nussknacker.engine.schemedkafka.{KafkaUniversalComponentTransformer, RuntimeSchemaData}

/**
  * This is universal kafka source - it will handle both avro and json
  * TODO: Move it to some other module when json schema handling will be available
  */
class UniversalKafkaSourceFactory(
    val schemaRegistryClientFactory: SchemaRegistryClientFactory,
    val schemaBasedMessagesSerdeProvider: SchemaBasedSerdeProvider,
    val modelDependencies: ProcessObjectDependencies,
    protected val implProvider: KafkaSourceImplFactory[Any, Any]
) extends SourceFactory
    with KafkaUniversalComponentTransformer[Source]
    with WithExplicitTypesToExtract
    with UnboundedStreamComponent {

  override type State = UniversalKafkaSourceFactoryState

  override def typesToExtract: List[TypedClass] =
    Typed.typedClass[GenericRecord] :: Typed.typedClass[TimestampType] :: Nil

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition =
    topicParamStep orElse
      schemaParamStep orElse
      nextSteps(context, dependencies)

  protected def nextSteps(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case step @ TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) :: Nil,
          _
        ) =>
      val preparedTopic = prepareTopic(topic)
      val versionOption = parseVersionOption(version)
      val valueValidationResult = determineSchemaAndType(
        prepareUniversalValueSchemaDeterminer(preparedTopic, versionOption),
        Some(SchemaVersionParamName)
      )

      prepareSourceFinalResults(preparedTopic, valueValidationResult, context, dependencies, step.parameters, Nil)
    case step @ TransformationStep((`topicParamName`, _) :: (SchemaVersionParamName, _) :: Nil, _) =>
      // Edge case - for some reason Topic/Version is not defined, e.g. when topic or version does not match DefinedEagerParameter(String, _):
      // 1. FailedToDefineParameter
      // 2. not resolved as a valid String
      // Those errors are identified by parameter validation and handled elsewhere, hence empty list of errors.
      prepareSourceFinalErrors(context, dependencies, step.parameters, errors = Nil)
  }

  protected def determineSchemaAndType(schemaDeterminer: ParsedSchemaDeterminer, paramName: Option[String])(
      implicit nodeId: NodeId
  ): Validated[ProcessCompilationError, (Option[RuntimeSchemaData[ParsedSchema]], TypingResult)] = {
    schemaDeterminer.determineSchemaUsedInTyping
      .map { schemaData =>
        val schema = schemaData.schema
        (Some(schemaData), schemaSupportDispatcher.forSchemaType(schema.schemaType()).typeDefinition(schema))
      }
      .leftMap(error => CustomNodeError(error.getMessage, paramName))
  }

  // Source specific FinalResults
  protected def prepareSourceFinalResults(
      preparedTopic: PreparedKafkaTopic,
      valueValidationResult: Validated[
        ProcessCompilationError,
        (Option[RuntimeSchemaData[ParsedSchema]], TypingResult)
      ],
      context: ValidationContext,
      dependencies: List[NodeDependencyValue],
      parameters: List[(String, DefinedParameter)],
      errors: List[ProcessCompilationError]
  )(implicit nodeId: NodeId): FinalResults = {
    val keyValidationResult = if (kafkaConfig.useStringForKey) {
      Valid((None, Typed[String]))
    } else {
      determineSchemaAndType(prepareUniversalKeySchemaDeterminer(preparedTopic), Some(topicParamName))
    }

    (keyValidationResult, valueValidationResult) match {
      case (Valid((keyRuntimeSchema, keyType)), Valid((valueRuntimeSchema, valueType))) =>
        val finalInitializer = prepareContextInitializer(dependencies, parameters, keyType, valueType)
        val finalState       = UniversalKafkaSourceFactoryState(keyRuntimeSchema, valueRuntimeSchema, finalInitializer)
        FinalResults.forValidation(context, errors, Some(finalState))(finalInitializer.validationContext)
      case _ =>
        prepareSourceFinalErrors(
          context,
          dependencies,
          parameters,
          keyValidationResult.swap.toList ++ valueValidationResult.swap.toList
        )
    }
  }

  // Source specific FinalResults with errors
  protected def prepareSourceFinalErrors(
      context: ValidationContext,
      dependencies: List[NodeDependencyValue],
      parameters: List[(String, DefinedParameter)],
      errors: List[ProcessCompilationError]
  )(implicit nodeId: NodeId): FinalResults = {
    val initializerWithUnknown = prepareContextInitializer(dependencies, parameters, Unknown, Unknown)
    FinalResults.forValidation(context, errors)(initializerWithUnknown.validationContext)
  }

  // Overwrite this for dynamic type definitions.
  protected def prepareContextInitializer(
      dependencies: List[NodeDependencyValue],
      parameters: List[(String, DefinedParameter)],
      keyTypingResult: TypingResult,
      valueTypingResult: TypingResult
  ): ContextInitializer[ConsumerRecord[Any, Any]] =
    new KafkaContextInitializer[Any, Any](
      OutputVariableNameDependency.extract(dependencies),
      keyTypingResult,
      valueTypingResult
    )

  override def paramsDeterminedAfterSchema: List[Parameter] = Nil

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): Source = {
    implicit val nodeId: NodeId = TypedNodeDependency[NodeId].extract(dependencies)

    val preparedTopic = extractPreparedTopic(params)
    val UniversalKafkaSourceFactoryState(
      keySchemaDataUsedInRuntime,
      valueSchemaUsedInRuntime,
      kafkaContextInitializer
    ) = finalState.get

    // prepare KafkaDeserializationSchema based on given key and value schema (with schema evolution)
    val deserializationSchema = schemaBasedMessagesSerdeProvider.deserializationSchemaFactory
      .create[Any, Any](kafkaConfig, keySchemaDataUsedInRuntime, valueSchemaUsedInRuntime)

    // prepare KafkaDeserializationSchema based on given key and value schema (without schema evolution - we want format test-data exactly the same way, it was sent to kafka)
    val formatterSchema =
      schemaBasedMessagesSerdeProvider.deserializationSchemaFactory.create[Any, Any](kafkaConfig, None, None)
    val recordFormatter =
      schemaBasedMessagesSerdeProvider.recordFormatterFactory.create[Any, Any](kafkaConfig, formatterSchema)
    implProvider.createSource(
      params,
      dependencies,
      finalState.get,
      List(preparedTopic),
      kafkaConfig,
      deserializationSchema,
      recordFormatter,
      kafkaContextInitializer,
      prepareKafkaTestParametersInfo(valueSchemaUsedInRuntime, preparedTopic.original),
      modelDependencies.namingStrategy
    )
  }

  private def prepareKafkaTestParametersInfo(runtimeSchemaOpt: Option[RuntimeSchemaData[ParsedSchema]], topic: String)(
      implicit nodeId: NodeId
  ): KafkaTestParametersInfo = {
    Validated
      .fromOption(
        runtimeSchemaOpt,
        NonEmptyList.one(CustomNodeError(nodeId.id, "Cannot generate test parameters: no runtime schema found", None))
      )
      .andThen { runtimeSchema =>
        val universalSchemaSupport: UniversalSchemaSupport =
          schemaSupportDispatcher.forSchemaType(runtimeSchema.schema.schemaType())
        universalSchemaSupport
          .extractParameters(runtimeSchema.schema)
          .map { params =>
            KafkaTestParametersInfo(params, prepareTestRecord(runtimeSchema, universalSchemaSupport, topic))
          }
      }
      .valueOr(e => throw new RuntimeException(e.toList.mkString("")))
  }

  private def prepareTestRecord(
      runtimeSchema: RuntimeSchemaData[ParsedSchema],
      universalSchemaSupport: UniversalSchemaSupport,
      topic: String
  ): Any => TestRecord = any => {
    val json = universalSchemaSupport.prepareMessageFormatter(runtimeSchema.schema, schemaRegistryClient)(any)
    val serializedConsumerRecord =
      SerializableConsumerRecord[Json, Json](None, json, Some(topic), None, None, None, None, None, None)
    TestRecord(
      SchemaBasedSerializableConsumerRecord[Json, Json](
        None,
        runtimeSchema.schemaIdOpt,
        serializedConsumerRecord
      ).asJson
    )
  }

  override def nodeDependencies: List[NodeDependency] =
    List(TypedNodeDependency[MetaData], TypedNodeDependency[NodeId], OutputVariableNameDependency)

}

object UniversalKafkaSourceFactory {

  case class UniversalKafkaSourceFactoryState(
      keySchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      valueSchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      contextInitializer: ContextInitializer[ConsumerRecord[Any, Any]]
  )

}

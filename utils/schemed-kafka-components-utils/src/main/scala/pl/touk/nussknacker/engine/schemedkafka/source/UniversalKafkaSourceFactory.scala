package pl.touk.nussknacker.engine.schemedkafka.source

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.Valid
import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, FatalUnknownError}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, ProcessObjectDependencies, Source, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.util.NotNothing
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.api.{MetaData, NodeId}
import pl.touk.nussknacker.engine.json.JsonSinkValueParameter
import pl.touk.nussknacker.engine.kafka.KafkaFactory.SinkValueParamName
import pl.touk.nussknacker.engine.kafka.PreparedKafkaTopic
import pl.touk.nussknacker.engine.kafka.source._
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.KafkaSourceImplFactory
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.SchemaVersionParamName
import pl.touk.nussknacker.engine.schemedkafka.encode.BestEffortAvroEncoder
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.{AvroSchemaSupport, JsonPayloadRecordFormatterSupport, UnsupportedSchemaType}
import pl.touk.nussknacker.engine.schemedkafka.sink.AvroSinkValueParameter
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory.UniversalKafkaSourceFactoryState
import pl.touk.nussknacker.engine.schemedkafka.{KafkaUniversalComponentTransformer, RuntimeSchemaData, SchemaDeterminerErrorHandler}

import scala.reflect.ClassTag

/**
  * This is universal kafka source - it will handle both avro and json
  * TODO: Move it to some other module when json schema handling will be available
  */
class UniversalKafkaSourceFactory[K: ClassTag: NotNothing, V: ClassTag: NotNothing](val schemaRegistryClientFactory: SchemaRegistryClientFactory,
                                                                                    val schemaBasedMessagesSerdeProvider: SchemaBasedSerdeProvider,
                                                                                    val processObjectDependencies: ProcessObjectDependencies,
                                                                                    protected val implProvider: KafkaSourceImplFactory[K, V])
  extends SourceFactory
    with KafkaUniversalComponentTransformer[Source]
    with WithExplicitTypesToExtract {

  override type State = UniversalKafkaSourceFactoryState[K, V]

  override def typesToExtract: List[TypedClass] = Typed.typedClassOpt[K].toList ::: Typed.typedClassOpt[V].toList ::: Typed.typedClass[TimestampType] :: Nil

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: NodeId): NodeTransformationDefinition =
    topicParamStep orElse
      schemaParamStep orElse
      nextSteps(context, dependencies)

  protected def nextSteps(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case step@TransformationStep((`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
      (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) :: Nil, _) =>
      val preparedTopic = prepareTopic(topic)
      val versionOption = parseVersionOption(version)
      val valueValidationResult = determineSchemaAndType(prepareUniversalValueSchemaDeterminer(preparedTopic, versionOption), Some(SchemaVersionParamName))

      prepareSourceFinalResults(preparedTopic, valueValidationResult, context, dependencies, step.parameters, Nil)
    case step@TransformationStep((`topicParamName`, _) :: (SchemaVersionParamName, _) :: Nil, _) =>
      // Edge case - for some reason Topic/Version is not defined, e.g. when topic or version does not match DefinedEagerParameter(String, _):
      // 1. FailedToDefineParameter
      // 2. not resolved as a valid String
      // Those errors are identified by parameter validation and handled elsewhere, hence empty list of errors.
      prepareSourceFinalErrors(context, dependencies, step.parameters, errors = Nil)
  }

  protected def determineSchemaAndType(schemaDeterminer: ParsedSchemaDeterminer, paramName: Option[String])(implicit nodeId: NodeId):
  Validated[ProcessCompilationError, (Option[RuntimeSchemaData[ParsedSchema]], TypingResult)] = {
    schemaDeterminer.determineSchemaUsedInTyping.map { schemaData =>
      val schema = schemaData.schema
      (Some(schemaData), schemaSupportDispatcher.forSchemaType(schema.schemaType()).typeDefinition(schema))
    }.leftMap(error => CustomNodeError(error.getMessage, paramName))
  }

  // Source specific FinalResults
  protected def prepareSourceFinalResults(preparedTopic: PreparedKafkaTopic,
                                          valueValidationResult: Validated[ProcessCompilationError, (Option[RuntimeSchemaData[ParsedSchema]], TypingResult)],
                                          context: ValidationContext,
                                          dependencies: List[NodeDependencyValue],
                                          parameters: List[(String, DefinedParameter)],
                                          errors: List[ProcessCompilationError])(implicit nodeId: NodeId): FinalResults = {
    val keyValidationResult = if (kafkaConfig.useStringForKey) {
      Valid((None, Typed[String]))
    } else {
      determineSchemaAndType(prepareUniversalKeySchemaDeterminer(preparedTopic), Some(topicParamName))
    }

    (keyValidationResult, valueValidationResult) match {
      case (Valid((keyRuntimeSchema, keyType)), Valid((valueRuntimeSchema, valueType))) =>
        val finalInitializer = prepareContextInitializer(dependencies, parameters, keyType, valueType)
        val finalState = UniversalKafkaSourceFactoryState(keyRuntimeSchema, valueRuntimeSchema, finalInitializer)
        FinalResults.forValidation(context, errors, Some(finalState))(finalInitializer.validationContext)
      case _ =>
        prepareSourceFinalErrors(context, dependencies, parameters, keyValidationResult.swap.toList ++ valueValidationResult.swap.toList)
    }
  }

  // Source specific FinalResults with errors
  protected def prepareSourceFinalErrors(context: ValidationContext,
                                         dependencies: List[NodeDependencyValue],
                                         parameters: List[(String, DefinedParameter)],
                                         errors: List[ProcessCompilationError])(implicit nodeId: NodeId): FinalResults = {
    val initializerWithUnknown = prepareContextInitializer(dependencies, parameters, Unknown, Unknown)
    FinalResults.forValidation(context, errors)(initializerWithUnknown.validationContext)
  }

  // Overwrite this for dynamic type definitions.
  protected def prepareContextInitializer(dependencies: List[NodeDependencyValue],
                                          parameters: List[(String, DefinedParameter)],
                                          keyTypingResult: TypingResult,
                                          valueTypingResult: TypingResult): ContextInitializer[ConsumerRecord[K, V]] =
    new KafkaContextInitializer[K, V](OutputVariableNameDependency.extract(dependencies), keyTypingResult, valueTypingResult)

  override def paramsDeterminedAfterSchema: List[Parameter] = Nil

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): Source = {
    implicit val nodeId: NodeId = TypedNodeDependency[NodeId].extract(dependencies)

    val preparedTopic = extractPreparedTopic(params)
    val versionOption = parseVersionOption(params)
    val UniversalKafkaSourceFactoryState(keySchemaDataUsedInRuntime, valueSchemaUsedInRuntime, kafkaContextInitializer) = finalState.get

    // prepare KafkaDeserializationSchema based on given key and value schema (with schema evolution)
    val deserializationSchema = schemaBasedMessagesSerdeProvider
      .deserializationSchemaFactory.create[K, V](kafkaConfig, keySchemaDataUsedInRuntime, valueSchemaUsedInRuntime)

    // prepare KafkaDeserializationSchema based on given key and value schema (without schema evolution - we want format test-data exactly the same way, it was sent to kafka)
    val formatterSchema = schemaBasedMessagesSerdeProvider.deserializationSchemaFactory.create[K, V](kafkaConfig, None, None)
    val recordFormatter = schemaBasedMessagesSerdeProvider.recordFormatterFactory.create[K, V](kafkaConfig, formatterSchema)

    implProvider.createSource(params, dependencies, finalState.get, List(preparedTopic), kafkaConfig, deserializationSchema,
      recordFormatter, kafkaContextInitializer, getParameters(valueSchemaUsedInRuntime))
  }

  private def getParameters(runtimeSchema: Option[RuntimeSchemaData[ParsedSchema]])
                           (implicit nodeId: NodeId): KafkaTestParametersInfo = {
    Validated.fromOption(runtimeSchema, NonEmptyList.one(CustomNodeError(nodeId.id, "Cannot generate test parameters: no runtime schema found", None)))
      .andThen(handleSchemaType)
      .valueOr(e => throw new RuntimeException(e.toList.mkString("")))
  }

  private def handleSchemaType(determinedSchema: RuntimeSchemaData[ParsedSchema])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, KafkaTestParametersInfo] = {
    (determinedSchema.schema match {
      case s: AvroSchema => AvroSinkValueParameter(s.rawSchema()).map(_.toParameters)
      case s: JsonSchema => JsonSinkValueParameter(s.rawSchema(), SinkValueParamName, ValidationMode.lax)(nodeId).map(_.toParameters)
      case s => Validated.invalidNel(FatalUnknownError(s"Avro or Json schema is required, but got ${s.schemaType()}"))
    }).map { params =>
      val schemaIdAsString = determinedSchema.schemaIdOpt.map {
        case IntSchemaId(value) => value.toString
        case StringSchemaId(value) => value
      }
      KafkaTestParametersInfo(params, schemaIdAsString, prepareMessageFormatter(determinedSchema.schema))
    }
  }

  private def prepareMessageFormatter(schema: ParsedSchema): Any => Json = {
    schema match {
      case s: AvroSchema =>
        val encoder = BestEffortAvroEncoder(ValidationMode.lax)
        val recordFormatterSupport = new AvroSchemaSupport(kafkaConfig).recordFormatterSupport(schemaRegistryClient)
        (data: Any) => recordFormatterSupport.formatMessage(encoder.encodeOrError(data, s.rawSchema()))
      case _: JsonSchema => JsonPayloadRecordFormatterSupport.formatMessage
      case _ => throw new UnsupportedSchemaType(schema.schemaType())
    }
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[MetaData],
    TypedNodeDependency[NodeId], OutputVariableNameDependency)

}

object UniversalKafkaSourceFactory {

  case class UniversalKafkaSourceFactoryState[K, V](keySchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                                    valueSchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                                    contextInitializer: ContextInitializer[ConsumerRecord[K, V]])

}
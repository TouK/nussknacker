package pl.touk.nussknacker.engine.avro.source

import cats.data.Validated
import cats.data.Validated.Valid
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.apache.avro.Schema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, OutputVariableNameDependency, Parameter, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, ProcessObjectDependencies, Source, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer.SchemaVersionParamName
import pl.touk.nussknacker.engine.avro.schemaregistry.{KafkaSerDeSchemaRegistryProvider, SchemaRegistryProvider}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{UniversalKafkaBaseTransformer, UniversalRuntimeSchemaData, UniversalSchemaDeterminer}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.avro.source.UniversalKafkaSourceFactory.UniversalKafkaSourceFactoryState
import pl.touk.nussknacker.engine.json.JsonSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.kafka.PreparedKafkaTopic
import pl.touk.nussknacker.engine.kafka.source.KafkaContextInitializer
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.KafkaSourceImplFactory

import scala.reflect.ClassTag

/**
  * This is universal kafka source - it will handle both avro and json
  * TODO: Move it to some other module when json schema handling will be available
  */
class UniversalKafkaSourceFactory[K: ClassTag, V: ClassTag](val schemaRegistryProvider: KafkaSerDeSchemaRegistryProvider,
                                                            val processObjectDependencies: ProcessObjectDependencies,
                                                            protected val implProvider: KafkaSourceImplFactory[K, V])
  extends SourceFactory
    with UniversalKafkaBaseTransformer[Source] {

  override type State = UniversalKafkaSourceFactoryState[K, V]

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
      val valueValidationResult = determineSchemaAndType(prepareValueSchemaDeterminer(preparedTopic, versionOption), Some(SchemaVersionParamName))

      prepareSourceFinalResults(preparedTopic, valueValidationResult, context, dependencies, step.parameters, Nil)
    case step@TransformationStep((`topicParamName`, _) :: (SchemaVersionParamName, _) :: Nil, _) =>
      // Edge case - for some reason Topic/Version is not defined, e.g. when topic or version does not match DefinedEagerParameter(String, _):
      // 1. FailedToDefineParameter
      // 2. not resolved as a valid String
      // Those errors are identified by parameter validation and handled elsewhere, hence empty list of errors.
      prepareSourceFinalErrors(context, dependencies, step.parameters, errors = Nil)
  }

  protected def determineSchemaAndType(schemaDeterminer: UniversalSchemaDeterminer, paramName: Option[String])(implicit nodeId: NodeId):
  Validated[ProcessCompilationError, (Option[UniversalRuntimeSchemaData], TypingResult)] = {
    schemaDeterminer.determineSchemaUsedInTyping.map { schemaData =>
      (schemaDeterminer.toRuntimeSchema(schemaData), UniversalSchemaTypeDefinitionExtractor.typeDefinition(schemaData.schema))
    }.leftMap(error => CustomNodeError(error.getMessage, paramName))
  }

  // Source specific FinalResults
  protected def prepareSourceFinalResults(preparedTopic: PreparedKafkaTopic,
                                          valueValidationResult: Validated[ProcessCompilationError, (Option[UniversalRuntimeSchemaData], TypingResult)],
                                          context: ValidationContext,
                                          dependencies: List[NodeDependencyValue],
                                          parameters: List[(String, DefinedParameter)],
                                          errors: List[ProcessCompilationError])(implicit nodeId: NodeId): FinalResults = {
    val keyValidationResult = if (kafkaConfig.useStringForKey) {
      Valid((None, Typed[String]))
    } else {
      determineSchemaAndType(prepareKeySchemaDeterminer(preparedTopic), Some(topicParamName))
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
    val preparedTopic = extractPreparedTopic(params)
    val UniversalKafkaSourceFactoryState(keySchemaDataUsedInRuntime, valueSchemaUsedInRuntime, kafkaContextInitializer) = finalState.get
    val deserializationSchema = schemaRegistryProvider.createKafkaDeserializationSchema[K,V](keySchemaDataUsedInRuntime, valueSchemaUsedInRuntime)
    val recordFormatter = schemaRegistryProvider.recordFormatter(deserializationSchema)

    implProvider.createSource(params, dependencies, finalState.get, List(preparedTopic), kafkaConfig, deserializationSchema, recordFormatter, kafkaContextInitializer)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[MetaData],
    TypedNodeDependency[NodeId], OutputVariableNameDependency)

}

object UniversalKafkaSourceFactory {
  case class UniversalKafkaSourceFactoryState[K, V](keySchemaDataOpt: Option[UniversalRuntimeSchemaData],
                                                    valueSchemaDataOtp: Option[UniversalRuntimeSchemaData],
                                                    contextInitializer: ContextInitializer[ConsumerRecord[K, V]])

}

object UniversalSchemaTypeDefinitionExtractor {
  def typeDefinition(schema: ParsedSchema): TypingResult = {
    schema.rawSchema() match {
      case a: Schema => AvroSchemaTypeDefinitionExtractor.typeDefinition(a)
      case s: org.everit.json.schema.Schema => JsonSchemaTypeDefinitionExtractor.typeDefinition(s)
      case _ => throw new IllegalArgumentException(s"Unsupported schema type: ${schema.schemaType()}")
    }
  }
}
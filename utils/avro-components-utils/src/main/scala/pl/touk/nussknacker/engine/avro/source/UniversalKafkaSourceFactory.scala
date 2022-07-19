package pl.touk.nussknacker.engine.avro.source

import cats.data.Validated
import cats.data.Validated.Valid
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, OutputVariableNameDependency, Parameter, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, ProcessObjectDependencies, Source, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{MetaData, NodeId}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer.SchemaVersionParamName
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaBasedMessagesSerdeProvider, SchemaVersionOption}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.source.UniversalKafkaSourceFactory.UniversalKafkaSourceFactoryState
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{KafkaAvroBaseTransformer, ParsedSchemaDeterminer, RuntimeSchemaData}
import pl.touk.nussknacker.engine.json.JsonSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.kafka.PreparedKafkaTopic
import pl.touk.nussknacker.engine.kafka.source.KafkaContextInitializer
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.KafkaSourceImplFactory

import scala.reflect.ClassTag

/**
 * This is universal kafka source - it will handle both avro and json
 * TODO: Move it to some other module when json schema handling will be available
 */

trait SchemaBasedMessagesSerdeProviderFactory {
  def create[S <: ParsedSchema](schema: S): SchemaBasedMessagesSerdeProvider[S]
}

class UniversalKafkaSourceFactory[K: ClassTag, V: ClassTag](serdeProviderFactory: SchemaBasedMessagesSerdeProviderFactory,
                                                            val schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                            val processObjectDependencies: ProcessObjectDependencies,
                                                            protected val implProvider: KafkaSourceImplFactory[K, V])
  extends SourceFactory with KafkaAvroBaseTransformer[Source] {

  override type State[S <: ParsedSchema] = UniversalKafkaSourceFactoryState[K, V, S]

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
      val valueValidationResult = determineSchemaAndType(prepareSchemaDeterminerForValue(preparedTopic, versionOption), Some(SchemaVersionParamName))

      prepareSourceFinalResults(preparedTopic, valueValidationResult, context, dependencies, Nil)
    case step@TransformationStep((`topicParamName`, _) :: (SchemaVersionParamName, _) :: Nil, _) =>
      // Edge case - for some reason Topic/Version is not defined, e.g. when topic or version does not match DefinedEagerParameter(String, _):
      // 1. FailedToDefineParameter
      // 2. not resolved as a valid String
      // Those errors are identified by parameter validation and handled elsewhere, hence empty list of errors.
      prepareSourceFinalErrors(context, dependencies, errors = Nil)
  }

  protected def determineSchemaAndType(schemaDeterminer: ParsedSchemaDeterminer, paramName: Option[String])(implicit nodeId: NodeId):
  Validated[ProcessCompilationError, (RuntimeSchemaData[ParsedSchema], TypingResult)] = {
    schemaDeterminer.determineSchemaUsedInTyping.map { schemaData =>
      (schemaData, toTypeDefinition(schemaData.schema))
    }.leftMap(error => CustomNodeError(error.getMessage, paramName))
  }

  private def toTypeDefinition(schema: ParsedSchema) = schema match {
    case schema: AvroSchema => AvroSchemaTypeDefinitionExtractor.typeDefinition(schema.rawSchema())
    case schema: JsonSchema => JsonSchemaTypeDefinitionExtractor.typeDefinition(schema.rawSchema())
    case _ => throw new IllegalArgumentException("Not supported")
  }

  protected def prepareSchemaDeterminerForKey(topic: PreparedKafkaTopic): ParsedSchemaDeterminer = ???
  protected def prepareSchemaDeterminerForValue(topic: PreparedKafkaTopic, schemaVersionOption: SchemaVersionOption): ParsedSchemaDeterminer = ???

  // Source specific FinalResults
  protected def prepareSourceFinalResults(preparedTopic: PreparedKafkaTopic,
                                          valueValidationResult: Validated[ProcessCompilationError, (RuntimeSchemaData[ParsedSchema], TypingResult)],
                                          context: ValidationContext,
                                          dependencies: List[NodeDependencyValue],
                                          errors: List[ProcessCompilationError])(implicit nodeId: NodeId): FinalResults = {
    val keyValidationResult = if (kafkaConfig.useStringForKey) {
      Valid((None, Typed[String]))
    } else {
      determineSchemaAndType(prepareSchemaDeterminerForKey(preparedTopic), Some(topicParamName)).map(v=> (Some(v._1), v._2))
    }

    (keyValidationResult, valueValidationResult) match {
      case (Valid((keyRuntimeSchema, keyType)), Valid((valueRuntimeSchema, valueType))) =>
        val finalInitializer = prepareContextInitializer(dependencies, keyType, valueType)

        //todo check that key schema if defined is of the same type as value schema
        val state: UniversalKafkaSourceFactoryState[K, V, _ <: ParsedSchema] = valueRuntimeSchema.schema match {
          case _: AvroSchema => UniversalKafkaSourceFactoryState(keyRuntimeSchema.asInstanceOf[Option[RuntimeSchemaData[AvroSchema]]], valueRuntimeSchema.asInstanceOf[RuntimeSchemaData[AvroSchema]], finalInitializer)
          case _: JsonSchema => UniversalKafkaSourceFactoryState(keyRuntimeSchema.asInstanceOf[Option[RuntimeSchemaData[JsonSchema]]], valueRuntimeSchema.asInstanceOf[RuntimeSchemaData[JsonSchema]], finalInitializer)
          case _ => throw new IllegalArgumentException()
        }
        val finalState = UniversalKafkaSourceFactoryState(keyRuntimeSchema, valueRuntimeSchema, finalInitializer)
        FinalResults.forValidation(context, errors, Some(state))(finalInitializer.validationContext)
      case _ =>
        prepareSourceFinalErrors(context, dependencies, keyValidationResult.swap.toList ++ valueValidationResult.swap.toList)
    }
  }

  // Source specific FinalResults with errors
  protected def prepareSourceFinalErrors(context: ValidationContext,
                                         dependencies: List[NodeDependencyValue],
                                         errors: List[ProcessCompilationError])(implicit nodeId: NodeId): FinalResults = {
    val initializerWithUnknown = prepareContextInitializer(dependencies, Unknown, Unknown)
    FinalResults.forValidation(context, errors)(initializerWithUnknown.validationContext)
  }

  // Overwrite this for dynamic type definitions.
  protected def prepareContextInitializer(dependencies: List[NodeDependencyValue],
                                          keyTypingResult: TypingResult,
                                          valueTypingResult: TypingResult): ContextInitializer[ConsumerRecord[K, V]] =
    new KafkaContextInitializer[K, V](OutputVariableNameDependency.extract(dependencies), keyTypingResult, valueTypingResult)

  override def paramsDeterminedAfterSchema: List[Parameter] = Nil

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State[_ <: ParsedSchema]]): Source = {
    val preparedTopic = extractPreparedTopic(params)
    val UniversalKafkaSourceFactoryState(keySchema, valueSchema, kafkaContextInitializer) = finalState.get

    val serdeProvider = serdeProviderFactory.create(valueSchema.schema)

    val deserializationSchema = serdeProvider.deserializationSchemaFactory.create[K, V](kafkaConfig, keySchema, Some(valueSchema))
    val recordFormatter = serdeProvider.recordFormatterFactory.create[K, V](kafkaConfig, deserializationSchema)

    implProvider.createSource(params, dependencies, finalState.get, List(preparedTopic), kafkaConfig, deserializationSchema, recordFormatter, kafkaContextInitializer)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[MetaData],
    TypedNodeDependency[NodeId], OutputVariableNameDependency)

}

object UniversalKafkaSourceFactory {

  case class UniversalKafkaSourceFactoryState[K, V, S <: ParsedSchema](keySchemaDataOpt: Option[RuntimeSchemaData[S]],
                                                                       valueSchemaData: RuntimeSchemaData[S],
                                                                       contextInitializer: ContextInitializer[ConsumerRecord[K, V]])

}
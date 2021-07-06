package pl.touk.nussknacker.engine.avro.source

import cats.data.Validated
import cats.data.Validated.Valid
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{BaseDefinedParameter, DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, OutputVariableNameDependency, Parameter, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.SchemaVersionParamName
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory.KafkaAvroSourceFactoryState
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroSchemaDeterminer, KafkaAvroBaseTransformer, RuntimeSchemaData}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkContextInitializer, FlinkSource, FlinkSourceFactory}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.kafka.source.{KafkaContextInitializer, KafkaSource}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic, RecordFormatter}

import scala.reflect.ClassTag

/**
  * Base implementation of KafkaSource factory with Avro schema support. It is based on GenericNodeTransformation to
  * - allow key and value type identification based on Schema Registry and
  * - allow Context initialization with event's value, key and metadata
  * You can provide schemas for both key and value. When useStringForKey = true (see KafkaConfig) the contents of event's key
  * are treated as String (this is default scenario).
  * Reader schema used in runtime is determined by topic and version.
  * Reader schema can be different than schema used by writer (e.g. when writer produces event with new schema), in that case "schema evolution" may be required.
  * For SpecificRecord use SpecificRecordKafkaAvroSourceFactory.
  * Assumptions:
  * 1. Every event that comes in has its key and value schemas registered in Schema Registry.
  * 2. Avro payload must include schema id for both Generic and Specific records (to provide "schema evolution" we need to know the exact writers schema).
  *
  * @param schemaRegistryProvider - provides a set of strategies for serialization and deserialization while event processing and/or testing.
  * @tparam K - type of event's key, used to determine if key object is Specific or Generic (for GenericRecords use Any)
  * @tparam V - type of event's value, used to determine if value object is Specific or Generic (for GenericRecords use Any)
  */
class KafkaAvroSourceFactory[K:ClassTag, V:ClassTag](val schemaRegistryProvider: SchemaRegistryProvider,
                                                     val processObjectDependencies: ProcessObjectDependencies,
                                                     timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]])
  extends FlinkSourceFactory[ConsumerRecord[K, V]] with KafkaAvroBaseTransformer[FlinkSource[ConsumerRecord[K, V]]] with Serializable {

  override type State = KafkaAvroSourceFactoryState[K, V, DefinedParameter]

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: NodeId): NodeTransformationDefinition =
    topicParamStep orElse
      schemaParamStep orElse
      nextSteps(context, dependencies)

  protected def nextSteps(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case step@TransformationStep((`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
      (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::Nil, _) =>
      val preparedTopic = prepareTopic(topic)
      val versionOption = parseVersionOption(version)
      val valueValidationResult = determineSchemaAndType(prepareValueSchemaDeterminer(preparedTopic, versionOption), Some(SchemaVersionParamName))

      prepareSourceFinalResults(preparedTopic, valueValidationResult, context, dependencies, step.parameters, Nil)
    case step@TransformationStep((`topicParamName`, _) :: (SchemaVersionParamName, _) ::Nil, _) =>
      // Edge case - for some reason Topic/Version is not defined, e.g. when topic or version does not match DefinedEagerParameter(String, _):
      // 1. FailedToDefineParameter
      // 2. not resolved as a valid String
      // Those errors are identified by parameter validation and handled elsewhere, hence empty list of errors.
      prepareSourceFinalErrors(context, dependencies, step.parameters, errors = Nil)
  }

  protected def determineSchemaAndType(schemaDeterminer: AvroSchemaDeterminer, paramName: Option[String])(implicit nodeId: NodeId):
  Validated[ProcessCompilationError, (Option[RuntimeSchemaData], TypingResult)] = {
    schemaDeterminer.determineSchemaUsedInTyping.map { schemaData =>
      (schemaDeterminer.toRuntimeSchema(schemaData), AvroSchemaTypeDefinitionExtractor.typeDefinition(schemaData.schema))
    }.leftMap(error => CustomNodeError(error.getMessage, paramName))
  }

  // Source specific FinalResults
  protected def prepareSourceFinalResults(preparedTopic: PreparedKafkaTopic,
                                          valueValidationResult: Validated[ProcessCompilationError, (Option[RuntimeSchemaData], TypingResult)],
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
        val finalInitializer = new KafkaContextInitializer[K, V, DefinedParameter](keyType, valueType)
        val finalState = KafkaAvroSourceFactoryState(keyRuntimeSchema, valueRuntimeSchema, finalInitializer)
        FinalResults(finalInitializer.validationContext(context, dependencies, parameters), state = Some(finalState), errors = errors)
      case _ =>
        prepareSourceFinalErrors(context, dependencies, parameters, keyValidationResult.swap.toList ++ valueValidationResult.swap.toList)
    }
  }

  // Source specific FinalResults with errors
  protected def prepareSourceFinalErrors(context: ValidationContext,
                                         dependencies: List[NodeDependencyValue],
                                         parameters: List[(String, DefinedParameter)],
                                         errors: List[ProcessCompilationError])(implicit nodeId: NodeId): FinalResults = {
    val initializerWithUnknown = KafkaContextInitializer.initializerWithUnknown[K, V, DefinedParameter]
    FinalResults(initializerWithUnknown.validationContext(context, dependencies, parameters), errors, None)
  }

  override def paramsDeterminedAfterSchema: List[Parameter] = Nil

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): FlinkSource[ConsumerRecord[K, V]] = {
    val preparedTopic = extractPreparedTopic(params)
    val KafkaAvroSourceFactoryState(keySchemaDataUsedInRuntime, valueSchemaUsedInRuntime, kafkaContextInitializer) = finalState.get

    // prepare KafkaDeserializationSchema based on given key and value schema (with schema evolution)
    val deserializationSchema = schemaRegistryProvider.deserializationSchemaFactory.create[K, V](kafkaConfig, keySchemaDataUsedInRuntime, valueSchemaUsedInRuntime)

    // - avro payload formatter requires to format test data with writer schema, id of writer schema comes with event
    // - for json payload event does not come with writer schema id
    val formatterSchema = schemaRegistryProvider.deserializationSchemaFactory.create[K, V](kafkaConfig, None, None)
    val recordFormatter = schemaRegistryProvider.recordFormatterFactory.create[K, V](kafkaConfig, formatterSchema)

    createSource(params,
      dependencies,
      finalState,
      List(preparedTopic),
      kafkaConfig,
      deserializationSchema,
      recordFormatter,
      kafkaContextInitializer
    )
  }

  /**
    * Basic implementation of new source creation. Override this method to create custom KafkaSource.
    */
  protected def createSource(params: Map[String, Any],
                             dependencies: List[NodeDependencyValue],
                             finalState: Option[State],
                             preparedTopics: List[PreparedKafkaTopic],
                             kafkaConfig: KafkaConfig,
                             deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
                             formatter: RecordFormatter,
                             flinkContextInitializer: FlinkContextInitializer[ConsumerRecord[K, V]]): KafkaSource[ConsumerRecord[K, V]] = {
    new KafkaSource[ConsumerRecord[K, V]](
      preparedTopics,
      kafkaConfig,
      deserializationSchema,
      timestampAssigner,
      formatter
    ) {
      override val contextInitializer: FlinkContextInitializer[ConsumerRecord[K, V]] = flinkContextInitializer
    }
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]),
    TypedNodeDependency(classOf[NodeId]), OutputVariableNameDependency)

}

object KafkaAvroSourceFactory {

  case class KafkaAvroSourceFactoryState[K, V, DefinedParameter <: BaseDefinedParameter](keySchemaDataOpt: Option[RuntimeSchemaData],
                                                                                         valueSchemaDataOpt: Option[RuntimeSchemaData],
                                                                                         contextInitializer: KafkaContextInitializer[K, V, DefinedParameter])

}

package pl.touk.nussknacker.engine.avro.source

import cats.data.Validated
import cats.data.Validated.Valid
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{BaseDefinedParameter, DefinedEagerParameter, NodeDependencyValue, OutputVariableNameValue}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, OutputVariableNameDependency, Parameter, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.{SchemaVersionParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory.KafkaAvroSourceFactoryState
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroSchemaDeterminer, KafkaAvroBaseTransformer, RuntimeSchemaData, SchemaDeterminerError}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSource
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.kafka.source.KafkaContextInitializer

import scala.reflect.ClassTag

class KafkaAvroSourceFactory[K:ClassTag, V:ClassTag](val schemaRegistryProvider: SchemaRegistryProvider,
                                                     val processObjectDependencies: ProcessObjectDependencies,
                                                     timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]])
  extends BaseKafkaAvroSourceFactory[K, V](timestampAssigner) with KafkaAvroBaseTransformer[FlinkSource[ConsumerRecord[K, V]]]{

  override type State = KafkaAvroSourceFactoryState[K, V, DefinedParameter]

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: NodeId): NodeTransformationDefinition =
    topicParamStep orElse
      schemaParamStep orElse
      {
        case step@TransformationStep((TopicParamName, DefinedEagerParameter(topic:String, _)) ::
          (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::Nil, _) =>
          //we do casting here and not in case, as version can be null...
          val preparedTopic = prepareTopic(topic)
          val versionOption = parseVersionOption(version)

          val (keyValidationResult, keyErrors) = if (kafkaConfig.useStringForKey) {
            (Valid((None, Typed[String])), Nil)
          } else {
            determineSchemaAndType(prepareKeySchemaDeterminer(preparedTopic), Some(TopicParamName))
          }
          val (valueValidationResult, valueErrors) = determineSchemaAndType(prepareValueSchemaDeterminer(preparedTopic, versionOption), Some(SchemaVersionParamName))

          prepareSourceFinalResults(context, dependencies, step.parameters, keyValidationResult, keyErrors, valueValidationResult, valueErrors)

        //edge case - for some reason Topic/Version is not defined
        case step@TransformationStep((TopicParamName, _) :: (SchemaVersionParamName, _) ::Nil, _) =>
          prepareSourceFinalErrors(context, dependencies, step.parameters, List(CustomNodeError("Topic/Version is not defined", Some(TopicParamName))))
      }

  protected def determineSchemaAndType(keySchemaDeterminer: AvroSchemaDeterminer, paramName: Option[String])(implicit nodeId: NodeId):
  (Validated[SchemaDeterminerError, (Option[RuntimeSchemaData], TypingResult)], List[CustomNodeError]) = {
    val validationResult = keySchemaDeterminer.determineSchemaUsedInTyping.map { schemaData =>
      (keySchemaDeterminer.toRuntimeSchema(schemaData), AvroSchemaTypeDefinitionExtractor.typeDefinition(schemaData.schema))
    }
    val errors = validationResult.swap.map(error => CustomNodeError(error.getMessage, paramName)).toList
    (validationResult, errors)
  }

  // Source specific FinalResults
  protected def prepareSourceFinalResults(context: ValidationContext,
                                          dependencies: List[NodeDependencyValue],
                                          parameters: List[(String, DefinedParameter)],
                                          keyValidationResult: Validated[SchemaDeterminerError, (Option[RuntimeSchemaData], TypingResult)],
                                          keyErrors: List[CustomNodeError],
                                          valueValidationResult: Validated[SchemaDeterminerError, (Option[RuntimeSchemaData], TypingResult)],
                                          valueErrors: List[CustomNodeError])(implicit nodeId: NodeId): FinalResults = {
    (keyValidationResult, valueValidationResult) match {
      case (Valid((keyRuntimeSchema, keyType)), Valid((valueRuntimeSchema, valueType))) =>
        val finalInitializer = new KafkaContextInitializer[K, V, DefinedParameter](keyType, valueType)
        val finalState = KafkaAvroSourceFactoryState[K, V, DefinedParameter](keyRuntimeSchema, valueRuntimeSchema, finalInitializer)
        FinalResults(finalInitializer.validationContext(context, dependencies, parameters), state = Some(finalState.asInstanceOf[State]))
      case _ =>
        prepareSourceFinalErrors(context, dependencies, parameters, keyErrors ++ valueErrors)
    }
  }

  // Source specific FinalResults with errors
  protected def prepareSourceFinalErrors(context: ValidationContext,
                                         dependencies: List[NodeDependencyValue],
                                         parameters: List[(String, DefinedParameter)],
                                         errors: List[CustomNodeError])(implicit nodeId: NodeId): FinalResults = {
    val initializerWithUnknown = new KafkaContextInitializer[K, V, DefinedParameter](Unknown, Unknown)
    FinalResults(initializerWithUnknown.validationContext(context, dependencies, parameters), errors, None)
  }

  override def paramsDeterminedAfterSchema: List[Parameter] = Nil

  private def variableName(dependencies: List[NodeDependencyValue]): String = {
    dependencies.collectFirst {
      case OutputVariableNameValue(name) => name
    }.get
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): FlinkSource[ConsumerRecord[K, V]] = {
    val preparedTopic = extractPreparedTopic(params)
    val version = extractVersionOption(params)
    val KafkaAvroSourceFactoryState(keySchemaDataUsedInRuntime, valueSchemaUsedInRuntime, kafkaContextInitializer) = finalState.get
    createSource(
      preparedTopic,
      kafkaConfig,
      schemaRegistryProvider.deserializationSchemaFactory,
      schemaRegistryProvider.recordFormatterFactory,
      keySchemaDataUsedInRuntime,
      valueSchemaUsedInRuntime,
      kafkaContextInitializer
    )(typedDependency[MetaData](dependencies), typedDependency[NodeId](dependencies))
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]),
    TypedNodeDependency(classOf[NodeId]), OutputVariableNameDependency)

}

object KafkaAvroSourceFactory {

  case class KafkaAvroSourceFactoryState[K, V, DefinedParameter <: BaseDefinedParameter](keySchemaDataOpt: Option[RuntimeSchemaData],
                                                                                         valueSchemaDataOpt: Option[RuntimeSchemaData],
                                                                                         contextInitializer: KafkaContextInitializer[K, V, DefinedParameter])

}

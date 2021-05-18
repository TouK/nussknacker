package pl.touk.nussknacker.engine.avro.source

import cats.data.Validated
import cats.data.Validated.Valid
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue, OutputVariableNameValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
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

  override type State = KafkaAvroSourceFactoryState

  var kafkaContextInitializer: KafkaContextInitializer[K, V, DefinedParameter, State] = _

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = topicParamStep orElse schemaParamStep orElse {
    case step@TransformationStep((TopicParamName, DefinedEagerParameter(topic:String, _)) ::
      (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::Nil, _) =>
      //we do casting here and not in case, as version can be null...
      val preparedTopic = prepareTopic(topic)
      val versionOption = parseVersionOption(version)

      val (keyValidationResult, keyErrors) = if (kafkaConfig.useStringForKey) {
        (Valid((None, Typed[String])), Nil)
      } else {
        // TODO: add key schema versioning
        determineSchemaAndType(prepareKeySchemaDeterminer(preparedTopic), Some(TopicParamName))
      }
      val (valueValidationResult, valueErrors) = determineSchemaAndType(prepareValueSchemaDeterminer(preparedTopic, versionOption), Some(SchemaVersionParamName))

      (keyValidationResult, valueValidationResult) match {
        case (Valid((keyRuntimeSchema, keyType)), Valid((valueRuntimeSchema, valueType))) =>
          val finalState = KafkaAvroSourceFactoryState(keyRuntimeSchema, valueRuntimeSchema)
          FinalResults(finalCtx(context, dependencies, step.parameters, step.state, keyType, valueType), state = Some(finalState))
        case _ =>
          FinalResults(finalCtx(context, dependencies, step.parameters, step.state, Unknown, Unknown), keyErrors ++ valueErrors, None)
      }
    //edge case - for some reason Topic/Version is not defined
    case step@TransformationStep((TopicParamName, _) :: (SchemaVersionParamName, _) ::Nil, _) =>
      FinalResults(finalCtx(context, dependencies, step.parameters, step.state, Unknown, Unknown), Nil, Some(KafkaAvroSourceFactoryState(None, None)))
  }

  protected def determineSchemaAndType(keySchemaDeterminer: AvroSchemaDeterminer, paramName: Option[String])(implicit nodeId: NodeId):
  (Validated[SchemaDeterminerError, (Option[RuntimeSchemaData], TypingResult)], List[CustomNodeError]) = {
    val validationResult = keySchemaDeterminer.determineSchemaUsedInTyping.map { schemaData =>
      (keySchemaDeterminer.toRuntimeSchema(schemaData), AvroSchemaTypeDefinitionExtractor.typeDefinition(schemaData.schema))
    }
    val errors = validationResult.swap.map(error => CustomNodeError(error.getMessage, paramName)).toList
    (validationResult, errors)
  }

  override def paramsDeterminedAfterSchema: List[Parameter] = Nil

  protected def finalCtx(context: ValidationContext, dependencies: List[NodeDependencyValue],
                         parameters: List[(String, DefinedParameter)], state: Option[State],
                         keyTypingResult: TypingResult, valueTypingResult: TypingResult)(implicit nodeId: NodeId): ValidationContext = {
    kafkaContextInitializer = new KafkaContextInitializer[K, V, DefinedParameter, State](keyTypingResult, valueTypingResult)
    kafkaContextInitializer.validationContext(context, dependencies, parameters, state)
  }

  private def variableName(dependencies: List[NodeDependencyValue]): String = {
    dependencies.collectFirst {
      case OutputVariableNameValue(name) => name
    }.get
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): FlinkSource[ConsumerRecord[K, V]] = {
    val preparedTopic = extractPreparedTopic(params)
    val version = extractVersionOption(params)
    val KafkaAvroSourceFactoryState(keySchemaDataUsedInRuntime, valueSchemaUsedInRuntime) = finalState.get
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

  case class KafkaAvroSourceFactoryState(keySchemaDataOpt: Option[RuntimeSchemaData], valueSchemaDataOpt: Option[RuntimeSchemaData])

}

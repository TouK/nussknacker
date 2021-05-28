package pl.touk.nussknacker.engine.avro.source

import cats.data.Validated.Valid
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue, OutputVariableNameValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.{SchemaVersionParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory.KafkaAvroSourceFactoryState
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroSchemaDeterminer, KafkaAvroBaseTransformer, RuntimeSchemaData}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSource
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

import scala.reflect.ClassTag

//TODO: add key-value as default deserailization scenario
class KafkaAvroSourceFactory[T:ClassTag](val schemaRegistryProvider: SchemaRegistryProvider,
                                         val processObjectDependencies: ProcessObjectDependencies,
                                         timestampAssigner: Option[TimestampWatermarkHandler[T]])
  extends BaseKafkaAvroSourceFactory[T](timestampAssigner) with KafkaAvroBaseTransformer[FlinkSource[T]]{

  override type State = KafkaAvroSourceFactoryState

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = topicParamStep orElse schemaParamStep orElse {
    case TransformationStep((TopicParamName, DefinedEagerParameter(topic:String, _)) ::
      (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::Nil, _) =>
      //we do casting here and not in case, as version can be null...
      val preparedTopic = prepareTopic(topic)
      val versionOption = parseVersionOption(version)

      // TODO: add key schema versioning
      val (keyValidationResult, keyErrors) = determineSchemaAndType(prepareKeySchemaDeterminer(preparedTopic), Some(TopicParamName))
      val (valueValidationResult, valueErrors) = determineSchemaAndType(prepareValueSchemaDeterminer(preparedTopic, versionOption), Some(SchemaVersionParamName))

      (keyValidationResult, valueValidationResult) match {
        case (Valid((keyRuntimeSchema, keyType)), Valid((valueRuntimeSchema, valueType))) =>
          val finalState = KafkaAvroSourceFactoryState(keyRuntimeSchema, valueRuntimeSchema)
          FinalResults(finalCtx(context, dependencies, keyType, valueType), state = Some(finalState))
        case _ =>
          FinalResults(finalCtx(context, dependencies, Unknown, Unknown), keyErrors ++ valueErrors, None)
      }
    //edge case - for some reason Topic/Version is not defined
    case TransformationStep((TopicParamName, _) ::
      (SchemaVersionParamName, _) ::Nil, _) =>
      FinalResults(finalCtx(context, dependencies, Unknown, Unknown), Nil, Some(KafkaAvroSourceFactoryState(None, None)))
  }

  private def determineSchemaAndType(keySchemaDeterminer: AvroSchemaDeterminer, paramName: Option[String])(implicit nodeId: NodeId) = {
    val validationResult = keySchemaDeterminer.determineSchemaUsedInTyping.map { schemaData =>
      (keySchemaDeterminer.toRuntimeSchema(schemaData), AvroSchemaTypeDefinitionExtractor.typeDefinition(schemaData.schema))
    }
    val errors = validationResult.swap.map(error => CustomNodeError(error.getMessage, paramName)).toList
    (validationResult, errors)
  }

  override def paramsDeterminedAfterSchema: List[Parameter] = Nil

  private def finalCtx(context: ValidationContext, dependencies: List[NodeDependencyValue], keyResult: typing.TypingResult, valueResult: typing.TypingResult)(implicit nodeId: NodeId): ValidationContext = {
    context.withVariable(variableName(dependencies), valueResult, None).getOrElse(context)
  }

  private def variableName(dependencies: List[NodeDependencyValue]) = {
    dependencies.collectFirst {
      case OutputVariableNameValue(name) => name
    }.get
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): FlinkSource[T] = {
    val preparedTopic = extractPreparedTopic(params)
    val version = extractVersionOption(params)
    val KafkaAvroSourceFactoryState(keySchemaDataUsedInRuntime, valueSchemaUsedInRuntime) = finalState.get
    createSource(
      preparedTopic,
      kafkaConfig,
      schemaRegistryProvider.deserializationSchemaFactory,
      schemaRegistryProvider.recordFormatter,
      keySchemaDataUsedInRuntime,
      valueSchemaUsedInRuntime
    )(typedDependency[MetaData](dependencies), typedDependency[NodeId](dependencies))
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]),
    TypedNodeDependency(classOf[NodeId]), OutputVariableNameDependency)

}

object KafkaAvroSourceFactory {

  case class KafkaAvroSourceFactoryState(keySchemaDataOpt: Option[RuntimeSchemaData], valueSchemaDataOpt: Option[RuntimeSchemaData])

}

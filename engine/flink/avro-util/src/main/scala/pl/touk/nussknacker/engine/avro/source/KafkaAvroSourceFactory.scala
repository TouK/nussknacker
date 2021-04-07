package pl.touk.nussknacker.engine.avro.source

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue, OutputVariableNameValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.{SchemaVersionParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.flink.api.process.FlinkSource
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

import scala.reflect.ClassTag

//TODO: add key-value as default deserailization scenario
class KafkaAvroSourceFactory[T:ClassTag](val schemaRegistryProvider: SchemaRegistryProvider,
                                         val processObjectDependencies: ProcessObjectDependencies,
                                         timestampAssigner: Option[TimestampWatermarkHandler[T]])
  extends BaseKafkaAvroSourceFactory(timestampAssigner) with KafkaAvroBaseTransformer[FlinkSource[T]]{

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = topicParamStep orElse schemaParamStep orElse {
    case TransformationStep((TopicParamName, DefinedEagerParameter(topic:String, _)) ::
      (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::Nil, _) =>
      //we do casting here and not in case, as version can be null...
      val preparedTopic = prepareTopic(topic)
      val versionOption = parseVersionOption(version)

      // key schema
      // TODO: add key schema versioning
      val keySchemaDeterminer = prepareKeySchemaDeterminer(preparedTopic)
      val keyValidType = keySchemaDeterminer.determineSchemaUsedInTyping.map(schemaData => AvroSchemaTypeDefinitionExtractor.typeDefinition(schemaData.schema))
      // value schema
      val valueSchemaDeterminer = prepareValueSchemaDeterminer(preparedTopic, versionOption)
      val valueValidType = valueSchemaDeterminer.determineSchemaUsedInTyping.map(schemaData => AvroSchemaTypeDefinitionExtractor.typeDefinition(schemaData.schema))

      val finalCtxValue = finalCtx(context, dependencies, keyValidType.getOrElse(Unknown), valueValidType.getOrElse(Unknown))
      val finalErrors = valueValidType.swap.map(error => CustomNodeError(error.getMessage, Some(SchemaVersionParamName))).toList
      FinalResults(finalCtxValue, finalErrors)
    //edge case - for some reason Topic/Version is not defined
    case TransformationStep((TopicParamName, _) ::
      (SchemaVersionParamName, _) ::Nil, _) =>
      FinalResults(finalCtx(context, dependencies, Unknown, Unknown), Nil)
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
    createSource(
      preparedTopic,
      kafkaConfig,
      schemaRegistryProvider.deserializationSchemaFactory,
      schemaRegistryProvider.recordFormatter,
      prepareKeySchemaDeterminer(preparedTopic),
      prepareValueSchemaDeterminer(preparedTopic, version),
      returnGenericAvroType = true
    )(typedDependency[MetaData](dependencies), typedDependency[NodeId](dependencies))
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]),
    TypedNodeDependency(classOf[NodeId]), OutputVariableNameDependency)

}

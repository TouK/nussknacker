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

class KafkaAvroSourceFactory(val schemaRegistryProvider: SchemaRegistryProvider,
                             val processObjectDependencies: ProcessObjectDependencies,
                             timestampAssigner: Option[TimestampWatermarkHandler[Any]])
  extends BaseKafkaAvroSourceFactory(timestampAssigner) with KafkaAvroBaseTransformer[FlinkSource[Any]]{

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val initial = topicParam
      NextParameters(List(initial.value), initial.written)
    case TransformationStep((TopicParamName, DefinedEagerParameter(topic:String, _)) :: Nil, _) =>
      val preparedTopic = prepareTopic(topic)
      val versionOption = versionParam(preparedTopic)
     NextParameters(List(versionOption.value), versionOption.written, None)
    case TransformationStep((TopicParamName, _) :: Nil, _) =>
      NextParameters(List(fallbackVersionOptionParam), Nil, None)
    case TransformationStep((TopicParamName, DefinedEagerParameter(topic:String, _)) ::
      (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::Nil, _) =>
      //we do casting here and not in case, as version can be null...
      val preparedTopic = prepareTopic(topic)
      val versionOption = parseVersionOption(version)
      val schemaDeterminer = prepareSchemaDeterminer(preparedTopic, versionOption)
      val validType = schemaDeterminer.determineSchemaUsedInTyping.map(schemaData => AvroSchemaTypeDefinitionExtractor.typeDefinition(schemaData.schema))
      val finalCtxValue = finalCtx(context, dependencies, validType.getOrElse(Unknown))
      val finalErrors = validType.swap.map(error => CustomNodeError(error.getMessage, Some(SchemaVersionParamName))).toList
      FinalResults(finalCtxValue, finalErrors)
    //edge case - for some reason Topic/Version is not defined
    case TransformationStep((TopicParamName, _) ::
      (SchemaVersionParamName, _) ::Nil, _) =>
      FinalResults(finalCtx(context, dependencies, Unknown), Nil)
  }

  private def finalCtx(context: ValidationContext, dependencies: List[NodeDependencyValue], result: typing.TypingResult)(implicit nodeId: NodeId): ValidationContext = {
    context.withVariable(variableName(dependencies), result).getOrElse(context)
  }

  private def variableName(dependencies: List[NodeDependencyValue]) = {
    dependencies.collectFirst {
      case OutputVariableNameValue(name) => name
    }.get
  }

  override def initialParameters: List[Parameter] = {
    List(topicParam(NodeId("")).value, versionParam(Nil))
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): FlinkSource[Any] = {
    val preparedTopic = extractPreparedTopic(params)
    val version = extractVersionOption(params)
    createSource(preparedTopic, kafkaConfig, schemaRegistryProvider.deserializationSchemaFactory, schemaRegistryProvider.recordFormatter,
      prepareSchemaDeterminer(preparedTopic, version), returnGenericAvroType = true)(
      typedDependency[MetaData](dependencies), typedDependency[NodeId](dependencies))
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]),
    TypedNodeDependency(classOf[NodeId]), OutputVariableNameDependency)

}

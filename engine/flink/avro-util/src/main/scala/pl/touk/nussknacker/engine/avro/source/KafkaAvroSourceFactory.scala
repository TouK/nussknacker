package pl.touk.nussknacker.engine.avro.source

import org.apache.flink.streaming.api.functions.TimestampAssigner
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue, OutputVariableNameValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer
import pl.touk.nussknacker.engine.avro.KafkaAvroFactory.{SchemaVersionParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.flink.api.process.FlinkSource

import scala.reflect.ClassTag

class KafkaAvroSourceFactory[T: ClassTag](val schemaRegistryProvider: SchemaRegistryProvider[T],
                                          val processObjectDependencies: ProcessObjectDependencies,
                                          timestampAssigner: Option[TimestampAssigner[T]])
  extends BaseKafkaAvroSourceFactory(processObjectDependencies, timestampAssigner) with KafkaAvroBaseTransformer[FlinkSource[T], T]{

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val initial = topicParam
      NextParameters(List(initial.value), initial.written)
    case TransformationStep((TopicParamName, DefinedEagerParameter(topic:String, _)) :: Nil, _) =>
      val preparedTopic = prepareTopic(topic)
      val version = versionParam(preparedTopic)
     NextParameters(List(version.value), version.written, None)
    case TransformationStep((TopicParamName, _) :: Nil, _) =>
      fallbackVersionParam
    case TransformationStep((TopicParamName, DefinedEagerParameter(topic:String, _)) ::
      (SchemaVersionParamName, DefinedEagerParameter(version, _)) ::Nil, _) =>
      //we do casting here and not in case, as version can be null...
      val preparedTopic = prepareTopic(topic)
      val result = createSchemaRegistryProvider(preparedTopic, version.asInstanceOf[Integer]).typeDefinition
      val finalCtxValue = finalCtx(context, dependencies, result.getOrElse(Unknown))
      val finalErrors = result.swap.map(error => CustomNodeError(error.getMessage, Some(SchemaVersionParamName))).toList
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
    List(topicParam(NodeId("")).value)
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue]): FlinkSource[T] = {
    createSource(extractPreparedTopic(params), kafkaConfig, createSchemaRegistryProvider(params),
      typedDependency[MetaData](dependencies), typedDependency[NodeId](dependencies))
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]),
    TypedNodeDependency(classOf[NodeId]), OutputVariableNameDependency)

}

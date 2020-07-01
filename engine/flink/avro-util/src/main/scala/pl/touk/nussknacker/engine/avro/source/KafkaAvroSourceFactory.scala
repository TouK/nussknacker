package pl.touk.nussknacker.engine.avro.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue, OutputVariableNameValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.{KafkaAvroBaseTransformer, KafkaAvroFactory}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSource

class KafkaAvroSourceFactory[T:TypeInformation](val schemaRegistryProvider: SchemaRegistryProvider[T],
                                                val processObjectDependencies: ProcessObjectDependencies,
                                                timestampAssigner: Option[TimestampAssigner[T]])
  extends BaseKafkaAvroSourceFactory(processObjectDependencies, timestampAssigner) with KafkaAvroBaseTransformer[FlinkSource[T], T]{

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(initialParameters)
    case TransformationStep((KafkaAvroFactory.TopicParamName, DefinedEagerParameter(topic:String, _)) :: Nil, _) =>
       NextParameters(List(versionParam(topic)), Nil, None)
    case TransformationStep((KafkaAvroFactory.TopicParamName, DefinedEagerParameter(topic:String, _)) ::
      (KafkaAvroFactory.SchemaVersionParamName, DefinedEagerParameter(version:Integer, _)) ::Nil, _) =>
      val result = createSchemaRegistryProvider(topic, version).typeDefinition
      val finalCtxValue = finalCtx(context, dependencies, result.getOrElse(Unknown))
      FinalResults(finalCtxValue, result.fold(error => List(CustomNodeError(error.getMessage, None)), _ => Nil))
    case TransformationStep((KafkaAvroFactory.TopicParamName, _) ::
      (KafkaAvroFactory.SchemaVersionParamName, _) ::Nil, _) =>
      FinalResults(finalCtx(context, dependencies, Unknown), Nil)
  }

  private def finalCtx(context: ValidationContext, dependencies: List[NodeDependencyValue], result: typing.TypingResult)(implicit nodeId: NodeId) = {
    context.withVariable(variableName(dependencies), result).getOrElse(context)
  }

  private def variableName(dependencies: List[NodeDependencyValue]) = {
    dependencies.collectFirst {
      case OutputVariableNameValue(name) => name
    }.get
  }

  override def initialParameters: List[Parameter] = {
    List(topicParam)
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue]): FlinkSource[T] = {
    createSource(topic(params), kafkaConfig, createSchemaRegistryProvider(params),
      typedDependency[MetaData](dependencies), typedDependency[NodeId](dependencies))
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]),
    TypedNodeDependency(classOf[NodeId]), OutputVariableNameDependency)

}

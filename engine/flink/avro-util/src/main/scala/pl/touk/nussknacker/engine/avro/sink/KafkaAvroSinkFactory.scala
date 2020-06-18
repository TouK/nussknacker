package pl.touk.nussknacker.engine.avro.sink

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.{KafkaAvroBaseTransformer, KafkaAvroFactory}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSink

class KafkaAvroSinkFactory(val schemaRegistryProvider: SchemaRegistryProvider[Any],
                           val processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaAvroSinkFactory(processObjectDependencies) with KafkaAvroBaseTransformer[FlinkSink, Any]{

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(initialParameters)
    case TransformationStep((KafkaAvroFactory.SinkOutputParamName, _) ::
      (KafkaAvroFactory.TopicParamName, DefinedEagerParameter(topic:String, _)) :: Nil, _) =>
       NextParameters(List(versionParam(topic)), Nil, None)
    case TransformationStep((KafkaAvroFactory.SinkOutputParamName, _) ::
      (KafkaAvroFactory.TopicParamName, _) ::
      (KafkaAvroFactory.SchemaVersionParamName, _) ::Nil, _) => FinalResults(context, Nil)
  }

  override def initialParameters: List[Parameter] = {
    List(
      Parameter[Any](KafkaAvroFactory.SinkOutputParamName).copy(isLazyParameter = true),
      topicParam,
    )
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue]): FlinkSink = {
    val output = params(KafkaAvroFactory.SinkOutputParamName).asInstanceOf[LazyParameter[Any]]
    createSink(topic(params), output, kafkaConfig, createSchemaRegistryProvider(params),
      typedDependency[MetaData](dependencies),
      typedDependency[NodeId](dependencies))
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]), TypedNodeDependency(classOf[NodeId]))

}

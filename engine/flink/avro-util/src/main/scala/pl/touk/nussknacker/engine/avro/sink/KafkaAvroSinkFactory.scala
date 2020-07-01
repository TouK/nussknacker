package pl.touk.nussknacker.engine.avro.sink

import cats.Id
import cats.data.WriterT
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, DefinedParameter, FailedToDefineParameter, NodeDependencyValue}
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
    case TransformationStep(Nil, _) =>
      val initial = initialParametersForNode
      NextParameters(initial.value, initial.written)
    case TransformationStep((KafkaAvroFactory.SinkOutputParamName, _) ::
      (KafkaAvroFactory.TopicParamName, DefinedEagerParameter(topic:String, _)) :: Nil, _) =>
        val version = versionParam(topic)
        NextParameters(List(version.value), version.written, None)
    //edge case - for some reason Topic is not defined
    case TransformationStep((KafkaAvroFactory.SinkOutputParamName, _) ::
      (KafkaAvroFactory.TopicParamName, _) :: Nil, _) =>
        NextParameters(List(versionParam(Nil)), Nil, None)
    case TransformationStep((KafkaAvroFactory.SinkOutputParamName, output:DefinedParameter) ::
      (KafkaAvroFactory.TopicParamName, DefinedEagerParameter(topic:String, _)) ::
      (KafkaAvroFactory.SchemaVersionParamName, DefinedEagerParameter(version, _)) ::Nil, _) =>
        //we cast here, since null will not be matched in case...
        val provider = createSchemaRegistryProvider(topic, version.asInstanceOf[java.lang.Integer])
        val validationResult = validateOutput(output.returnType, provider).swap.toList
        FinalResults(context, validationResult)
    //edge case - for some reason Topic/Version is not defined
    case TransformationStep((KafkaAvroFactory.SinkOutputParamName, _) ::
          (KafkaAvroFactory.TopicParamName, _) ::
          (KafkaAvroFactory.SchemaVersionParamName, _) ::Nil, _) => FinalResults(context, Nil)
  }

  override def initialParameters: List[Parameter] = {
    initialParametersForNode(NodeId("")).value
  }

  private def initialParametersForNode(implicit nodeId: NodeId): WriterT[Id, List[ProcessCompilationError], List[Parameter]] =
    topicParam.map(List(Parameter[Any](KafkaAvroFactory.SinkOutputParamName).copy(isLazyParameter = true), _))

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue]): FlinkSink = {
    val output = params(KafkaAvroFactory.SinkOutputParamName).asInstanceOf[LazyParameter[Any]]
    createSink(topic(params), output, kafkaConfig, createSchemaRegistryProvider(params),
      typedDependency[MetaData](dependencies),
      typedDependency[NodeId](dependencies))
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]), TypedNodeDependency(classOf[NodeId]))

}

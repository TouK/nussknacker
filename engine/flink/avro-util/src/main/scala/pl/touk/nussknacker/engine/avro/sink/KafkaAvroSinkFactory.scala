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

class KafkaAvroSinkFactory(val schemaRegistryProvider: SchemaRegistryProvider[Any], val processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaAvroSinkFactory with KafkaAvroBaseTransformer[FlinkSink, Any]{

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val initial = initialParametersForNode
      NextParameters(initial.value, initial.written)
    case TransformationStep((KafkaAvroFactory.SinkOutputParamName, _) ::
      (KafkaAvroFactory.TopicParamName, DefinedEagerParameter(topic:String, _)) :: Nil, _) =>
        val preparedTopic = prepareTopic(topic)
        val version = versionParam(preparedTopic)
        NextParameters(List(version.value), version.written, None)
    case TransformationStep((KafkaAvroFactory.SinkOutputParamName, _) ::
      (KafkaAvroFactory.TopicParamName, _) :: Nil, _) => fallbackVersionParam
    case TransformationStep((KafkaAvroFactory.SinkOutputParamName, output:DefinedParameter) ::
      (KafkaAvroFactory.TopicParamName, DefinedEagerParameter(topic:String, _)) ::
      (KafkaAvroFactory.SchemaVersionParamName, DefinedEagerParameter(version, _)) ::Nil, _) =>
        //we cast here, since null will not be matched in case...
        val preparedTopic = prepareTopic(topic)
        val provider = createSchemaRegistryProvider(preparedTopic, version.asInstanceOf[java.lang.Integer])
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
    topicParam.map(List(Parameter[AnyRef](KafkaAvroFactory.SinkOutputParamName).copy(isLazyParameter = true), _))

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue]): FlinkSink = {
    val output = params(KafkaAvroFactory.SinkOutputParamName).asInstanceOf[LazyParameter[AnyRef]]
    val preparedTopic = extractPreparedTopic(params)

    createSink(preparedTopic, output, kafkaConfig, createSchemaRegistryProvider(params),
      typedDependency[MetaData](dependencies),
      typedDependency[NodeId](dependencies))
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]), TypedNodeDependency(classOf[NodeId]))

}

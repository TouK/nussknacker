package pl.touk.nussknacker.engine.avro.sink

import cats.Id
import cats.data.WriterT
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, BaseDefinedParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.avro.{KafkaAvroBaseTransformer, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.flink.api.process.FlinkSink

class KafkaAvroSinkFactory(val schemaRegistryProvider: SchemaRegistryProvider, val processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaAvroSinkFactory with KafkaAvroBaseTransformer[FlinkSink]{

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val initial = initialParametersForNode
      NextParameters(initial.value, initial.written)
    case TransformationStep((KafkaAvroBaseTransformer.SinkKeyParamName, _) :: (KafkaAvroBaseTransformer.SinkValueParamName, _) ::
      (KafkaAvroBaseTransformer.TopicParamName, DefinedEagerParameter(topic:String, _)) :: Nil, _) =>
        val preparedTopic = prepareTopic(topic)
        val version = versionParam(preparedTopic)
        NextParameters(List(version.value), version.written, None)
    case TransformationStep((KafkaAvroBaseTransformer.SinkKeyParamName, _) :: (KafkaAvroBaseTransformer.SinkValueParamName, _) ::
      (KafkaAvroBaseTransformer.TopicParamName, _) :: Nil, _) => fallbackVersionParam
    case TransformationStep((KafkaAvroBaseTransformer.SinkKeyParamName, _: BaseDefinedParameter) :: (KafkaAvroBaseTransformer.SinkValueParamName, value: BaseDefinedParameter) ::
      (KafkaAvroBaseTransformer.TopicParamName, DefinedEagerParameter(topic:String, _)) ::
      (KafkaAvroBaseTransformer.SchemaVersionParamName, DefinedEagerParameter(version, _)) ::Nil, _) =>
        //we cast here, since null will not be matched in case...
        val preparedTopic = prepareTopic(topic)
        val schemaDeterminer = prepareSchemaDeterminer(preparedTopic, Option(version.asInstanceOf[java.lang.Integer]).map(_.intValue()))
        val validationResult = schemaDeterminer.determineSchemaUsedInTyping
          .leftMap(SchemaDeterminerErrorHandler.handleSchemaRegistryError)
          .andThen(schema => validateValueType(value.returnType, schema)).swap.toList
        FinalResults(context, validationResult)
    //edge case - for some reason Topic/Version is not defined
    case TransformationStep((KafkaAvroBaseTransformer.SinkKeyParamName, _) :: (KafkaAvroBaseTransformer.SinkValueParamName, _) ::
          (KafkaAvroBaseTransformer.TopicParamName, _) ::
          (KafkaAvroBaseTransformer.SchemaVersionParamName, _) ::Nil, _) => FinalResults(context, Nil)
  }

  override def initialParameters: List[Parameter] = {
    initialParametersForNode(NodeId("")).value
  }

  private def initialParametersForNode(implicit nodeId: NodeId): WriterT[Id, List[ProcessCompilationError], List[Parameter]] =
    topicParam.map(List(
      Parameter.optional[CharSequence](KafkaAvroBaseTransformer.SinkKeyParamName).copy(isLazyParameter = true),
      Parameter[AnyRef](KafkaAvroBaseTransformer.SinkValueParamName).copy(isLazyParameter = true), _))

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue]): FlinkSink = {
    val preparedTopic = extractPreparedTopic(params)
    val version = extractVersion(params)
    val key = params(KafkaAvroBaseTransformer.SinkKeyParamName).asInstanceOf[LazyParameter[CharSequence]]
    val value = params(KafkaAvroBaseTransformer.SinkValueParamName).asInstanceOf[LazyParameter[AnyRef]]

    createSink(preparedTopic, version, key, value,
      kafkaConfig, schemaRegistryProvider.serializationSchemaFactory, prepareSchemaDeterminer(preparedTopic, version))(
      typedDependency[MetaData](dependencies), typedDependency[NodeId](dependencies))
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]), TypedNodeDependency(classOf[NodeId]))

}

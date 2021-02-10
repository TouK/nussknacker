package pl.touk.nussknacker.engine.avro.sink

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.transformation.{BaseDefinedParameter, DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.{KafkaAvroBaseTransformer, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSink

object KafkaAvroSinkFactory {

  private[sink] val paramsDeterminedAfterSchema = List(
    Parameter[String](KafkaAvroBaseTransformer.SinkValidationModeParameterName)
      .copy(editor = Some(FixedValuesParameterEditor(ValidationMode.values.map(ep => FixedExpressionValue(s"'${ep.name}'", ep.label))))),
    Parameter.optional[CharSequence](KafkaAvroBaseTransformer.SinkKeyParamName).copy(isLazyParameter = true),
    Parameter[AnyRef](KafkaAvroBaseTransformer.SinkValueParamName).copy(isLazyParameter = true)
  )

  private[sink] def extractValidationMode(value: String): ValidationMode =
    ValidationMode.byName(value).getOrElse(throw CustomNodeValidationException(s"Unknown validation mode: $value", Some(KafkaAvroBaseTransformer.SinkValidationModeParameterName)))
}

class KafkaAvroSinkFactory(val schemaRegistryProvider: SchemaRegistryProvider, val processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaAvroSinkFactory with KafkaAvroBaseTransformer[FlinkSink] {
  import KafkaAvroSinkFactory._

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val initial = getTopicParam.map(List(_))
      NextParameters(initial.value, initial.written)
    case TransformationStep((KafkaAvroBaseTransformer.TopicParamName, DefinedEagerParameter(topic: String, _)) :: Nil, _) =>
      val preparedTopic = prepareTopic(topic)
      val version = getVersionParam(preparedTopic)
      NextParameters(List(version.value) ++ paramsDeterminedAfterSchema, version.written, None)
    case TransformationStep((KafkaAvroBaseTransformer.TopicParamName, _) :: Nil, _) =>
      NextParameters(List(fallbackVersionOptionParam) ++ paramsDeterminedAfterSchema)
    case TransformationStep(
    (KafkaAvroBaseTransformer.TopicParamName, DefinedEagerParameter(topic: String, _)) ::
      (KafkaAvroBaseTransformer.SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::
      (KafkaAvroBaseTransformer.SinkValidationModeParameterName, DefinedEagerParameter(mode: String, _)) ::
      (KafkaAvroBaseTransformer.SinkKeyParamName, _: BaseDefinedParameter) ::
      (KafkaAvroBaseTransformer.SinkValueParamName, value: BaseDefinedParameter) :: Nil, _
    ) =>
      //we cast here, since null will not be matched in case...
      val preparedTopic = prepareTopic(topic)
      val versionOption = parseVersionOption(version)
      val schemaDeterminer = prepareSchemaDeterminer(preparedTopic, versionOption)
      val determinedSchema = schemaDeterminer.determineSchemaUsedInTyping
        .leftMap(SchemaDeterminerErrorHandler.handleSchemaRegistryError)
        .leftMap(NonEmptyList.one)
      val validationResult = (determinedSchema andThen validateSchema)
        .andThen { schemaData =>
          validateValueType(value.returnType, schemaData.schema, extractValidationMode(mode))
            .leftMap(NonEmptyList.one)
        }.swap.toList.flatMap(_.toList)
      FinalResults(context, validationResult)
    //edge case - for some reason Topic/Version is not defined
    case TransformationStep(
    (KafkaAvroBaseTransformer.TopicParamName, _) ::
      (KafkaAvroBaseTransformer.SchemaVersionParamName, _) ::
      (KafkaAvroBaseTransformer.SinkValidationModeParameterName, _) ::
      (KafkaAvroBaseTransformer.SinkKeyParamName, _) ::
      (KafkaAvroBaseTransformer.SinkValueParamName, _) :: Nil, _
    ) => FinalResults(context, Nil)
  }

  override def initialParameters: List[Parameter] = {
    implicit val nodeId: NodeId = NodeId("")
    val topic = getTopicParam.value
    List(topic, getVersionParam(Nil)) ++ paramsDeterminedAfterSchema
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): FlinkSink = {
    val preparedTopic = extractPreparedTopic(params)
    val versionOption = extractVersionOption(params)
    val key = params(KafkaAvroBaseTransformer.SinkKeyParamName).asInstanceOf[LazyParameter[CharSequence]]
    val value = params(KafkaAvroBaseTransformer.SinkValueParamName).asInstanceOf[LazyParameter[AnyRef]]
    val validationMode = extractValidationMode(params(KafkaAvroBaseTransformer.SinkValidationModeParameterName).asInstanceOf[String])

    createSink(preparedTopic, versionOption, key, value,
      kafkaConfig, schemaRegistryProvider.serializationSchemaFactory, prepareSchemaDeterminer(preparedTopic, versionOption), validationMode)(
      typedDependency[MetaData](dependencies), typedDependency[NodeId](dependencies))
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]), TypedNodeDependency(classOf[NodeId]))
}

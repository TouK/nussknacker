package pl.touk.nussknacker.engine.avro.sink.flink

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory}
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer.{SchemaVersionParamName, SinkKeyParamName}
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import KafkaAvroSinkFactoryWithEditor.TransformationState
import pl.touk.nussknacker.engine.avro.{KafkaAvroBaseComponentTransformer, KafkaAvroBaseTransformer, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSink
import pl.touk.nussknacker.engine.util.KeyedValue


object KafkaAvroSinkFactoryWithEditor {

  private val paramsDeterminedAfterSchema = List(
    Parameter.optional[CharSequence](KafkaAvroBaseComponentTransformer.SinkKeyParamName).copy(isLazyParameter = true)
  )

  case class TransformationState(sinkValueParameter: AvroSinkValueParameter)
}

class KafkaAvroSinkFactoryWithEditor(val schemaRegistryProvider: SchemaRegistryProvider[KeyedValue[AnyRef, AnyRef]], val processObjectDependencies: ProcessObjectDependencies)
  extends SinkFactory with KafkaAvroBaseTransformer[FlinkSink] {

  override type State = TransformationState

  override def paramsDeterminedAfterSchema: List[Parameter] = KafkaAvroSinkFactoryWithEditor.paramsDeterminedAfterSchema

  private def valueParamStep(context: ValidationContext)(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep
      (
        (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
        (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::
        (SinkKeyParamName, _) :: Nil, _
      ) =>
      val preparedTopic = prepareTopic(topic)
      val versionOption = parseVersionOption(version)
      val schemaDeterminer = prepareValueSchemaDeterminer(preparedTopic, versionOption)
      val determinedSchema = schemaDeterminer
        .determineSchemaUsedInTyping
        .leftMap(SchemaDeterminerErrorHandler.handleSchemaRegistryError(_))
        .leftMap(NonEmptyList.one)
      val validatedSchema = determinedSchema.andThen { s =>
        schemaRegistryProvider.validateSchema(s.schema)
          .leftMap(_.map(e => CustomNodeError(nodeId.id, e.getMessage, None)))
        }

      validatedSchema.andThen { schema =>
        AvroSinkValueParameter(schema).map { valueParam =>
          val state = TransformationState(sinkValueParameter = valueParam)
          NextParameters(valueParam.toParameters, state = Some(state))
        }
      }.valueOr(e => FinalResults(context, e.toList))
    case TransformationStep
      (
        (`topicParamName`, _) ::
        (SchemaVersionParamName, _) ::
        (SinkKeyParamName, _) :: Nil, state
      ) => FinalResults(context, Nil, state)
  }

  protected def finalParamStep(context: ValidationContext)(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(
      (`topicParamName`, _) :: (SchemaVersionParamName, _) :: (SinkKeyParamName, _) :: valueParams, state) if valueParams.nonEmpty =>
        FinalResults(context, Nil, state)
  }

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: NodeId): NodeTransformationDefinition =
    topicParamStep orElse
      schemaParamStep orElse
        valueParamStep(context) orElse
          finalParamStep(context)

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): FlinkSink = {
    implicit val nodeId: NodeId = typedDependency[NodeId](dependencies)
    val state = finalState.get
    val preparedTopic = extractPreparedTopic(params)
    val versionOption = extractVersionOption(params)
    val processMetaData = typedDependency[NodeId](dependencies)
    val clientId = s"${processMetaData.id}-${preparedTopic.prepared}"

    val schemaDeterminer = prepareValueSchemaDeterminer(preparedTopic, versionOption)
    val schemaData = schemaDeterminer.determineSchemaUsedInTyping.valueOr(SchemaDeterminerErrorHandler.handleSchemaRegistryErrorAndThrowException)
    val schemaUsedInRuntime = schemaDeterminer.toRuntimeSchema(schemaData)

    val sinkValue = AvroSinkValue.applyUnsafe(state.sinkValueParameter, parameterValues = params)
    val key = params(SinkKeyParamName).asInstanceOf[LazyParameter[CharSequence]]

    new KafkaAvroSink(preparedTopic, versionOption, key, sinkValue, kafkaConfig, schemaRegistryProvider.serializationSchemaFactory,
      schemaData.serializableSchema, schemaUsedInRuntime.map(_.serializableSchema), clientId, ValidationMode.strict)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]), TypedNodeDependency(classOf[NodeId]))

}

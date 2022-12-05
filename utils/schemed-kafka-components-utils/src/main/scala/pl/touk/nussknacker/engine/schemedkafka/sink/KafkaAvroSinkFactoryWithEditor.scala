package pl.touk.nussknacker.engine.schemedkafka.sink

import cats.data.NonEmptyList
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{SchemaVersionParamName, SinkKeyParamName}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaBasedSerdeProvider, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.sink.KafkaAvroSinkFactoryWithEditor.TransformationState
import pl.touk.nussknacker.engine.schemedkafka.{KafkaUniversalComponentTransformer, RuntimeSchemaData, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValue
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.SinkValueParameter

object KafkaAvroSinkFactoryWithEditor {

  private val paramsDeterminedAfterSchema = List(
    Parameter.optional[CharSequence](KafkaUniversalComponentTransformer.SinkKeyParamName).copy(isLazyParameter = true)
  )

  case class TransformationState(schema: RuntimeSchemaData[AvroSchema], runtimeSchema: Option[RuntimeSchemaData[AvroSchema]], sinkValueParameter: SinkValueParameter)

}

class KafkaAvroSinkFactoryWithEditor(val schemaRegistryClientFactory: SchemaRegistryClientFactory,
                                     val schemaBasedMessagesSerdeProvider: SchemaBasedSerdeProvider,
                                     val processObjectDependencies: ProcessObjectDependencies,
                                     implProvider: KafkaAvroSinkImplFactory)
  extends KafkaUniversalComponentTransformer[Sink] with SinkFactory {

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
        schemaBasedMessagesSerdeProvider.validateSchema(s)
          .leftMap(_.map(e => CustomNodeError(nodeId.id, e.getMessage, None)))
        }
      validatedSchema.andThen { schemaData =>
        AvroSinkValueParameter(schemaData.schema.rawSchema()).map { valueParam =>
          val state = TransformationState(schemaData, schemaDeterminer.toRuntimeSchema(schemaData), valueParam)
          NextParameters(valueParam.toParameters, state = Option(state))
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

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalStateOpt: Option[State]): Sink = {
    val preparedTopic = extractPreparedTopic(params)
    val key = params(SinkKeyParamName).asInstanceOf[LazyParameter[CharSequence]]
    val finalState = finalStateOpt.getOrElse(throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation"))
    val sinkValue = SinkValue.applyUnsafe(finalState.sinkValueParameter, parameterValues = params)
    val valueLazyParam = sinkValue.toLazyParameter

    val serializationSchema = schemaBasedMessagesSerdeProvider.serializationSchemaFactory.create(preparedTopic.prepared, finalState.runtimeSchema.map(_.toParsedSchemaData), kafkaConfig)
    val clientId = s"${TypedNodeDependency[MetaData].extract(dependencies).id}-${preparedTopic.prepared}"

    implProvider.createSink(preparedTopic, key, valueLazyParam, kafkaConfig, serializationSchema, clientId, finalState.schema, ValidationMode.strict)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[MetaData], TypedNodeDependency[NodeId])

}

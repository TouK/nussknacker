package pl.touk.nussknacker.engine.avro.sink

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer.{SchemaVersionParamName, SinkKeyParamName}
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, SchemaRegistryProvider}
import pl.touk.nussknacker.engine.avro.sink.KafkaAvroSinkFactoryWithEditor.TransformationState
import pl.touk.nussknacker.engine.avro.{KafkaAvroBaseComponentTransformer, KafkaAvroBaseTransformer, RuntimeSchemaData, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.definition.LazyParameterUtils

import scala.collection.immutable.ListMap

object KafkaAvroSinkFactoryWithEditor {

  private val paramsDeterminedAfterSchema = List(
    Parameter.optional[CharSequence](KafkaAvroBaseComponentTransformer.SinkKeyParamName).copy(isLazyParameter = true)
  )

  case class TransformationState(schema: RuntimeSchemaData, runtimeSchema: Option[RuntimeSchemaData], sinkValueParameter: AvroSinkValueParameter)

}

class KafkaAvroSinkFactoryWithEditor(val schemaRegistryProvider: SchemaRegistryProvider,
                                     val processObjectDependencies: ProcessObjectDependencies,
                                     implProvider: KafkaAvroSinkImplFactory)
  extends KafkaAvroBaseTransformer[Sink] with SinkFactory {

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
          .map(_ => s)
          .leftMap(_.map(e => CustomNodeError(nodeId.id, e.getMessage, None)))
        }
      validatedSchema.andThen { schemaData =>
        AvroSinkValueParameter(schemaData.schema).map { valueParam =>
          val state = TransformationState(schemaData, schemaDeterminer.toRuntimeSchema(schemaData), valueParam)
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

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalStateOpt: Option[State]): Sink = {
    val preparedTopic = extractPreparedTopic(params)
    val versionOption = extractVersionOption(params)
    val key = params(SinkKeyParamName).asInstanceOf[LazyParameter[CharSequence]]
    val finalState = finalStateOpt.getOrElse(throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation"))
    val sinkValue = AvroSinkValue.applyUnsafe(finalState.sinkValueParameter, parameterValues = params)
    val valueLazyParam = toLazyParameter(sinkValue)

    val versionOpt = Option(versionOption).collect {
      case ExistingSchemaVersion(version) => version
    }
    val serializationSchema = schemaRegistryProvider.serializationSchemaFactory.create(preparedTopic.prepared, versionOpt, finalState.runtimeSchema.map(_.serializableSchema), kafkaConfig)
    val clientId = s"${TypedNodeDependency[MetaData].extract(dependencies).id}-${preparedTopic.prepared}"

    implProvider.createSink(preparedTopic, key, valueLazyParam, kafkaConfig, serializationSchema, clientId, finalState.schema, ValidationMode.strict)
  }

  private def toLazyParameter(sv: AvroSinkValue): LazyParameter[AnyRef] = sv match {
    case AvroSinkSingleValue(value) =>
      value
    case AvroSinkRecordValue(fields) =>
      LazyParameterUtils.typedMap(ListMap(fields.toList.map {
        case (key, value) => key -> toLazyParameter(value)
      }: _*))
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[MetaData], TypedNodeDependency[NodeId])

}

package pl.touk.nussknacker.engine.avro.sink

import cats.data.NonEmptyList
import io.confluent.kafka.schemaregistry.ParsedSchema
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{BaseDefinedParameter, DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData, NodeId}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer._
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.UniversalSchemaSupport
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaBasedSerdeProvider, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.sink.UniversalKafkaSinkFactory.{RawEditorParamName, TransformationState}
import pl.touk.nussknacker.engine.avro.{KafkaUniversalComponentTransformer, RuntimeSchemaData, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.util.output.OutputValidatorErrorsConverter
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValue
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.{SinkSingleValueParameter, SinkValueParameter}

/**
 * This is universal kafka sink - it will handle both avro and json
 * TODO: Move it to some other module when json schema handling will be available
 */
object UniversalKafkaSinkFactory {

  private val RawEditorParamName = "Raw editor"

  private val paramsDeterminedAfterSchema = List(
    Parameter.optional[CharSequence](SinkKeyParamName).copy(isLazyParameter = true, defaultValue = Some("null")),
    Parameter[Boolean](RawEditorParamName).copy(defaultValue = Some("false"), editor = Some(BoolParameterEditor), validators = List(MandatoryParameterValidator))
  )

  case class TransformationState(schema: RuntimeSchemaData[ParsedSchema], sinkValueParameter: Option[SinkValueParameter])
}

class UniversalKafkaSinkFactory(val schemaRegistryClientFactory: SchemaRegistryClientFactory,
                                val schemaBasedMessagesSerdeProvider: SchemaBasedSerdeProvider,
                                val processObjectDependencies: ProcessObjectDependencies,
                                implProvider: UniversalKafkaSinkImplFactory)
  extends KafkaUniversalComponentTransformer[Sink] with SinkFactory {

  override type State = TransformationState

  override def paramsDeterminedAfterSchema: List[Parameter] = UniversalKafkaSinkFactory.paramsDeterminedAfterSchema

  private val outputValidatorErrorsConverter = new OutputValidatorErrorsConverter(SinkValueParamName)

  private val rawValueParam: Parameter = Parameter[AnyRef](SinkValueParamName).copy(isLazyParameter = true)
  private val validationModeParam = Parameter[String](SinkValidationModeParameterName).copy(editor = Some(FixedValuesParameterEditor(ValidationMode.values.map(ep => FixedExpressionValue(s"'${ep.name}'", ep.label)))))

  protected def rawEditorParameterStep(context: ValidationContext)
                                      (implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep((`topicParamName`, _) :: (SchemaVersionParamName, _) :: (SinkKeyParamName, _) :: (RawEditorParamName, DefinedEagerParameter(true, _)) :: Nil, _) =>
      NextParameters(validationModeParam :: rawValueParam :: Nil)
    case TransformationStep(
    (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
      (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::
      (SinkKeyParamName, _) ::
      (RawEditorParamName, _) ::
      (SinkValidationModeParameterName, DefinedEagerParameter(mode: String, _)) ::
      (SinkValueParamName, value: BaseDefinedParameter) :: Nil, _
    ) =>
      val determinedSchema = getSchema(topic, version)
      val validationResult = determinedSchema.map(_.schema)
        .andThen(schemaBasedMessagesSerdeProvider.validateSchema(_).leftMap(_.map(e => CustomNodeError(nodeId.id, e.getMessage, None))))
        .andThen { schema =>
          val validate = UniversalSchemaSupport.rawOutputValidator(schema)
          validate(value.returnType, extractValidationMode(mode))
            .leftMap(outputValidatorErrorsConverter.convertValidationErrors)
            .leftMap(NonEmptyList.one)
        }.swap.toList.flatMap(_.toList)
      val finalState = determinedSchema.toOption.map(schema => TransformationState(schema, None))
      FinalResults(context, validationResult, finalState)
    //edge case - for some reason Topic/Version is not defined
    case TransformationStep((`topicParamName`, _) :: (SchemaVersionParamName, _) :: (SinkKeyParamName, _) :: (RawEditorParamName, _) ::
      (SinkValidationModeParameterName, _) :: (SinkValueParamName, _) :: Nil, _) => FinalResults(context, Nil)
  }

  private def valueEditorParamStep(context: ValidationContext)(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep
      (
      (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
        (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::
        (SinkKeyParamName, _) ::
        (RawEditorParamName, DefinedEagerParameter(false, _)) :: Nil, _
      ) =>
      val determinedSchema = getSchema(topic, version)
      val validatedSchema = determinedSchema.andThen { s =>
        schemaBasedMessagesSerdeProvider.validateSchema(s.schema)
          .map(_ => s)
          .leftMap(_.map(e => CustomNodeError(nodeId.id, e.getMessage, None)))
      }
      validatedSchema.andThen { schemaData =>
        UniversalSchemaSupport.extractSinkValueParameter(schemaData.schema).map { valueParam =>
          val state = TransformationState(schemaData, Some(valueParam))
          NextParameters(valueParam.toParameters, state = Option(state))
        }
      }.valueOr(e => FinalResults(context, e.toList))
    case TransformationStep((`topicParamName`, _) :: (SchemaVersionParamName, _) :: (SinkKeyParamName, _) :: (RawEditorParamName, DefinedEagerParameter(false, _)) :: valueParams, state) => FinalResults(context, Nil, state)
  }

  private def getSchema(topic: String, version: String)(implicit nodeId: NodeId) = {
    val preparedTopic = prepareTopic(topic)
    val versionOption = parseVersionOption(version)
    val schemaDeterminer = prepareUniversalValueSchemaDeterminer(preparedTopic, versionOption)
    schemaDeterminer
      .determineSchemaUsedInTyping
      .leftMap(SchemaDeterminerErrorHandler.handleSchemaRegistryError(_))
      .leftMap(NonEmptyList.one)
  }

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: NodeId): NodeTransformationDefinition =
    topicParamStep orElse
      schemaParamStep orElse
      rawEditorParameterStep(context) orElse
      valueEditorParamStep(context)

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalStateOpt: Option[State]): Sink = {
    val preparedTopic = extractPreparedTopic(params)
    val key = params(SinkKeyParamName).asInstanceOf[LazyParameter[CharSequence]]
    val finalState = finalStateOpt.getOrElse(throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation"))

    val sinkValue = SinkValue.applyUnsafe(finalState.sinkValueParameter.getOrElse(SinkSingleValueParameter(rawValueParam)), parameterValues = params)
    val valueLazyParam = sinkValue.toLazyParameter

    val serializationSchema = schemaBasedMessagesSerdeProvider.serializationSchemaFactory.create(preparedTopic.prepared, Option(finalState.schema), kafkaConfig)
    val clientId = s"${TypedNodeDependency[MetaData].extract(dependencies).id}-${preparedTopic.prepared}"

    implProvider.createSink(preparedTopic, key, valueLazyParam, kafkaConfig, serializationSchema, clientId, finalState.schema, ValidationMode.strict)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[MetaData], TypedNodeDependency[NodeId])

}

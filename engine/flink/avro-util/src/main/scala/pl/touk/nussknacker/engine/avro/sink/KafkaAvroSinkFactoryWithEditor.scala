package pl.touk.nussknacker.engine.avro.sink

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory}
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.{KafkaAvroBaseTransformer, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.{SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, TopicParamName}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.flink.api.process.FlinkSink
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.sink.KafkaAvroSinkFactory.extractValidationMode


object KafkaAvroSinkFactoryWithEditor {

  private val paramsDeterminedAfterSchema = List(
    Parameter[String](KafkaAvroBaseTransformer.SinkValidationModeParameterName)
      .copy(editor = Some(FixedValuesParameterEditor(ValidationMode.values.map(ep => FixedExpressionValue(s"'${ep.name}'", ep.label))))),
    Parameter.optional[CharSequence](KafkaAvroBaseTransformer.SinkKeyParamName).copy(isLazyParameter = true)
  )
}

class KafkaAvroSinkFactoryWithEditor(val schemaRegistryProvider: SchemaRegistryProvider, val processObjectDependencies: ProcessObjectDependencies)
  extends SinkFactory with KafkaAvroBaseTransformer[FlinkSink] {

  private var sinkValueParameter: Option[AvroSinkValueParameter] = None

  override def paramsDeterminedAfterSchema: List[Parameter] = KafkaAvroSinkFactoryWithEditor.paramsDeterminedAfterSchema

  private def valueParamStep(context: ValidationContext)(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep
      (
        (TopicParamName, DefinedEagerParameter(topic: String, _)) ::
        (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::
        (SinkValidationModeParameterName, _) ::
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
        val typing = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema)
        AvroSinkValueParameter(typing).map { valueParam =>
          sinkValueParameter = Some(valueParam)
          NextParameters(valueParam.toParameters)
        }
      }.valueOr(e => FinalResults(context, e.toList))
  }

  protected def finalParamStep(context: ValidationContext)(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(
      (TopicParamName, _) :: (SchemaVersionParamName, _) :: (SinkValidationModeParameterName, _) :: (SinkKeyParamName, _) ::
      valueParams, _) if valueParams.nonEmpty => FinalResults(context, Nil)
  }

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: NodeId): NodeTransformationDefinition =
    topicParamStep orElse
      schemaParamStep orElse
        valueParamStep(context) orElse
          finalParamStep(context)

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): FlinkSink = {
    implicit val nodeId: NodeId = typedDependency[NodeId](dependencies)
    val preparedTopic = extractPreparedTopic(params)
    val versionOption = extractVersionOption(params)
    val processMetaData = typedDependency[NodeId](dependencies)
    val clientId = s"${processMetaData.id}-${preparedTopic.prepared}"

    val schemaDeterminer = prepareValueSchemaDeterminer(preparedTopic, versionOption)
    val schemaData = schemaDeterminer.determineSchemaUsedInTyping.valueOr(SchemaDeterminerErrorHandler.handleSchemaRegistryErrorAndThrowException)
    val schemaUsedInRuntime = schemaDeterminer.toRuntimeSchema(schemaData)

    val validationMode = extractValidationMode(params(SinkValidationModeParameterName).asInstanceOf[String])
    val sinkValue = AvroSinkValue.applyUnsafe(sinkValueParameter.get, parameterValues = params)
    val key = params(SinkKeyParamName).asInstanceOf[LazyParameter[CharSequence]]

    new KafkaAvroSink(preparedTopic, versionOption, key, sinkValue, kafkaConfig, schemaRegistryProvider.serializationSchemaFactory,
      schemaData.serializableSchema, schemaUsedInRuntime.map(_.serializableSchema), clientId, validationMode)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]), TypedNodeDependency(classOf[NodeId]))

  override def requiresOutput: Boolean = false
}

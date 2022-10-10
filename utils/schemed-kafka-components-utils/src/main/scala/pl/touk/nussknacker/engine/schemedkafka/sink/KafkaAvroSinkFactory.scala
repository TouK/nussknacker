package pl.touk.nussknacker.engine.schemedkafka.sink

import cats.data.NonEmptyList
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{BaseDefinedParameter, DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.schemedkafka.encode.AvroSchemaOutputValidator
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaBasedSerdeProvider, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.{KafkaUniversalComponentTransformer, RuntimeSchemaData, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.extractValidationMode
import pl.touk.nussknacker.engine.util.output.OutputValidatorErrorsConverter

object KafkaAvroSinkFactory {

  case class KafkaAvroSinkFactoryState(schema: RuntimeSchemaData[AvroSchema], runtimeSchema: Option[RuntimeSchemaData[AvroSchema]])

  private[sink] val paramsDeterminedAfterSchema = List(
    Parameter[String](KafkaUniversalComponentTransformer.SinkValidationModeParameterName)
      .copy(editor = Some(FixedValuesParameterEditor(ValidationMode.values.map(ep => FixedExpressionValue(s"'${ep.name}'", ep.label))))),
    Parameter.optional[CharSequence](KafkaUniversalComponentTransformer.SinkKeyParamName).copy(isLazyParameter = true),
    Parameter[AnyRef](KafkaUniversalComponentTransformer.SinkValueParamName).copy(isLazyParameter = true)
  )
}

class KafkaAvroSinkFactory(val schemaRegistryClientFactory: SchemaRegistryClientFactory,
                           val schemaBasedMessagesSerdeProvider: SchemaBasedSerdeProvider,
                           val processObjectDependencies: ProcessObjectDependencies,
                           implProvider: KafkaAvroSinkImplFactory)
  extends KafkaUniversalComponentTransformer[Sink] with SinkFactory {

  import KafkaAvroSinkFactory._

  override type State = KafkaAvroSinkFactoryState

  private val outputValidatorErrorsConverter = new OutputValidatorErrorsConverter(KafkaUniversalComponentTransformer.SinkValueParamName)

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: NodeId): NodeTransformationDefinition = topicParamStep orElse schemaParamStep orElse {
    case TransformationStep(
    (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
      (KafkaUniversalComponentTransformer.SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::
      (KafkaUniversalComponentTransformer.SinkValidationModeParameterName, DefinedEagerParameter(mode: String, _)) ::
      (KafkaUniversalComponentTransformer.SinkKeyParamName, _: BaseDefinedParameter) ::
      (KafkaUniversalComponentTransformer.SinkValueParamName, value: BaseDefinedParameter) :: Nil, _
    ) =>
      //we cast here, since null will not be matched in case...
      val preparedTopic = prepareTopic(topic)
      val versionOption = parseVersionOption(version)
      val schemaDeterminer = prepareValueSchemaDeterminer(preparedTopic, versionOption)
      val determinedSchema = schemaDeterminer.determineSchemaUsedInTyping
        .leftMap(SchemaDeterminerErrorHandler.handleSchemaRegistryError)
        .leftMap(NonEmptyList.one)
      val validatedSchema = determinedSchema.andThen { s =>
        schemaBasedMessagesSerdeProvider.validateSchema(s.schema)
          .leftMap(_.map(e => CustomNodeError(nodeId.id, e.getMessage, None)))
      }
      val validationResult = validatedSchema
        .andThen { schema =>
          new AvroSchemaOutputValidator(extractValidationMode(mode))
            .validateTypingResultToSchema(value.returnType, schema.rawSchema())
            .leftMap(outputValidatorErrorsConverter.convertValidationErrors)
            .leftMap(NonEmptyList.one)
        }.swap.toList.flatMap(_.toList)
      val finalState = determinedSchema.toOption.map(schema => KafkaAvroSinkFactoryState(schema, schemaDeterminer.toRuntimeSchema(schema)))
      FinalResults(context, validationResult, finalState)
    //edge case - for some reason Topic/Version is not defined
    case TransformationStep(
    (`topicParamName`, _) ::
      (KafkaUniversalComponentTransformer.SchemaVersionParamName, _) ::
      (KafkaUniversalComponentTransformer.SinkValidationModeParameterName, _) ::
      (KafkaUniversalComponentTransformer.SinkKeyParamName, _) ::
      (KafkaUniversalComponentTransformer.SinkValueParamName, _) :: Nil, _
    ) => FinalResults(context, Nil)
  }

  override def paramsDeterminedAfterSchema: List[Parameter] = KafkaAvroSinkFactory.paramsDeterminedAfterSchema

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalStateOpt: Option[State]): Sink = {
    val preparedTopic = extractPreparedTopic(params)
    val key = params(KafkaUniversalComponentTransformer.SinkKeyParamName).asInstanceOf[LazyParameter[CharSequence]]
    val value = params(KafkaUniversalComponentTransformer.SinkValueParamName).asInstanceOf[LazyParameter[AnyRef]]
    val finalState = finalStateOpt.getOrElse(throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation"))

    val serializationSchema = schemaBasedMessagesSerdeProvider.serializationSchemaFactory.create(preparedTopic.prepared, finalState.runtimeSchema.map(_.toParsedSchemaData), kafkaConfig)
    val validationMode = extractValidationMode(params(KafkaUniversalComponentTransformer.SinkValidationModeParameterName).asInstanceOf[String])
    val clientId = s"${TypedNodeDependency[MetaData].extract(dependencies).id}-${preparedTopic.prepared}"

    implProvider.createSink(preparedTopic, key, value,
      kafkaConfig, serializationSchema, clientId, finalState.schema, validationMode)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[MetaData], TypedNodeDependency[NodeId])

}

package pl.touk.nussknacker.engine.avro.sink

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{BaseDefinedParameter, DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.avro.encode.{OutputValidator, ValidationMode}
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, SchemaRegistryProvider}
import pl.touk.nussknacker.engine.avro.{KafkaAvroBaseComponentTransformer, KafkaAvroBaseTransformer, RuntimeSchemaData, SchemaDeterminerErrorHandler}

object KafkaAvroSinkFactory {

  case class KafkaAvroSinkFactoryState(schema: RuntimeSchemaData, runtimeSchema: Option[RuntimeSchemaData])

  private[sink] val paramsDeterminedAfterSchema = List(
    Parameter[String](KafkaAvroBaseComponentTransformer.SinkValidationModeParameterName)
      .copy(editor = Some(FixedValuesParameterEditor(ValidationMode.values.map(ep => FixedExpressionValue(s"'${ep.name}'", ep.label))))),
    Parameter.optional[CharSequence](KafkaAvroBaseComponentTransformer.SinkKeyParamName).copy(isLazyParameter = true),
    Parameter[AnyRef](KafkaAvroBaseComponentTransformer.SinkValueParamName).copy(isLazyParameter = true)
  )

  private[sink] def extractValidationMode(value: String): ValidationMode =
    ValidationMode.byName(value).getOrElse(throw CustomNodeValidationException(s"Unknown validation mode: $value", Some(KafkaAvroBaseComponentTransformer.SinkValidationModeParameterName)))
}

class KafkaAvroSinkFactory(val schemaRegistryProvider: SchemaRegistryProvider,
                           val processObjectDependencies: ProcessObjectDependencies,
                           implProvider: KafkaAvroSinkImplFactory)
  extends KafkaAvroBaseTransformer[Sink] with SinkFactory {

  import KafkaAvroSinkFactory._

  override type State = KafkaAvroSinkFactoryState

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = topicParamStep orElse schemaParamStep orElse {
    case TransformationStep(
    (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
      (KafkaAvroBaseComponentTransformer.SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::
      (KafkaAvroBaseComponentTransformer.SinkValidationModeParameterName, DefinedEagerParameter(mode: String, _)) ::
      (KafkaAvroBaseComponentTransformer.SinkKeyParamName, _: BaseDefinedParameter) ::
      (KafkaAvroBaseComponentTransformer.SinkValueParamName, value: BaseDefinedParameter) :: Nil, _
    ) =>
      //we cast here, since null will not be matched in case...
      val preparedTopic = prepareTopic(topic)
      val versionOption = parseVersionOption(version)
      val schemaDeterminer = prepareValueSchemaDeterminer(preparedTopic, versionOption)
      val determinedSchema = schemaDeterminer.determineSchemaUsedInTyping
        .leftMap(SchemaDeterminerErrorHandler.handleSchemaRegistryError)
        .leftMap(NonEmptyList.one)
      val validatedSchema = determinedSchema.andThen { s =>
        schemaRegistryProvider.validateSchema(s.schema)
          .leftMap(_.map(e => CustomNodeError(nodeId.id, e.getMessage, None)))
      }
      val validationResult = validatedSchema
        .andThen { schema =>
          OutputValidator.validateOutput(value.returnType, schema, extractValidationMode(mode))
            .leftMap(NonEmptyList.one)
        }.swap.toList.flatMap(_.toList)
      val finalState = determinedSchema.toOption.map(schema => KafkaAvroSinkFactoryState(schema, schemaDeterminer.toRuntimeSchema(schema)))
      FinalResults(context, validationResult, finalState)
    //edge case - for some reason Topic/Version is not defined
    case TransformationStep(
    (`topicParamName`, _) ::
      (KafkaAvroBaseComponentTransformer.SchemaVersionParamName, _) ::
      (KafkaAvroBaseComponentTransformer.SinkValidationModeParameterName, _) ::
      (KafkaAvroBaseComponentTransformer.SinkKeyParamName, _) ::
      (KafkaAvroBaseComponentTransformer.SinkValueParamName, _) :: Nil, _
    ) => FinalResults(context, Nil)
  }

  override def paramsDeterminedAfterSchema: List[Parameter] = KafkaAvroSinkFactory.paramsDeterminedAfterSchema

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalStateOpt: Option[State]): Sink = {
    val preparedTopic = extractPreparedTopic(params)
    val versionOption = extractVersionOption(params)
    val key = params(KafkaAvroBaseComponentTransformer.SinkKeyParamName).asInstanceOf[LazyParameter[CharSequence]]
    val value = params(KafkaAvroBaseComponentTransformer.SinkValueParamName).asInstanceOf[LazyParameter[AnyRef]]
    val finalState = finalStateOpt.getOrElse(throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation"))

    val versionOpt = Option(versionOption).collect {
      case ExistingSchemaVersion(version) => version
    }
    val serializationSchema = schemaRegistryProvider.serializationSchemaFactory.create(preparedTopic.prepared, versionOpt, finalState.runtimeSchema.map(_.serializableSchema), kafkaConfig)
    val validationMode = extractValidationMode(params(KafkaAvroBaseComponentTransformer.SinkValidationModeParameterName).asInstanceOf[String])
    val clientId = s"${TypedNodeDependency[MetaData].extract(dependencies).id}-${preparedTopic.prepared}"

    implProvider.createSink(preparedTopic, key, value,
      kafkaConfig, serializationSchema, clientId, finalState.schema, validationMode)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[MetaData], TypedNodeDependency[NodeId])

}

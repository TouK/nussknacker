package pl.touk.nussknacker.engine.avro.sink

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.api.typed.typing.{TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.{KafkaAvroBaseTransformer, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.{SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, SinkValueParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.flink.api.process.FlinkSink
import pl.touk.nussknacker.engine.api.typed
import pl.touk.nussknacker.engine.definition.parameter.editor.ParameterTypeEditorDeterminer

object KafkaAvroSinkFactory {

  private val restrictedParamNames = Set(SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, TopicParamName)

  private val paramsDeterminedAfterSchema = List(
    Parameter[String](SinkValidationModeParameterName)
      .copy(editor = Some(FixedValuesParameterEditor(ValidationMode.values.map(ep => FixedExpressionValue(s"'${ep.name}'", ep.label))))),
    Parameter.optional[CharSequence](SinkKeyParamName).copy(isLazyParameter = true)
  )

  private def extractValidationMode(value: String): ValidationMode =
    ValidationMode.byName(value).getOrElse(throw CustomNodeValidationException(s"Unknown validation mode: $value", Some(SinkValidationModeParameterName)))

  private def containsRestrictedNames(obj: TypedObjectTypingResult): Boolean =
    (obj.fields.keySet & restrictedParamNames).nonEmpty
  private def toParamEither(typing: TypingResult)(implicit nodeId: NodeId): Validated[ProcessCompilationError, Either[List[Parameter], Parameter]] = {
    def toSingleParam(name: String, typing: TypingResult) =
      Parameter(name, typing).copy(
        isLazyParameter = true,
        // TODO
        editor = new ParameterTypeEditorDeterminer(typing).determine()
      )

    def toObjectFieldParams(typedObject: TypedObjectTypingResult) =
      typedObject.fields.map { case (name, typing) =>
        toSingleParam(name, typing)
      }.toList

    def toParamEither(typing: TypingResult, isRoot: Boolean)(implicit nodeId: NodeId) =
      typing match {
        case typed.typing.Unknown =>
          Invalid(CustomNodeError(nodeId.id, "Cannot determine typing for provided schema", None))
        case typedObject: TypedObjectTypingResult if containsRestrictedNames(typedObject) =>
          Invalid(CustomNodeError(nodeId.id, s"""Record field name is restricted. Restricted names are ${restrictedParamNames.mkString(", ")}""", None))
        case typedObject: TypedObjectTypingResult if isRoot =>
          Valid(Left(toObjectFieldParams(typedObject)))
        case _: TypedObjectTypingResult =>
          Valid(Right(toSingleParam(SinkValueParamName, typing)))
        case _ =>
          Valid(Right(toSingleParam(SinkValueParamName, typing)))
      }

    toParamEither(typing, isRoot = true)
  }
}

class KafkaAvroSinkFactory(val schemaRegistryProvider: SchemaRegistryProvider, val processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaAvroSinkFactory with KafkaAvroBaseTransformer[FlinkSink] {
  import KafkaAvroSinkFactory._
  import cats.implicits.catsSyntaxEither

  private var paramEither: Either[List[Parameter], Parameter] = Left(Nil)

  protected def topicParamStep(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val topicParam = getTopicParam.map(List(_))
      NextParameters(parameters = topicParam.value, errors = topicParam.written)
  }

  protected def schemaParamStep(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep((TopicParamName, DefinedEagerParameter(topic: String, _)) :: Nil, _) =>
      val preparedTopic = prepareTopic(topic)
      val version = getVersionParam(preparedTopic)
      NextParameters(version.value :: paramsDeterminedAfterSchema, errors = version.written)
    case TransformationStep((TopicParamName, _) :: Nil, _) =>
      NextParameters(parameters = fallbackVersionOptionParam :: paramsDeterminedAfterSchema)
  }

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
      val schemaDeterminer = prepareSchemaDeterminer(preparedTopic, versionOption)
      val schemaValidated = schemaDeterminer
        .determineSchemaUsedInTyping
        .leftMap(SchemaDeterminerErrorHandler.handleSchemaRegistryError(_))
      schemaValidated.andThen { runtimeSchema =>
        val typing = AvroSchemaTypeDefinitionExtractor.typeDefinition(runtimeSchema.schema)
        toParamEither(typing).map { paramE =>
          paramEither = paramE
          paramEither match {
            case Right(value) => NextParameters(value :: Nil)
            case Left(fields) => NextParameters(fields)
          }
        }
      }.valueOr(e => FinalResults(context, e :: Nil))
  }

  /* TODO, FIXME:
    1. optional params with default in avro schema
 */
  protected def finalParamStep(context: ValidationContext)(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(
      (TopicParamName, _) :: (SchemaVersionParamName, _) :: (SinkValidationModeParameterName, _) :: (SinkKeyParamName, _) ::
      valueParams, _) if valueParams.nonEmpty => FinalResults(context, Nil)
  }

  override def initialParameters: List[Parameter] = {
    implicit val nodeId: NodeId = NodeId("")
    val topic = getTopicParam.value
    topic :: getVersionParam(Nil) :: paramsDeterminedAfterSchema
  }

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: NodeId): NodeTransformationDefinition =
    topicParamStep orElse
      schemaParamStep orElse
        valueParamStep(context) orElse
          finalParamStep(context)

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): FlinkSink = {
    val preparedTopic = extractPreparedTopic(params)
    val versionOption = extractVersionOption(params)
    val key = params(SinkKeyParamName).asInstanceOf[LazyParameter[CharSequence]]
    val validationMode = extractValidationMode(params(SinkValidationModeParameterName).asInstanceOf[String])
    val valueEither = paramEither
      .map(_ => params(SinkValueParamName).asInstanceOf[LazyParameter[AnyRef]])
      .leftMap(_.map(p => (p.name, params(p.name).asInstanceOf[LazyParameter[AnyRef]])))

    createSink(preparedTopic, versionOption, key, valueEither,
      kafkaConfig, schemaRegistryProvider.serializationSchemaFactory, prepareSchemaDeterminer(preparedTopic, versionOption), validationMode)(
      typedDependency[MetaData](dependencies), typedDependency[NodeId](dependencies))
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]), TypedNodeDependency(classOf[NodeId]))
}

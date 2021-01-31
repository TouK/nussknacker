package pl.touk.nussknacker.engine.avro.sink

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.transformation.{BaseDefinedParameter, DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.api.typed.typing.{TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.{KafkaAvroBaseTransformer, RuntimeSchemaData, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.{SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, SinkValueParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.flink.api.process.FlinkSink
import pl.touk.nussknacker.engine.api.typed

object KafkaAvroSinkFactory {

  private val paramsDeterminedAfterSchema = List(
    Parameter[String](SinkValidationModeParameterName)
      .copy(editor = Some(FixedValuesParameterEditor(ValidationMode.values.map(ep => FixedExpressionValue(s"'${ep.name}'", ep.label))))),
    Parameter.optional[CharSequence](SinkKeyParamName).copy(isLazyParameter = true)
  )

  private def toParamEither(typing: TypingResult): Either[List[Parameter], Parameter] = {
    // TODO: optional params with default in avro schema

    // TODO: ParameterEditor
    def toSingleParam(name: String, typing: TypingResult): Parameter =
      Parameter(name, typing).copy(isLazyParameter = true)

    def toObjectFieldParams(typedObject: TypedObjectTypingResult): List[Parameter] =
      typedObject.fields.map { case (name, typing) =>
        toSingleParam(name, typing)
      }.toList

    def toParamEither(typing: TypingResult, isRoot: Boolean): Either[List[Parameter], Parameter] =
      typing match {
          // TODO
//        case typed.typing.Unknown => ???
        case typedObject: TypedObjectTypingResult if isRoot =>
          val obj = toObjectFieldParams(typedObject)
          Left(obj)
        case _: TypedObjectTypingResult =>
          Right(toSingleParam(SinkValueParamName, typing))
        case _ =>
          Right(toSingleParam(SinkValueParamName, typing))
      }

    toParamEither(typing, isRoot = true)
  }
}

class KafkaAvroSinkFactory(val schemaRegistryProvider: SchemaRegistryProvider, val processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaAvroSinkFactory with KafkaAvroBaseTransformer[FlinkSink] {
  import KafkaAvroSinkFactory._

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

  private def valueParamStep(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep
      (
        (TopicParamName, DefinedEagerParameter(topic: String, _)) ::
        (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::
        (SinkValidationModeParameterName, DefinedEagerParameter(mode: String, _)) ::
        (SinkKeyParamName, _: BaseDefinedParameter) :: Nil,
        _
      ) =>
      val preparedTopic = prepareTopic(topic)
      val versionOption = parseVersionOption(version)
      val schemaDeterminer = prepareSchemaDeterminer(preparedTopic, versionOption)
      val schemaValidated = schemaDeterminer.determineSchemaUsedInTyping
      schemaValidated
        .map { runtimeSchema =>
          val typing = AvroSchemaTypeDefinitionExtractor.typeDefinition(runtimeSchema.schema)
          paramEither = toParamEither(typing)
          NextParameters(parameters = paramEither.map(_ :: Nil).getOrElse(Nil))
        }
        .valueOr { e =>
          val compilationErrors = SchemaDeterminerErrorHandler.handleSchemaRegistryError(e) :: Nil
          NextParameters(parameters = Nil, errors = compilationErrors)
        }
  }

  /*
TODO, FIXME:
1. pola ze schematu nie mogą się pokrywać z topic, key param name
2. dedykowane formatki, np dla bool
3.
 */
  protected def finalParamStep(context: ValidationContext)(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(
    (TopicParamName, DefinedEagerParameter(topic: String, _)) ::
      (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::
      (SinkValidationModeParameterName, DefinedEagerParameter(mode: String, _)) ::
      (SinkKeyParamName, _: BaseDefinedParameter) ::
      (SinkValueParamName, value: BaseDefinedParameter) :: valueParams, _
    ) if valueParams.nonEmpty =>
      //we cast here, since null will not be matched in case...
//      val preparedTopic = prepareTopic(topic)
//      val versionOption = parseVersionOption(version)
//      val schemaDeterminer = prepareSchemaDeterminer(preparedTopic, versionOption)
//      val validationResult = schemaDeterminer.determineSchemaUsedInTyping
//        .leftMap(SchemaDeterminerErrorHandler.handleSchemaRegistryError)
//        .andThen(schemaData => validateValueType(value.returnType, schemaData.schema, extractValidationMode(mode))).swap.toList
//      TODO: validation
      FinalResults(context, Nil)
    //edge case - for some reason Topic/Version is not defined
    case TransformationStep(
    (TopicParamName, _) ::
      (SchemaVersionParamName, _) ::
      (SinkValidationModeParameterName, _) ::
      (SinkKeyParamName, _) ::
      (SinkValueParamName, _) :: valueParams, _
    ) if valueParams.nonEmpty =>
      FinalResults(context, Nil)
  }

  override def initialParameters: List[Parameter] = {
    implicit val nodeId: NodeId = NodeId("")
    val topic = getTopicParam.value
    List(topic, getVersionParam(Nil)) ++ paramsDeterminedAfterSchema
  }

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: NodeId): NodeTransformationDefinition =
    topicParamStep orElse
      schemaParamStep orElse
        valueParamStep orElse
          finalParamStep(context)

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): FlinkSink = {
    import cats.implicits.catsSyntaxEither
    val preparedTopic = extractPreparedTopic(params)
    val versionOption = extractVersionOption(params)
    val key = params(SinkKeyParamName).asInstanceOf[LazyParameter[CharSequence]]

    val valueEither = paramEither
      .map(_ => params(SinkValueParamName).asInstanceOf[LazyParameter[AnyRef]])
      .leftMap(_.map(p => (p.name, params(p.name).asInstanceOf[LazyParameter[AnyRef]])))

    val validationMode = extractValidationMode(params(SinkValidationModeParameterName).asInstanceOf[String])

    createSink(preparedTopic, versionOption, key, valueEither,
      kafkaConfig, schemaRegistryProvider.serializationSchemaFactory, prepareSchemaDeterminer(preparedTopic, versionOption), validationMode)(
      typedDependency[MetaData](dependencies), typedDependency[NodeId](dependencies))
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]), TypedNodeDependency(classOf[NodeId]))

  private def extractValidationMode(value: String): ValidationMode
    = ValidationMode.byName(value).getOrElse(throw CustomNodeValidationException(s"Unknown validation mode: $value", Some(SinkValidationModeParameterName)))
}

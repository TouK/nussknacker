package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks
import cats.data.NonEmptyList
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition.{BoolParameterEditor, MandatoryParameterValidator, NodeDependency, Parameter, ParameterWithExtractor, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{MetaData, NodeId}
import pl.touk.nussknacker.engine.json.{JsonSchemaExtractor, JsonSchemaSubclassDeterminer, JsonSinkValueParameter}
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.JsonRequestResponseSink.{SinkRawEditorParamName, SinkRawValueParamName}
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings.OutputSchemaProperty
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValue
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.{SinkSingleValueParameter, SinkValueParameter}

object JsonRequestResponseSink {

  final val SinkRawValueParamName: String = "Value"
  final val SinkRawEditorParamName: String = "Raw editor"

}
class JsonRequestResponseSinkFactory(implProvider: ResponseRequestSinkImplFactory) extends SingleInputGenericNodeTransformation[Sink] with SinkFactory {

  override type State = EditorTransformationState
  private val jsonSchemaExtractor = new JsonSchemaExtractor()

  final val rawModeParam: Parameter = Parameter[Boolean](SinkRawEditorParamName).copy(defaultValue = Some("false"), editor = Some(BoolParameterEditor), validators = List(MandatoryParameterValidator))

  private val rawValueParam = ParameterWithExtractor.lazyMandatory[AnyRef](SinkRawValueParamName)

  def rawParamStep()(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(parameters = rawModeParam :: Nil, state = None)

  }

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
    rawParamStep() orElse
      rawEditorParamStep(context, dependencies) orElse
      valueEditorParamStep(context, dependencies)
  }

  protected def rawEditorParamStep(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep((SinkRawEditorParamName, DefinedEagerParameter(true, _)) :: Nil, _) =>
      NextParameters(rawValueParam.parameter :: Nil)
    case TransformationStep((SinkRawEditorParamName, DefinedEagerParameter(true, _)) :: (SinkRawValueParamName, value) :: Nil, _) =>
      val determinedSchema = jsonSchemaExtractor.getSchemaFromProperty(OutputSchemaProperty, dependencies)

      val validationResult = determinedSchema.andThen { schema =>
        new JsonSchemaSubclassDeterminer(schema).validateTypingResultToSchema(value.returnType, SinkRawValueParamName).leftMap(NonEmptyList.one)
      }.swap.toList.flatMap(_.toList)

      //todo handle errors
      val schema = determinedSchema.toOption.get
      val valueParam: SinkValueParameter = SinkSingleValueParameter(rawValueParam.parameter)

      FinalResults(context, validationResult, Option(EditorTransformationState(schema, valueParam)))
  }

  protected def valueEditorParamStep(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep((SinkRawEditorParamName, DefinedEagerParameter(false, _)) :: Nil, _) =>
      jsonSchemaExtractor.getSchemaFromProperty(OutputSchemaProperty, dependencies)
        .andThen { schema =>
          JsonSinkValueParameter(schema, SinkRawValueParamName).map { valueParam =>
            val state = EditorTransformationState(schema, valueParam)
            NextParameters(valueParam.toParameters, state = Option(state))
          }
        }.valueOr(e => FinalResults(context, e.toList))
    case TransformationStep((SinkRawEditorParamName, DefinedEagerParameter(false, _)) :: valueParams, state) =>
      FinalResults(context, Nil, state)
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalStateOpt: Option[State]): Sink = {
    val finalState = finalStateOpt.getOrElse(throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation"))
    val sinkValue = SinkValue.applyUnsafe(finalState.sinkValueParameter, parameterValues = params)
    val valueLazyParam = sinkValue.toLazyParameter

    implProvider.createSink(valueLazyParam, finalState.schema)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[MetaData], TypedNodeDependency[NodeId])

  case class EditorTransformationState(schema: Schema, sinkValueParameter: SinkValueParameter)

}
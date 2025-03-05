package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks

import cats.data.Validated.valid
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, Params}
import pl.touk.nussknacker.engine.api.component.RequestResponseComponent
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.json.{JsonSchemaBasedParameter, JsonSchemaExtractor}
import pl.touk.nussknacker.engine.json.encode.JsonSchemaOutputValidator
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings.OutputSchemaProperty
import pl.touk.nussknacker.engine.util.parameters.{SchemaBasedParameter, SingleSchemaBasedParameter}
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValue

object JsonRequestResponseSink {

  final val SinkRawValueParamName: ParameterName           = ParameterName("Value")
  final val SinkRawEditorParamName: ParameterName          = ParameterName("Raw editor")
  final val SinkValidationModeParameterName: ParameterName = ParameterName("Value validation mode")

}

class JsonRequestResponseSinkFactory(implProvider: ResponseRequestSinkImplFactory)
    extends SingleInputDynamicComponent[Sink]
    with SinkFactory
    with RequestResponseComponent {
  import JsonRequestResponseSink._
  override type State = EditorTransformationState
  private val jsonSchemaExtractor = new JsonSchemaExtractor()

  private val rawModeParam: Parameter = Parameter[Boolean](SinkRawEditorParamName).copy(
    defaultValue = Some(Expression.spel("false")),
    editor = Some(BoolParameterEditor),
    validators = List(MandatoryParameterValidator)
  )

  private val rawValueParam = ParameterDeclaration.lazyMandatory[AnyRef](SinkRawValueParamName).withCreator()

  private val validationModeParam = Parameter[String](SinkValidationModeParameterName).copy(
    editor =
      Some(FixedValuesParameterEditor(ValidationMode.values.map(ep => FixedExpressionValue(s"'${ep.name}'", ep.label))))
  )

  def rawParamStep()(implicit nodeId: NodeId): ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
    NextParameters(parameters = rawModeParam :: Nil, state = None)

  }

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    rawParamStep() orElse
      rawEditorParamStep(context, dependencies) orElse
      valueEditorParamStep(context, dependencies)
  }

  protected def rawEditorParamStep(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep((SinkRawEditorParamName, DefinedEagerParameter(true, _)) :: Nil, _) =>
      NextParameters(validationModeParam :: rawValueParam.createParameter() :: Nil)
    case TransformationStep(
          (SinkRawEditorParamName, DefinedEagerParameter(true, _)) ::
          (SinkValidationModeParameterName, DefinedEagerParameter(mode: String, _)) ::
          (SinkRawValueParamName, value) :: Nil,
          _
        ) =>
      jsonSchemaExtractor
        .getSchemaFromProperty(OutputSchemaProperty, dependencies)
        .andThen { schema =>
          val valueParam = SingleSchemaBasedParameter(
            rawValueParam.createParameter(),
            new JsonSchemaOutputValidator(ValidationMode.fromString(mode, SinkValidationModeParameterName))
              .validate(_, schema)
          )
          val validationResult = valueParam.validateParams(Map(SinkRawValueParamName -> value))
          val state            = EditorTransformationState(schema, valueParam)
          valid(FinalResults(context, validationResult.swap.map(_.toList).getOrElse(Nil), Option(state)))
        }
        .valueOr(e => FinalResults(context, e.toList))
  }

  protected def valueEditorParamStep(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep((SinkRawEditorParamName, DefinedEagerParameter(false, _)) :: Nil, _) =>
      jsonSchemaExtractor
        .getSchemaFromProperty(OutputSchemaProperty, dependencies)
        .andThen { schema =>
          // in editor mode we use lax validation mode, to be backward compatible
          JsonSchemaBasedParameter(schema, SinkRawValueParamName, ValidationMode.lax).map { valueParam =>
            val state = EditorTransformationState(schema, valueParam)
            // shouldn't happen except for empty schema, but it can lead to infinite loop...
            if (valueParam.toParameters.isEmpty) {
              FinalResults(context, Nil, Some(state))
            } else {
              NextParameters(valueParam.toParameters, state = Option(state))
            }
          }
        }
        .valueOr(e => FinalResults(context, e.toList))
    case TransformationStep((SinkRawEditorParamName, DefinedEagerParameter(false, _)) :: valueParams, Some(state)) =>
      val errors = state.schemaBasedParameter.validateParams(valueParams.toMap).swap.map(_.toList).getOrElse(Nil)
      FinalResults(context, errors, Some(state))
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalStateOpt: Option[State]
  ): Sink = {
    val finalState = finalStateOpt.getOrElse(
      throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation")
    )
    val sinkValue      = SinkValue.applyUnsafe(finalState.schemaBasedParameter, parameterValues = params)
    val valueLazyParam = sinkValue.toLazyParameter

    implProvider.createSink(valueLazyParam, finalState.schema)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[MetaData], TypedNodeDependency[NodeId])

  case class EditorTransformationState(schema: Schema, schemaBasedParameter: SchemaBasedParameter)

}

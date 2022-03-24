package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.sinks

import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{MetaData, NodeId}
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.jsonschemautils.JsonSchemaExtractor
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings.OutputSchemaProperty
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValue
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.SinkValueParameter

class JsonRequestResponseSinkWithEditorFactory(implProvider: ResponseRequestSinkImplFactory) extends SingleInputGenericNodeTransformation[Sink] with SinkFactory  {

  override type State = EditorTransformationState
  private val jsonSchemaExtractor = new JsonSchemaExtractor()

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
    schemaParamStep(context, dependencies) orElse
      finalParamStep(context)
  }

  protected def schemaParamStep(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      jsonSchemaExtractor.getSchemaFromProperty(OutputSchemaProperty, dependencies)
        .andThen { schema => JsonSinkValueParameter(schema).map { valueParam =>
          val state = EditorTransformationState(schema, valueParam)
          NextParameters(valueParam.toParameters, state = Option(state))
        }
      }.valueOr(e => FinalResults(context, e.toList))
  }

  protected def finalParamStep(context: ValidationContext)(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(params, state) if params.nonEmpty =>
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

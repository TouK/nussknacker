package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.sinks

import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData, NodeId}
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.findmenewplace.JsonRequestResponseBaseTransformer
import pl.touk.nussknacker.engine.util.definition.LazyParameterUtils

import scala.collection.immutable.ListMap

class JsonRequestResponseSinkWithEditorFactory(implProvider: ResponseRequestSinkImplFactory) extends JsonRequestResponseBaseTransformer[Sink] {

  //FIXME
  val OutputSchemaProperty = "outputSchema"

  override type State = EditorTransformationState

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
    schemaParamStep(context, dependencies) orElse
      finalParamStep(context)
  }

  protected def schemaParamStep(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val determinedSchema = getRawSchemaFromProperty(OutputSchemaProperty, dependencies)

      determinedSchema.andThen { case(_, schema) =>
        JsonSinkValueParameter(schema).map { valueParam =>
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
    val sinkValue = JsonSinkValue.applyUnsafe(finalState.sinkValueParameter, parameterValues = params)
    val valueLazyParam = toLazyParameter(sinkValue)
    implProvider.createSink(valueLazyParam, finalState.schema)
  }

  //From KafkaAvroSinkFactoryWithEditor#toLazyParameter
  private def toLazyParameter(sv: JsonSinkValue): LazyParameter[AnyRef] = sv match {
    case JsonSinkSingleValue(value) =>
      value
    case JsonSinkRecordValue(fields) =>
      LazyParameterUtils.typedMap(ListMap(fields.toList.map {
        case (key, value) => key -> toLazyParameter(value)
      }: _*))
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[MetaData], TypedNodeDependency[NodeId])

  case class EditorTransformationState(schema: Schema, sinkValueParameter: JsonSinkValueParameter)

}

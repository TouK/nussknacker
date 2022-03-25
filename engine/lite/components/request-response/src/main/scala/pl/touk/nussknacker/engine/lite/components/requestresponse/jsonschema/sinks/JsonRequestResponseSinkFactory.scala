package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks

import cats.data.NonEmptyList
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.api.{MetaData, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{BaseDefinedParameter, NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.json.{JsonSchemaExtractor, JsonSchemaSubclassDeterminer}
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.JsonRequestResponseSinkFactory._
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings.OutputSchemaProperty

object JsonRequestResponseSinkFactory {

  final val SinkValueParamName: String = "Value"
  private val sinkParamsDefinition = ParameterWithExtractor.lazyMandatory[AnyRef](SinkValueParamName)

}

class JsonRequestResponseSinkFactory(implProvider: ResponseRequestSinkImplFactory) extends SingleInputGenericNodeTransformation[Sink] with SinkFactory {

  override type State = Schema
  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[MetaData], TypedNodeDependency[NodeId])

  private val jsonSchemaExtractor = new JsonSchemaExtractor()

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(parameters = sinkParamsDefinition.parameter :: Nil, errors = Nil)

    case TransformationStep((SinkValueParamName, value: BaseDefinedParameter) :: Nil, _) =>
      val determinedSchema = jsonSchemaExtractor.getSchemaFromProperty(OutputSchemaProperty, dependencies)

      val validationResult = determinedSchema.andThen { schema =>
          new JsonSchemaSubclassDeterminer(schema).validateTypingResultToSchema(value.returnType, SinkValueParamName).leftMap(NonEmptyList.one)
      }.swap.toList.flatMap(_.toList)

      FinalResults(context, validationResult, determinedSchema.toOption)
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalStateOpt: Option[State]): Sink = {
    val value = sinkParamsDefinition.extractValue(params)
    val finalSchemaState = finalStateOpt.getOrElse(
      throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation")
    )
    implProvider.createSink(value, finalSchemaState)
  }

}

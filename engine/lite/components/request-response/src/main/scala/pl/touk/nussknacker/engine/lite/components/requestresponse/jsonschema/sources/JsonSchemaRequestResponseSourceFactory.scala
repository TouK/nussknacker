package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sources

import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, Params}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputDynamicComponent}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process.{BasicContextInitializer, Source}
import pl.touk.nussknacker.engine.api.typed._
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.json.{JsonSchemaExtractor, SwaggerBasedJsonSchemaTypeDefinitionExtractor}
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponseSource, RequestResponseSourceFactory}
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings.{
  InputSchemaProperty,
  OutputSchemaProperty
}

class JsonSchemaRequestResponseSourceFactory
    extends RequestResponseSourceFactory
    with SingleInputDynamicComponent[Source] {

  override type State = (Schema, Schema)

  private val nodeIdDependency   = TypedNodeDependency[NodeId]
  private val metaDataDependency = TypedNodeDependency[MetaData]

  override def nodeDependencies: List[NodeDependency] = List(nodeIdDependency, metaDataDependency)

  private val jsonSchemaExtractor = new JsonSchemaExtractor()

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
    val inputSchema      = jsonSchemaExtractor.getSchemaFromProperty(InputSchemaProperty, dependencies)
    val outputSchema     = jsonSchemaExtractor.getSchemaFromProperty(OutputSchemaProperty, dependencies)
    val validationResult = inputSchema.product(outputSchema).swap.toList.flatMap(_.toList)
    val finalState       = inputSchema.product(outputSchema).toOption
    val finalInitializer = inputSchema.toOption.fold(new BasicContextInitializer(Unknown)) { schema =>
      val schemaTypingResult = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult
      new BasicContextInitializer(schemaTypingResult)
    }
    FinalResults.forValidation(context, validationResult, finalState)(finalInitializer.validationContext)
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalStateOpt: Option[(Schema, Schema)]
  ): RequestResponseSource[Any] = {
    val (inputSchemaState, outputSchemaState) = finalStateOpt.getOrElse(
      throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation")
    )
    val nodeId   = nodeIdDependency.extract(dependencies)
    val metaData = metaDataDependency.extract(dependencies)
    new JsonSchemaRequestResponseSource(
      inputSchemaState.toString,
      metaData,
      inputSchemaState,
      outputSchemaState,
      nodeId
    )
  }

}

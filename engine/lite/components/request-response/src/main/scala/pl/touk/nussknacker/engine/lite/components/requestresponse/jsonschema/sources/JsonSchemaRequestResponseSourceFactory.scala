package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sources

import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process.{BasicContextInitializer, Source}
import pl.touk.nussknacker.engine.api.typed._
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.api.{MetaData, NodeId}
import pl.touk.nussknacker.engine.json.{JsonSchemaExtractor, SwaggerBasedJsonSchemaTypeDefinitionExtractor}
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings.InputSchemaProperty
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponseSource, RequestResponseSourceFactory}

class JsonSchemaRequestResponseSourceFactory extends RequestResponseSourceFactory with SingleInputGenericNodeTransformation[Source] {

  override type State = Schema

  private val nodeIdDependency = TypedNodeDependency[NodeId]
  private val metaDataDependency = TypedNodeDependency[MetaData]
  override def nodeDependencies: List[NodeDependency] = List(nodeIdDependency, metaDataDependency)

  private val jsonSchemaExtractor = new JsonSchemaExtractor()

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val determinedSchema = jsonSchemaExtractor.getSchemaFromProperty(InputSchemaProperty, dependencies)
      val validationResult = determinedSchema.swap.toList.flatMap(_.toList)
      val finalState = determinedSchema.toOption
      val finalInitializer = determinedSchema.toOption.fold(new BasicContextInitializer(Unknown)) { schema =>
        val schemaTypingResult = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult
        new BasicContextInitializer(schemaTypingResult)
      }
      FinalResults.forValidation(context, validationResult, finalState)(finalInitializer.validationContext)
  }

  override def implementation(params: Map[String, Any],
                              dependencies: List[NodeDependencyValue],
                              finalStateOpt: Option[Schema]): RequestResponseSource[Any] = {
    val finalSchemaState = finalStateOpt.getOrElse(throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation"))
    val nodeId = nodeIdDependency.extract(dependencies)
    val metaData = metaDataDependency.extract(dependencies)
    new JsonSchemaRequestResponseSource(finalSchemaState.toString, metaData, finalSchemaState, nodeId)
  }

}
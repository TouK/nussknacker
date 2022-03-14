package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.source

import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue
import pl.touk.nussknacker.engine.api.process.{BasicContextInitializer, Source}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.swagger.{JsonRequestResponseBaseTransformer, JsonSchemaTypeDefinitionExtractor}
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponseSource, RequestResponseSourceFactory}

//TODO: Move it to NU
object JsonRequestResponseSourceFactory {

  val InputSchemaProperty = "inputSchema"

  case class RequestResponseSourceState(rawSchema: String, schema: Schema)

}

class JsonRequestResponseSourceFactory extends RequestResponseSourceFactory with JsonRequestResponseBaseTransformer[Source] {

  import JsonRequestResponseSourceFactory._

  override type State = RequestResponseSourceState

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId) = {
    case TransformationStep(Nil, _) =>
      val determinedSchema = getRawSchemaFromProperty(InputSchemaProperty, dependencies)
      val validationResult = determinedSchema.swap.toList.flatMap(_.toList)

      val finalState = determinedSchema.toOption.map{
        case (rawSchema, schema) => RequestResponseSourceState(rawSchema, schema)
      }

      val finalInitializer = determinedSchema.toOption.fold(new BasicContextInitializer(Unknown)) { case (_, schema: Schema) =>
        val schemaTypingResult = JsonSchemaTypeDefinitionExtractor.typeDefinition(schema)
        new BasicContextInitializer(schemaTypingResult)
      }

      FinalResults.forValidation(context, validationResult, finalState)(finalInitializer.validationContext)
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalStateOpt: Option[RequestResponseSourceState]): RequestResponseSource[TypedMap] = {
    val finalState = finalStateOpt.getOrElse(throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation"))
    val nodeId = nodeIdDependency.extract(dependencies)
    val metaData = prepareMetadata(dependencies)
    new PostJsonSchemaSource(finalState.rawSchema, metaData, finalState.schema, nodeId)
  }

}
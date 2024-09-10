package pl.touk.nussknacker.test.utils

import com.networknt.schema.{InputFormat, JsonSchemaFactory, SchemaValidatorsConfig, ValidationMessage}
import io.circe.yaml.{parser => YamlParser}
import io.circe.{ACursor, Json}
import org.scalactic.anyvals.NonEmptyList
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.test.TapirJsonSchemaFactory

import scala.jdk.CollectionConverters._

class OpenAPIExamplesValidator private (schemaFactory: JsonSchemaFactory) {

  import OpenAPIExamplesValidator._

  def validateExamples(specYaml: String): List[InvalidExample] = {
    val specJson = YamlParser.parse(specYaml).toOption.get
    val componentsSchemas =
      specJson.hcursor
        .downField("components")
        .downField("schemas")
        .focusObjectFields
        .toMap
    for {
      (_, pathItem)  <- specJson.hcursor.downField("paths").focusObjectFields
      (_, operation) <- pathItem.asObject.map(_.toList).getOrElse(List.empty)
      operationId = operation.hcursor.downField("operationId").as[String].toTry.get
      invalidExample <- validateRequestExample(operation, operationId, componentsSchemas) :::
        validateResponsesExamples(operation, operationId, componentsSchemas)
    } yield invalidExample
  }

  private def validateRequestExample(
      operation: Json,
      operationId: String,
      componentsSchemas: Map[String, Json]
  ): List[InvalidExample] =
    for {
      (_, mediaType) <- operation.hcursor.downField("requestBody").downField("content").focusObjectFields
      exampleJson    <- validateMediaTypeExamples(mediaType, operationId, isRequest = true, componentsSchemas)
    } yield exampleJson

  private def validateResponsesExamples(
      operation: Json,
      operationId: String,
      componentsSchemas: Map[String, Json]
  ): List[InvalidExample] =
    for {
      (_, response)  <- operation.hcursor.downField("responses").focusObjectFields
      (_, mediaType) <- response.hcursor.downField("content").focusObjectFields
      exampleJson    <- validateMediaTypeExamples(mediaType, operationId, isRequest = false, componentsSchemas)
    } yield exampleJson

  private def validateMediaTypeExamples(
      mediaType: Json,
      operationId: String,
      isRequest: Boolean,
      componentsSchemas: Map[String, Json]
  ): List[InvalidExample] = {
    for {
      schema <- mediaType.hcursor.downField("schema").focus.toList
      resolvedSchema = resolveSchemaReferences(schema, componentsSchemas)
      jsonSchema     = schemaFactory.getSchema(resolvedSchema.spaces2)
      (exampleId, exampleValue) <- mediaType.hcursor.downField("example").focus.map("example" -> _).toList :::
        mediaType.hcursor.downField("examples").focusObjectFields.flatMap { case (exampleId, exampleRoot) =>
          exampleRoot.hcursor.downField("value").focus.toList.map(exampleId -> _)
        }
      invalidJson <- NonEmptyList
        .from(jsonSchema.validate(exampleValue.noSpaces, InputFormat.JSON).asScala.toList)
        .map { errors =>
          InvalidExample(exampleValue, resolvedSchema, operationId, isRequest, exampleId, errors)
        }
        .toList
    } yield invalidJson
  }

  private def resolveSchemaReferences(
      schema: Json,
      components: Map[String, Json]
  ): Json = {
    def resolveNested(nested: Json): Json = {
      nested.hcursor
        .downField("$ref")
        .withFocus(ref =>
          ref.asString.map(_.replace("#/components/schemas/", "#/definitions/")).map(Json.fromString).getOrElse(ref)
        )
        .top
        .getOrElse(nested)
        .fold(
          jsonNull = nested,
          jsonBoolean = Json.fromBoolean,
          jsonNumber = Json.fromJsonNumber,
          jsonString = Json.fromString,
          jsonArray = arr => Json.fromValues(arr.map(resolveNested)),
          jsonObject = obj => Json.fromFields(obj.toMap.mapValuesNow(resolveNested))
        )
    }

    val topLevel = schema.mapObject(
      _.add(
        "definitions",
        Json.fromFields(components.mapValuesNow(resolveNested))
      )
    )

    resolveNested(topLevel)
  }

}

final case class InvalidExample(
    example: Json,
    resolvedSchema: Json,
    operationId: String,
    isRequest: Boolean,
    exampleId: String,
    errors: NonEmptyList[ValidationMessage]
)

object OpenAPIExamplesValidator {

  val forTapir = new OpenAPIExamplesValidator(TapirJsonSchemaFactory.instance)

  private implicit class ACursorExt(aCursor: ACursor) {
    def focusObjectFields: List[(String, Json)] = aCursor.focus.flatMap(_.asObject).map(_.toList).getOrElse(List.empty)
  }

}

package pl.touk.nussknacker.ui.api

import com.networknt.schema.{InputFormat, InvalidSchemaRefException, JsonSchemaFactory, SpecVersion}
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.scalactic.anyvals.NonEmptyList
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import sttp.apispec.{ExampleMultipleValue, ExampleSingleValue, Schema}
import sttp.apispec.openapi.{MediaType, OpenAPI, Operation}
import sttp.apispec.openapi.circe._

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

object OpenAPIExamplesValidator {

  def validateExamples(spec: OpenAPI): List[InvalidExample] = {
    val componentsSchemas = spec.components.map(_.schemas.mapValuesNow(_.asJson)).getOrElse(Map.empty)
    for {
      (_, pathItem) <- spec.paths.pathItems.toList
      operation <- List(
        pathItem.get,
        pathItem.put,
        pathItem.post,
        pathItem.delete,
        pathItem.options,
        pathItem.patch,
        pathItem.trace
      ).flatten
      invalidExample <- validateRequestExample(operation, componentsSchemas) ::: validateResponsesExamples(
        operation,
        componentsSchemas
      )
    } yield invalidExample
  }

  private def validateRequestExample(operation: Operation, componentsSchemas: Map[String, Json]): List[InvalidExample] =
    for {
      refOrRequestBody <- operation.requestBody.toList
      requestBody      <- refOrRequestBody.toOption.toList
      (_, mediaType)   <- requestBody.content
      exampleJson      <- validateMediaTypeExamples(mediaType, operation, isRequest = true, componentsSchemas)
    } yield exampleJson

  private def validateResponsesExamples(
      operation: Operation,
      componentsSchemas: Map[String, Json]
  ): List[InvalidExample] =
    for {
      (_, refOrResponse) <- operation.responses.responses.toList
      response           <- refOrResponse.toOption.toList
      (_, mediaType)     <- response.content
      exampleJson        <- validateMediaTypeExamples(mediaType, operation, isRequest = false, componentsSchemas)
    } yield exampleJson

  private def validateMediaTypeExamples(
      mediaType: MediaType,
      operation: Operation,
      isRequest: Boolean,
      componentsSchemas: Map[String, Json]
  ): List[InvalidExample] = {
    for {
      schema <- mediaType.schema.toList
      resolvedSchema <- schema match {
        case jsonSchema: Schema =>
          List(
            removeNullsFromJson(
              resolveSchemaReferences(jsonSchema.asJson, componentsSchemas)
            )
          )
        case _ => Nil
      }
      jsonSchema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(resolvedSchema.spaces2)
      (exampleId, refOrExample) <- mediaType.examples.toList
      example                   <- refOrExample.toOption.toList
      exampleValue              <- example.value.toList
      singleExampleValue <- exampleValue match {
        case ExampleSingleValue(value)    => List(value)
        case ExampleMultipleValue(values) => values
      }
      singleExampleValueString = singleExampleValue match {
        case str: String => str
        case other       => throw new IllegalStateException(s"Not supported type of example value: ${other.getClass}")
      }
      // validate works only with objects?
      if singleExampleValueString.startsWith("{")
      invalidJson <- NonEmptyList
        .from(jsonSchema.validate(singleExampleValueString, InputFormat.JSON).asScala.toList)
        .map { errors =>
          InvalidExample(singleExampleValueString, resolvedSchema, operation.operationId, isRequest, exampleId, errors)
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

  private def removeNullsFromJson(json: Json): Json = {
    json.fold(
      jsonNull = json,
      jsonBoolean = Json.fromBoolean,
      jsonNumber = Json.fromJsonNumber,
      jsonString = Json.fromString,
      jsonArray = arr => Json.fromValues(arr.map(removeNullsFromJson)),
      jsonObject = obj =>
        Json.fromJsonObject(
          obj
            .filter { case (_, value) =>
              !value.isNull
            }
            .mapValues(removeNullsFromJson)
        )
    )
  }

}

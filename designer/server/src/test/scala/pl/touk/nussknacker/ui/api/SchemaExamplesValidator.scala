package pl.touk.nussknacker.ui.api

import com.networknt.schema.{InputFormat, InvalidSchemaRefException, JsonSchemaFactory, SpecVersion}
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.scalactic.anyvals.NonEmptyList
import sttp.apispec.{ExampleMultipleValue, ExampleSingleValue, Schema}
import sttp.apispec.openapi.{MediaType, OpenAPI, Operation}
import sttp.apispec.openapi.circe._
import scala.jdk.CollectionConverters._

object SchemaExamplesValidator {

  def validateSchemaExamples(spec: OpenAPI): List[InvalidExample] = {
    val componentsSchemas = spec.components.map(_.schemas).getOrElse(Map.empty).map { case (key, schemaLike) =>
      s"#/components/schemas/$key" -> schemaLike.asJson
    }
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
  ): List[InvalidExample] =
    for {
      schema <- mediaType.schema.toList
      jsonSchema <- {
        schema match {
          case jsonSchema: Schema =>
            val resolvedSchema = removeNullsFromJson(
              resolveSchemaReferences(jsonSchema.asJson, componentsSchemas, Set.empty)
            )
            val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
            val schema        = schemaFactory.getSchema(resolvedSchema.spaces2)
            List(schema)
          case _ => Nil
        }
      }
      (_, refOrExample) <- mediaType.examples.toList
      example           <- refOrExample.toOption.toList
      exampleValue      <- example.value.toList
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
      invalidJson <- {
        try {
          NonEmptyList
            .from(jsonSchema.validate(singleExampleValueString, InputFormat.JSON).asScala.toList)
            .map { errors =>
              InvalidExample(singleExampleValueString, operation.operationId, isRequest, errors)
            }
            .toList
        } catch {
          // FIXME: recursive schemas
          case _: InvalidSchemaRefException => List.empty
        }
      }
    } yield invalidJson

  private def resolveSchemaReferences(
      schema: Json,
      components: Map[String, Json],
      alreadyResolved: Set[String]
  ): Json = {
    (for {
      ref <- schema.hcursor.get[String]("$ref").toOption
      // FIXME: recursive schemas
      if !alreadyResolved.contains(ref)
      resolvedSchema <- components.get(ref)
    } yield {
      resolveSchemaReferences(resolvedSchema, components, alreadyResolved + ref)
    }).getOrElse {
      schema.fold(
        jsonNull = schema,
        jsonBoolean = Json.fromBoolean,
        jsonNumber = Json.fromJsonNumber,
        jsonString = Json.fromString,
        jsonArray = arr => Json.fromValues(arr.map(resolveSchemaReferences(_, components, alreadyResolved))),
        jsonObject = obj =>
          Json.fromFields(obj.toList.map { case (key, value) =>
            key -> resolveSchemaReferences(value, components, alreadyResolved)
          })
      )
    }
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

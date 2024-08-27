package pl.touk.nussknacker.engine.requestresponse.openapi

import io.circe.Json
import io.circe.syntax._
import pl.touk.nussknacker.engine.util.json.ToJsonEncoder

class RequestResponseOpenApiGenerator(oApiVersion: String, oApiInfo: OApiInfo) {

  def generateOpenApiDefinition(
      rootPathDefinitionGenerator: PathOpenApiDefinitionGenerator,
      serverDescriptions: List[OApiServer],
      defaultServerUrl: String
  ): Json = {
    generateOpenApiDefinition(
      List("/" -> rootPathDefinitionGenerator),
      Option(serverDescriptions).filter(_.nonEmpty).orElse(Some(List(OApiServer(defaultServerUrl, None))))
    )
  }

  def generateOpenApiDefinition(
      pathWithDefinitionGenerators: List[(String, PathOpenApiDefinitionGenerator)],
      serverDescriptions: Option[List[OApiServer]]
  ): Json = {
    val paths = generatePathsDefinition(pathWithDefinitionGenerators)
    OApiDocumentation(oApiVersion, oApiInfo, serverDescriptions, paths).asJson
  }

  private def generatePathsDefinition(
      pathWithDefinitionGenerators: List[(String, PathOpenApiDefinitionGenerator)]
  ): Json = {
    (for {
      pathWithDefinitionGenerator <- pathWithDefinitionGenerators
      (path, pathDefinitionGenerator) = pathWithDefinitionGenerator
      pathDefinition <- pathDefinitionGenerator.generatePathOpenApiDefinitionPart()
    } yield path -> pathDefinition).toMap.asJson
  }

}

object RequestResponseOpenApiGenerator {

  private val jsonEncoder = ToJsonEncoder(failOnUnknown = true, getClass.getClassLoader)

  private[requestresponse] def generateScenarioDefinition(
      operationId: String,
      summary: String,
      description: String,
      tags: List[String],
      requestDefinition: Json,
      responseDefinition: Json
  ): Json = {
    val postOpenApiDefinition =
      generatePostJsonOApiPathDefinition(operationId, summary, description, tags, requestDefinition, responseDefinition)
    val openApiDefinition = generateOApiDefinition(
      postOpenApiDefinition,
      Map(), // TODO generate openApi for GET sources
    )
    jsonEncoder.encode(openApiDefinition)
  }

  private def generateOApiDefinition(
      postOpenApiDefinition: Map[String, Any],
      getOpenApiDefinition: Map[String, Any]
  ) = {
    Map.empty[String, Any] ++
      (if (postOpenApiDefinition.isEmpty) Map.empty else Map("post" -> postOpenApiDefinition)) ++
      (if (getOpenApiDefinition.isEmpty) Map.empty else Map("get" -> getOpenApiDefinition))
  }

  private def generatePostJsonOApiPathDefinition(
      operationId: String,
      summary: String,
      description: String,
      tags: List[String],
      requestSchema: Json,
      responseSchema: Json
  ) =
    Map(
      "operationId" -> operationId,
      "summary"     -> summary,
      "description" -> description,
      "tags"        -> tags,
      "consumes"    -> List("application/json"),
      "produces"    -> List("application/json"),
      "requestBody" -> generateOApiRequestBody(requestSchema),
      "responses"   -> generateOApiResponse(responseSchema)
    )

  private def generateOApiRequestBody(schema: Json) = Map(
    "required" -> true,
    "content" -> Map(
      "application/json" -> Map(
        "schema" -> schema
      )
    )
  )

  private def generateOApiResponse(schema: Json) = Map(
    "200" -> Map(
      "content" -> Map(
        "application/json" -> Map(
          "schema" -> schema
        )
      )
    )
  )

}

trait PathOpenApiDefinitionGenerator {

  def generatePathOpenApiDefinitionPart(): Option[Json]

}

package pl.touk.nussknacker.engine.requestresponse.openapi

import io.circe.Json
import io.circe.syntax._
import pl.touk.nussknacker.engine.api.json.encoders.ToJsonEncoder
import pl.touk.nussknacker.engine.requestresponse.RequestResponseSecurityConfig
import pl.touk.nussknacker.engine.requestresponse.openapi.RequestResponseOpenApiGenerator.generateComponentsDefinition

class RequestResponseOpenApiGenerator(oApiVersion: String, oApiInfo: OApiInfo) {

  def generateOpenApiDefinition(
      rootPathDefinitionGenerator: PathOpenApiDefinitionGenerator,
      serverDescriptions: List[OApiServer],
      defaultServerUrl: String,
      securityConfig: Option[RequestResponseSecurityConfig]
  ): Json = {
    generateOpenApiDefinition(
      List("/" -> rootPathDefinitionGenerator),
      Option(serverDescriptions).filter(_.nonEmpty).orElse(Some(List(OApiServer(defaultServerUrl, None)))),
      securityConfig
    )
  }

  def generateOpenApiDefinition(
      pathWithDefinitionGenerators: List[(String, PathOpenApiDefinitionGenerator)],
      serverDescriptions: Option[List[OApiServer]],
      securityConfig: Option[RequestResponseSecurityConfig]
  ): Json = {
    val paths      = generatePathsDefinition(pathWithDefinitionGenerators, securityConfig)
    val components = generateComponentsDefinition(securityConfig)
    OApiDocumentation(
      openapi = oApiVersion,
      info = oApiInfo,
      servers = serverDescriptions,
      paths = paths,
      components = components
    ).asJson
  }

  private def generatePathsDefinition(
      pathWithDefinitionGenerators: List[(String, PathOpenApiDefinitionGenerator)],
      securityConfig: Option[RequestResponseSecurityConfig]
  ): Json = {
    (for {
      pathWithDefinitionGenerator <- pathWithDefinitionGenerators
      (path, pathDefinitionGenerator) = pathWithDefinitionGenerator
      pathDefinition <- pathDefinitionGenerator.generatePathOpenApiDefinitionPart()
    } yield path -> pathDefinition).toMap.asJson
  }

}

object RequestResponseOpenApiGenerator {

  private val BasicAuthSecuritySchemeName = "basicAuthScheme"

  private[requestresponse] def generateScenarioDefinition(
      operationId: String,
      summary: String,
      description: String,
      tags: List[String],
      requestDefinition: Json,
      responseDefinition: Json,
      securityConfig: Option[RequestResponseSecurityConfig]
  ): Json = {
    val postOpenApiDefinition =
      generatePostJsonOApiPathDefinition(
        operationId,
        summary,
        description,
        tags,
        requestDefinition,
        responseDefinition,
        securityConfig
      )
    val openApiDefinition = generateOApiDefinition(
      postOpenApiDefinition,
      Map(), // TODO generate openApi for GET sources
    )
    ToJsonEncoder.default.encodeUnsafe(openApiDefinition)
  }

  private def generateComponentsDefinition(
      securityConfig: Option[RequestResponseSecurityConfig]
  ): Option[Json] =
    securityConfig.collect { case RequestResponseSecurityConfig(Some(_)) =>
      Json.obj(
        "securitySchemes" -> Json.obj(
          BasicAuthSecuritySchemeName -> Json.obj(
            "type"   -> Json.fromString("http"),
            "scheme" -> Json.fromString("basic")
          )
        )
      )
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
      responseSchema: Json,
      securityConfig: Option[RequestResponseSecurityConfig]
  ) = {
    val baseMap = Map(
      "operationId" -> operationId,
      "summary"     -> summary,
      "description" -> description,
      "tags"        -> tags,
      "consumes"    -> List("application/json"),
      "produces"    -> List("application/json"),
      "requestBody" -> generateOApiRequestBody(requestSchema),
      "responses"   -> generateOApiResponse(responseSchema)
    )
    val securityMap = generateSecurityConfigForPath(securityConfig)
    baseMap ++ securityMap
  }

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

  private def generateSecurityConfigForPath(securityConfig: Option[RequestResponseSecurityConfig]): Map[String, Any] =
    securityConfig match {
      case Some(RequestResponseSecurityConfig(basicAuth)) =>
        Map(
          "security" -> List(
            Map(BasicAuthSecuritySchemeName -> List())
          )
        )
      case _ => Map.empty
    }

}

trait PathOpenApiDefinitionGenerator {

  def generatePathOpenApiDefinitionPart(): Option[Json]

}

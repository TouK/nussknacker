package pl.touk.nussknacker.engine.requestresponse.openapi

import io.circe.Json
import io.circe.syntax._
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreter.RequestResponseScenarioInterpreter
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings.OPEN_API_VERSION
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

import scala.language.higherKinds

object RequestResponseOpenApiGenerator {

  private val jsonEncoder = BestEffortJsonEncoder(failOnUnkown = true, getClass.getClassLoader)

  def generateOpenApi[Effect[_]](pathWithInterpreter: List[(String, RequestResponseScenarioInterpreter[Effect])], oApiInfo: OApiInfo, serversDescription: Option[OApiServer]): String = {
    val scenarioDefinitions: Json = generateScenarioDefinitions(pathWithInterpreter)
    OApiDocumentation(OPEN_API_VERSION, oApiInfo, serversDescription.map(List(_)), scenarioDefinitions).asJson.spaces2
  }

  private def generateScenarioDefinitions[Effect[_]](pathWithInterpreter: List[(String, RequestResponseScenarioInterpreter[Effect])]): Json = {
    pathWithInterpreter
      .flatMap(a => a._2.generateOpenApiDefinition().map(oApi => a._1 -> oApi)).toMap.asJson
  }

  private[requestresponse] def generateScenarioDefinition(processName: String,
                                                          requestDefinition: Json,
                                                          responseDefinition: Json,
                                                          description: String,
                                                          tags: List[String]
                                                         ): Json = {
    val postOpenApiDefinition = generatePostOApiDefinition(
      tags,
      processName,
      description,
      requestDefinition,
      responseDefinition
    )
    val openApiDefinition = generateOApiDefinition(
      postOpenApiDefinition,
      Map(), //TODO generate openApi for GET sources
    )
    jsonEncoder.encode(openApiDefinition)
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

  private def generateOApiDefinition(postOpenApiDefinition: Map[String, Any], getOpenApiDefinition: Map[String, Any]) = {
    Map.empty[String, Any] ++
      (if(postOpenApiDefinition.isEmpty) Map.empty else Map("post" -> postOpenApiDefinition)) ++
      (if(getOpenApiDefinition.isEmpty) Map.empty else Map("get" -> getOpenApiDefinition))
  }

  private def generatePostOApiDefinition(tags: List[String], processName: String, description: String, requestSchema: Json, responseSchema: Json) =
    Map(
      "tags" -> tags,
      "summary" -> processName,
      "description" -> description,
      "consumes" -> List("application/json"),
      "produces" -> List("application/json"),
      "requestBody" -> generateOApiRequestBody(requestSchema),
      "responses" -> generateOApiResponse(responseSchema)
    )

}

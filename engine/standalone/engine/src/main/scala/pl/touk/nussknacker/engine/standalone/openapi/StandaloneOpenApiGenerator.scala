package pl.touk.nussknacker.engine.standalone.openapi

import io.circe.Json
import io.circe.syntax._
import io.circe.generic.auto._
import pl.touk.nussknacker.engine.standalone.StandaloneProcessInterpreter
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

object StandaloneOpenApiGenerator {

  private val OPEN_API_VERSION = "3.0.0"

  private val jsonEncoder = BestEffortJsonEncoder(failOnUnkown = true, getClass.getClassLoader)

  private[standalone] def generateScenarioDefinition(processName: String,
                                                     requestDefinition: Json,
                                                     responseDefinition: Json,
                                                     description: Map[String, String],
                                                     tags: List[String]
                                                    ): Json = {
   val postOpenApiDefinition = generatePostOApiDefinition(
      tags,
      processName,
      preetyPrintMap(description),
      requestDefinition,
      responseDefinition
    )
    jsonEncoder.encode(postOpenApiDefinition)
  }

  def generateOpenApi(pathWithInterpreter: List[(String, StandaloneProcessInterpreter)], oApiInfo: OApiInfo, serverDescription: OApiServer): String = {
    val scenarioDefinitions: Map[String, Json] = pathWithInterpreter
      .flatMap(a => a._2.produceOpenApiDefinition().map(oApi => a._1 -> oApi))
      .map {
        case (path, interpreter) => "/" + path -> interpreter
      }.toMap
    OApiDocumentation(OPEN_API_VERSION, oApiInfo, List(serverDescription), scenarioDefinitions.asJson).asJson.spaces2
  }

  private def preetyPrintMap(m: Map[String, String]): String = m.map(v => s"**${v._1}**: ${v._2}").mkString("\\\n")

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
          "schema" -> Map(
            "type" -> "object",
            "properties" -> schema
          )
        )
      )
    )
  )

  private def generatePostOApiDefinition(tags: List[String], processName: String, description: String, requestSchema: Json, responseSchema: Json) = Map(
    "post" -> Map(
      "tags" -> tags,
      "summary" -> processName,
      "description" -> description,
      "consumes" -> List("application/json"),
      "produces" -> List("application/json"),
      "requestBody" -> generateOApiRequestBody(requestSchema),
      "responses" -> generateOApiResponse(responseSchema) //TODO
    )
  )

}

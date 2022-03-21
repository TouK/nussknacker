package pl.touk.nussknacker.engine.requestresponse.http


import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings.{InputSchemaProperty, OutputSchemaProperty}
import pl.touk.nussknacker.engine.spel
import io.circe.syntax._

class RequestResponseHttpJsonSchemaSpec extends RequestResponseHttpTest {
  import spel.Implicits._

  private implicit final val plainString: FromEntityUnmarshaller[String] =
    Unmarshaller.stringUnmarshaller.forContentTypes(`application/json`)

  def jsonSchemaProcess(outputValue: String): CanonicalProcess = {
    val inputSchema = """{"properties": {"field1": {"type": "string"}, "field2": {"type": "string"} } }"""
    val outputSchema = """{"properties": {"output": {"type": "string"}}}"""

    ScenarioBuilder
      .requestResponse(procId.value)
      .additionalFields(properties = Map(InputSchemaProperty -> inputSchema, OutputSchemaProperty -> outputSchema))
      .source("start", "jsonSchemaRequest")
      .emptySink("endNodeIID", "jsonSchemaResponse", "Value" -> outputValue)
      .toCanonicalProcess
  }

  it should "Should validate input and output schema and return string concatination" in {
    assertProcessNotRunning(procId)

    Post("/deploy", toEntity(deploymentData(
      jsonSchemaProcess("""{"output": #input.field1+#input.field2}""")
    ))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/${procId.value}", toEntity(Map("field1" -> "Hello ", "field2" -> "NU"))) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe """{"output":"Hello NU"}"""
        cancelProcess(procId)
      }
    }
  }

  it should "Should return schema error on bad input" in {
    assertProcessNotRunning(procId)

    Post("/deploy", toEntity(deploymentData(
      jsonSchemaProcess("""{"output": #input.field1+#input.field2}""")
    ))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/${procId.value}", toEntity(Map("field1" -> 123.asJson, "field2" -> "NU".asJson))) ~> processesRoute ~> check {
        status shouldBe StatusCodes.InternalServerError
        responseAs[String] shouldBe """ [{"nodeId":null,"message":"#/field1: expected type: String, found: Integer"}] """.strip()
        cancelProcess(procId)
      }
    }
  }

  //TODO more tests

}


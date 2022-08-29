package pl.touk.nussknacker.engine.requestresponse.http


import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings.{InputSchemaProperty, OutputSchemaProperty}
import pl.touk.nussknacker.engine.spel.Implicits._
import io.circe.syntax._
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.JsonRequestResponseSink.{SinkRawEditorParamName, SinkRawValueParamName}

class RequestResponseHttpJsonSchemaSpec extends RequestResponseHttpTest {

  private implicit final val plainString: FromEntityUnmarshaller[String] =
    Unmarshaller.stringUnmarshaller.forContentTypes(`application/json`)

  def jsonSchemaProcessWithEditor(inputSchema: String, outputSchema: String, outputValueParams: (String, Expression)*): CanonicalProcess = {
    ScenarioBuilder
      .requestResponse(procId.value)
      .additionalFields(properties = Map(InputSchemaProperty -> inputSchema, OutputSchemaProperty -> outputSchema))
      .source("start", "request")
      .emptySink("endNodeIID", "response", outputValueParams:_*)
      .toCanonicalProcess
  }

  def jsonSchemaProcess(inputSchema: String, outputSchema: String, outputValue: String): CanonicalProcess = {
    ScenarioBuilder
      .requestResponse(procId.value)
      .additionalFields(properties = Map(InputSchemaProperty -> inputSchema, OutputSchemaProperty -> outputSchema))
      .source("start", "request")
      .emptySink("endNodeIID", "response", SinkRawValueParamName -> outputValue, SinkRawEditorParamName -> "true")
      .toCanonicalProcess
  }

  def jsonSchemaSampleProcess(outputValue: String): CanonicalProcess = {
    val inputSchema = """{"type":"object","properties": {"field1": {"type": "string"}, "field2": {"type": "string"} } }"""
    val outputSchema = """{"type":"object","properties": {"output": {"type": "string"}}}"""
    jsonSchemaProcess(inputSchema, outputSchema, outputValue)
  }

  it should "should validate input and output schema and return string concatenation" in {
    assertProcessNotRunning(procId)

    Post("/deploy", toEntity(deploymentData(
      jsonSchemaSampleProcess("""{"output": #input.field1+#input.field2}""")
    ))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/${procId.value}", toEntity(Map("field1" -> "Hello ", "field2" -> "NU"))) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe """{"output":"Hello NU"}"""
        cancelProcess(procId)
      }
    }
  }

  it should "should return schema error on bad input" in {
    assertProcessNotRunning(procId)

    Post("/deploy", toEntity(deploymentData(
      jsonSchemaSampleProcess("""{"output": #input.field1+#input.field2}""")
    ))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/${procId.value}", toEntity(Map("field1" -> 123.asJson, "field2" -> "NU".asJson))) ~> processesRoute ~> check {
        status shouldBe StatusCodes.InternalServerError
        responseAs[String] shouldBe """ [{"nodeId":null,"message":"#/field1: expected type: String, found: Integer"}] """.strip()
        cancelProcess(procId)
      }
    }
  }

  it should "should not be able to deplot process with output not matching outputSchema" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(deploymentData(
      jsonSchemaSampleProcess("""{"output": 123L}""")
    ))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  it should "should handle simple type on outputSchema " in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(deploymentData(
      jsonSchemaProcess(
        inputSchema = """{"type":"object","properties": {"field1": {"type": "integer"}}}""",
        outputSchema = """{"type": "string"}""",
        outputValue = """ #input.field1+" Dalmatians" """.strip()
      )
    ))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/${procId.value}", toEntity(Map("field1" -> 101))) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe """ "101 Dalmatians" """.strip()
        cancelProcess(procId)
      }
    }
  }

  it should "should fill empty request with default values" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(deploymentData(
      jsonSchemaProcess(
        inputSchema = """{"type":"object","properties": {"field1": {"type": "integer", "default": 101}}}""",
        outputSchema = """{"type": "string"}""",
        outputValue = """ #input.field1+" Dalmatians" """.strip()
      )
    ))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/${procId.value}", toEntity(Map[String, String]())) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe """ "101 Dalmatians" """.strip()
        cancelProcess(procId)
      }
    }
  }

  it should "should generate sink field based on output schema " in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(deploymentData(
      jsonSchemaProcessWithEditor(
        inputSchema = """{"type":"object","properties": {"field1": {"type": "integer"}}}""",
        outputSchema =
          """{"type":"object","properties": {
            |"name": {"type": "string"},
            |"age": {"type": "integer"},
            |"address": {"type": "object", "properties": {"street": {"type": "string"}}}
            |}}""".stripMargin,
        outputValueParams = "name"-> "'John Casey'", "age" -> "36", "address.street" -> "'Sesame'", SinkRawEditorParamName -> "false"
      )
    ))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/${procId.value}", toEntity(Map[String, String]())) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe """{"address":{"street":"Sesame"},"name":"John Casey","age":36}"""
        cancelProcess(procId)
      }
    }
  }

}


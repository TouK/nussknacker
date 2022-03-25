package pl.touk.nussknacker.engine.requestresponse.http

import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.MethodRejection
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import io.circe._
import io.circe.parser._
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.sinks.JsonRequestResponseSinkFactory.SinkValueParamName
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings.OutputSchemaProperty
import pl.touk.nussknacker.engine.spel

class RequestResponseHttpAppSpec extends RequestResponseHttpTest {
  import spel.Implicits._

  private implicit final val plainString: FromEntityUnmarshaller[String] =
    Unmarshaller.stringUnmarshaller.forContentTypes(`application/json`)

  private val outputSchema = s"""{"properties": {
      |$SinkValueParamName: {"type": "string"}
      |}}""".stripMargin

  def canonicalProcess = ScenarioBuilder
    .requestResponse(procId.value)
    .additionalFields(properties = Map(OutputSchemaProperty -> outputSchema))
    .source("start", "request1-post-source")
    .filter("filter1", "#input.field1() == 'a'")
    .emptySink("endNodeIID", "response", SinkValueParamName -> "#input.field2")
    .toCanonicalProcess


  def processWithGet = ScenarioBuilder
    .requestResponse(procId.value)
    .additionalFields(properties = Map(OutputSchemaProperty -> outputSchema))
    .source("start", "request1-get-source")
    .filter("filter1", "#input.field1() == 'a'")
    .emptySink("endNodeIID", "response", SinkValueParamName -> "#input.field2")
    .toCanonicalProcess

  def processWithGenericGet = ScenarioBuilder
    .requestResponse(procId.value)
    .additionalFields(properties = Map(OutputSchemaProperty -> outputSchema))
    .source("start", "genericGetSource", "type" -> "{field1: 'java.lang.String', field2: 'java.lang.String'}")
    .filter("filter1", "#input.field1 == 'a'")
    .emptySink("endNodeIID", "response", SinkValueParamName -> "#input.field2 + '-' + #input.field1")
    .toCanonicalProcess

  def processWithPathJson = ScenarioBuilder
    .requestResponse(procId.value)
      .path(Some("customPath1"))
    .additionalFields(properties = Map(OutputSchemaProperty -> outputSchema))
    .source("start", "request1-post-source")
    .filter("filter1", "#input.field1() == 'a'")
    .emptySink("endNodeIID", "response", SinkValueParamName -> "#input.field2")
    .toCanonicalProcess

  def processWithLifecycleService = ScenarioBuilder
    .requestResponse(procId.value)
      .path(Some("customPath1"))
    .additionalFields(properties = Map(OutputSchemaProperty -> outputSchema))
    .source("start", "request1-post-source")
    .processor("service", "lifecycleService")
    .emptySink("endNodeIID", "response", SinkValueParamName -> "#input.field2")
    .toCanonicalProcess

  def noFilterProcess = ScenarioBuilder
    .requestResponse(procId.value)
    .additionalFields(properties = Map(OutputSchemaProperty -> outputSchema))
    .source("start", "request1-post-source")
    .emptySink("endNodeIID", "response", SinkValueParamName -> "#input.field2")
    .toCanonicalProcess

  def invalidProcess = ScenarioBuilder
    .requestResponse(procId.value)
    .additionalFields(properties = Map(OutputSchemaProperty -> outputSchema))
    .source("start", "request1-post-source")
    .emptySink("endNodeIID", "response", SinkValueParamName -> "#var1")
    .toCanonicalProcess

  def failingProcess = ScenarioBuilder
    .requestResponse(procId.value)
    .additionalFields(properties = Map(OutputSchemaProperty -> outputSchema))
    .source("start", "request1-post-source")
    .filter("filter1", "1/#input.field1.length() > 0")
    .emptySink("endNodeIID", "response", SinkValueParamName -> "''")
    .toCanonicalProcess

  it should "deploy process and then run it" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(deploymentData(canonicalProcess))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Get(s"/checkStatus/${procId.value}") ~> managementRoute ~> check {
        status shouldBe StatusCodes.OK

        val docs = parse(responseAs[String]) match {
          case Right(json) => json
          case Left(error) => throw new AssertionError(error)
        }

        val cursorState = docs.hcursor

        cursorState.downField("processVersion").downField("versionId").focus shouldBe Some(Json.fromInt(1))
        cursorState.downField("deploymentTime").focus shouldBe Some(Json.fromBigDecimal(testEpoch))

      }

      Post(s"/${procId.value}", toEntity(Request("a", "b"))) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe s"""[{"$SinkValueParamName":"b"}]"""
        cancelProcess(procId)
      }
    }
  }

  it should "deploy process under specific path and then run it" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(deploymentData(processWithPathJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/customPath1", toEntity(Request("a", "b"))) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe s"""[{"$SinkValueParamName":"b"}]"""
        cancelProcess(procId)
      }
    }
  }

  it should "be able to invoke with GET for GET source" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(deploymentData(processWithGet))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Get(s"/${procId.value}?field1=a&field2=b") ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe s"""{"inputField1":"a","list":[{"$SinkValueParamName":"b"}]}"""
        cancelProcess(procId)
      }
    }
  }

  it should "not be able to invoke with GET for POST source" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(deploymentData(canonicalProcess))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Get(s"/${procId.value}?field1=a&field2=b") ~> processesRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
        cancelProcess(procId)
      }
    }
  }

  it should "not be able to invoke with POST for GET source" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(deploymentData(processWithGet))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/${procId.value}", toEntity(Request("a", "b"))) ~> processesRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.GET)
        cancelProcess(procId)
      }
    }
  }

  it should "not run not deployed process" in {
    assertProcessNotRunning(procId)
    Post("/proc1", toEntity(Request("a", "b"))) ~> processesRoute ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  it should "open and close used services" in {
    assertProcessNotRunning(procId)
    LifecycleService.reset()
    Post("/deploy", toEntity(deploymentData(processWithLifecycleService))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      LifecycleService.opened shouldBe true
      cancelProcess(procId)
      LifecycleService.closed shouldBe true
    }
  }

  it should "not open not used services" in {
    assertProcessNotRunning(procId)
    LifecycleService.reset()
    Post("/deploy", toEntity(deploymentData(processWithPathJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      LifecycleService.opened shouldBe false
      cancelProcess(procId)
      LifecycleService.closed shouldBe false
    }
  }

  it should "run process that produces empty response" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(deploymentData(canonicalProcess))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/${procId.value}", toEntity(Request("c", "d"))) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "[]"
        cancelProcess(procId)
      }
    }
  }

  it should "display error messages for process" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(deploymentData(failingProcess))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/${procId.value}", toEntity(Request("", "d"))) ~> processesRoute ~> check {
        status shouldBe StatusCodes.InternalServerError
        responseAs[String] shouldBe """[{"nodeId":"filter1","message":"Expression [1/#input.field1.length() > 0] evaluation failed, message: / by zero"}]"""
        cancelProcess(procId)
      }
    }
  }

  it should "not be able to cancel process that does not exists" in {
    assertProcessNotRunning(procId)
    Post("/cancel/proc1") ~> managementRoute ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  it should "redeploy process with different logic" in {
    assertProcessNotRunning(procId)
    val req = Request("c", "b")
    Post("/deploy", toEntity(deploymentData(canonicalProcess))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/${procId.value}", toEntity(req)) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "[]"
        Post("/deploy", toEntity(deploymentData(noFilterProcess))) ~> managementRoute ~> check {
          status shouldBe StatusCodes.OK
          Post(s"/${procId.value}", toEntity(req)) ~> processesRoute ~> check {
            responseAs[String] shouldBe s"""[{"$SinkValueParamName":"b"}]"""
            cancelProcess(procId)
          }
        }
      }
    }
  }

  it should "not be able to deploy invalid process" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(deploymentData(invalidProcess))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  it should "be able to invoke generic get" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(deploymentData(processWithGenericGet))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Get(s"/${procId.value}?field1=a&field2=b") ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe s"""[{"$SinkValueParamName":"b-a"}]"""
        cancelProcess(procId)
      }
    }
  }

  it should "return health" in {
    Get("/healthCheck") ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
    }
  }

}

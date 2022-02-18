package pl.touk.nussknacker.engine.requestresponse.http

import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.MethodRejection
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.{fromAnyRef, fromIterable}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.parser._
import io.circe.syntax._
import io.circe._
import io.dropwizard.metrics5.MetricRegistry
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.RequestResponseScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.requestresponse.deployment.RequestResponseDeploymentData
import pl.touk.nussknacker.engine.requestresponse.http.logging.RequestResponseLogger
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.ModelJarBuilder

import java.nio.file.Files
import java.util
import java.util.UUID

class RequestResponseHttpAppSpec extends FlatSpec with Matchers with ScalatestRouteTest with BeforeAndAfterEach with FailFastCirceSupport {

  import spel.Implicits._

  private implicit final val plainString: FromEntityUnmarshaller[String] =
    Unmarshaller.stringUnmarshaller.forContentTypes(`application/json`)

  var procId : ProcessName = _

  override protected def beforeEach(): Unit = {
    procId = ProcessName(UUID.randomUUID().toString)
  }

  private val testEpoch = (math.random * 10000).toLong

  private val schemaSimple = "'{\"properties\": {\"distance\": {\"type\": \"number\"}}}'"
  private val schemaDefaultValue = "'{\"properties\": {\"city\": {\"type\": \"string\", \"default\": \"Warsaw\"}}}'"

  private def deploymentData(canonicalProcess: CanonicalProcess) = RequestResponseDeploymentData(canonicalProcess, testEpoch,
    ProcessVersion.empty.copy(processName=procId), DeploymentData.empty)

  def canonicalProcess = RequestResponseScenarioBuilder
    .id(procId)
    .source("start", "request1-post-source")
    .filter("filter1", "#input.field1() == 'a'")
    .emptySink("endNodeIID", "response-sink", "value" -> "#input.field2")
    .toCanonicalProcess


  def processWithGet = RequestResponseScenarioBuilder
    .id(procId)
    .source("start", "request1-get-source")
    .filter("filter1", "#input.field1() == 'a'")
    .emptySink("endNodeIID", "response-sink", "value" -> "#input.field2")
    .toCanonicalProcess

  def processWithGenericGet = RequestResponseScenarioBuilder
    .id(procId)
    .source("start", "genericGetSource", "type" -> "{field1: 'java.lang.String', field2: 'java.lang.String'}")
    .filter("filter1", "#input.field1 == 'a'")
    .emptySink("endNodeIID", "response-sink", "value" -> "#input.field2 + '-' + #input.field1")
    .toCanonicalProcess

  def processWithJsonSchemaSource(schema: String) = RequestResponseScenarioBuilder
    .id(procId)
    .source("start", "jsonSchemaSource", "schema" -> schema)
    .emptySink("endNodeIID", "response-sink", "value" -> "#input")
    .toCanonicalProcess

  def processWithPathJson = RequestResponseScenarioBuilder
    .id(procId)
      .path(Some("customPath1"))
    .source("start", "request1-post-source")
    .filter("filter1", "#input.field1() == 'a'")
    .emptySink("endNodeIID", "response-sink", "value" -> "#input.field2")
    .toCanonicalProcess

  def processWithLifecycleService = RequestResponseScenarioBuilder
    .id(procId)
      .path(Some("customPath1"))
    .source("start", "request1-post-source")
    .processor("service", "lifecycleService")
    .emptySink("endNodeIID", "response-sink", "value" -> "#input.field2")
    .toCanonicalProcess

  def noFilterProcess = RequestResponseScenarioBuilder
    .id(procId)
    .source("start", "request1-post-source")
    .emptySink("endNodeIID", "response-sink", "value" -> "#input.field2")
    .toCanonicalProcess

  def invalidProcess = RequestResponseScenarioBuilder
    .id(procId)
    .source("start", "request1-post-source")
    .emptySink("endNodeIID", "response-sink", "value" -> "#var1")
    .toCanonicalProcess

  def failingProcess = RequestResponseScenarioBuilder
    .id(procId)
    .source("start", "request1-post-source")
    .filter("filter1", "1/#input.field1.length() > 0")
    .emptySink("endNodeIID", "response-sink", "value" -> "''")
    .toCanonicalProcess

  val config = ConfigFactory.load()
    .withValue("scenarioRepositoryLocation", fromAnyRef(Files.createTempDirectory("scenarioLocation")
      .toFile.getAbsolutePath))
    .withValue("modelConfig.classPath", fromIterable(
      util.Arrays.asList(
        ModelJarBuilder.buildJarWithConfigCreator[TestConfigCreator]().getAbsolutePath)))

  val exampleApp = new RequestResponseHttpApp(config, new MetricRegistry)

  val managementRoute = exampleApp.managementRoute.route
  val processesRoute = exampleApp.processRoute.route(RequestResponseLogger.default)

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
        responseAs[String] shouldBe "[\"b\"]"
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
        responseAs[String] shouldBe "[\"b\"]"
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
        responseAs[String] shouldBe "{\"inputField1\":\"a\",\"list\":[\"b\"]}"
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

  it should "be able to parse schema and POST request to jsonSchemaSource" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(deploymentData(processWithJsonSchemaSource(schemaSimple)))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/${procId.value}", stringAsJsonEntity(""" {"distance": 123.4} """)) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe """{"distance":123.4}"""
        cancelProcess(procId)
      }
    }
  }

  it should "be able to put default value on empty request in jsonSchemaSource" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(deploymentData(processWithJsonSchemaSource(schemaDefaultValue)))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/${procId.value}", stringAsJsonEntity("{}")) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe """{"city":"Warsaw"}"""
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
            responseAs[String] shouldBe "[\"b\"]"
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
        responseAs[String] shouldBe "[\"b-a\"]"
        cancelProcess(procId)
      }
    }
  }

  it should "return health" in {
    Get("/healthCheck") ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  def cancelProcess(processName: ProcessName) = {
    val id = processName.value

    Post(s"/cancel/$id") ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      assertProcessNotRunning(processName)
    }
  }

  def assertProcessNotRunning(processName: ProcessName) = {
    val id = processName.value
    Get(s"/checkStatus/$id") ~> managementRoute ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  def toEntity[J : Encoder](jsonable: J): RequestEntity = {
    val json = jsonable.asJson.spaces2
    HttpEntity(ContentTypes.`application/json`, json)
  }

  def stringAsJsonEntity(jsonMessage: String): RequestEntity = {
    HttpEntity(ContentTypes.`application/json`, jsonMessage)
  }

}

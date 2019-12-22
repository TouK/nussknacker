package pl.touk.nussknacker.engine.standalone.http

import java.nio.file.Files
import java.util
import java.util.UUID

import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.MethodRejection
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.{fromAnyRef, fromIterable}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Encoder
import io.circe.syntax._
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, ProcessState}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.StandaloneProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.standalone.api.DeploymentData
import pl.touk.nussknacker.engine.standalone.utils.logging.StandaloneRequestResponseLogger
import pl.touk.nussknacker.engine.testing.ModelJarBuilder

class StandaloneHttpAppSpec extends FlatSpec with Matchers with ScalatestRouteTest with BeforeAndAfterEach with FailFastCirceSupport {

  import spel.Implicits._

  private implicit final val plainString: FromEntityUnmarshaller[String] =
    Unmarshaller.stringUnmarshaller.forContentTypes(`application/json`)

  var procId : ProcessName = _

  override protected def beforeEach() = {
    procId = ProcessName(UUID.randomUUID().toString)
  }

  private val testEpoch = (math.random * 10000).toLong

  private def deploymentData(processJson: String) = DeploymentData(processJson, testEpoch, ProcessVersion.empty.copy(processName=procId))

  def processJson = processToJson(StandaloneProcessBuilder
    .id(procId)
    .exceptionHandler()
    .source("start", "request1-post-source")
    .filter("filter1", "#input.field1() == 'a'")
    .sink("endNodeIID", "#input.field2", "response-sink"))


  def processJsonWithGet = processToJson(StandaloneProcessBuilder
    .id(procId)
    .exceptionHandler()
    .source("start", "request1-get-source")
    .filter("filter1", "#input.field1() == 'a'")
    .sink("endNodeIID", "#input.field2", "response-sink"))

  def processWithGenericGet = processToJson(StandaloneProcessBuilder
    .id(procId)
    .exceptionHandler()
    .source("start", "genericGetSource", "type" -> "{field1: 'java.lang.String', field2: 'java.lang.String'}")
    .filter("filter1", "#input.field1 == 'a'")
    .sink("endNodeIID", "#input.field2 + '-' + #input.field1", "response-sink")
  )

  def processWithPathJson = processToJson(StandaloneProcessBuilder
    .id(procId)
      .path(Some("customPath1"))
    .exceptionHandler()
    .source("start", "request1-post-source")
    .filter("filter1", "#input.field1() == 'a'")
    .sink("endNodeIID", "#input.field2", "response-sink"))

  def noFilterProcessJson = processToJson(StandaloneProcessBuilder
    .id(procId)
    .exceptionHandler()
    .source("start", "request1-post-source")
    .sink("endNodeIID", "#input.field2", "response-sink"))

  def invalidProcessJson = processToJson(StandaloneProcessBuilder
    .id(procId)
    .exceptionHandler()
    .source("start", "request1-post-source")
    .sink("endNodeIID", "#var1", "response-sink"))

  def failingProcessJson = processToJson(StandaloneProcessBuilder
    .id(procId)
    .exceptionHandler()
    .source("start", "request1-post-source")
    .filter("filter1", "1/#input.field1.length() > 0")
    .sink("endNodeIID", "''", "response-sink"))



  def processToJson(espProcess: EspProcess): String = {
    val canonical = ProcessCanonizer.canonize(espProcess)
    ProcessMarshaller.toJson(canonical).spaces2
  }


  val config = ConfigFactory.load()
    .withValue("standaloneEngineProcessLocation", fromAnyRef(Files.createTempDirectory("standaloneLocation")
      .toFile.getAbsolutePath))
    .withValue("standaloneConfig.classpath", fromIterable(
      util.Arrays.asList(
        ModelJarBuilder.buildJarWithConfigCreator[TestConfigCreator]().getAbsolutePath)))


  val exampleApp = new StandaloneHttpApp(config, new MetricRegistry)

  val managementRoute = exampleApp.managementRoute.route
  val processesRoute = exampleApp.processRoute.route(StandaloneRequestResponseLogger.default)

  
  it should "deploy process and then run it" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(deploymentData(processJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Get(s"/checkStatus/${procId.value}") ~> managementRoute ~> check {
        status shouldBe StatusCodes.OK
        val processState = responseAs[ProcessState]
        processState.deploymentId shouldBe DeploymentId(procId.value)
        processState.startTime shouldBe Some(testEpoch)

        processState.status shouldBe "RUNNING"
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
    Post("/deploy", toEntity(deploymentData(processJsonWithGet))) ~> managementRoute ~> check {
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
    Post("/deploy", toEntity(deploymentData(processJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Get(s"/${procId.value}?field1=a&field2=b") ~> processesRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
        cancelProcess(procId)
      }
    }
  }


  it should "not be able to invoke with POST for GET source" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(deploymentData(processJsonWithGet))) ~> managementRoute ~> check {
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

  it should "open and close services" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(deploymentData(processWithPathJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      LifecycleService.opened shouldBe true
      cancelProcess(procId)
      LifecycleService.closed shouldBe true
    }

  }

  it should "run process that produces empty response" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(deploymentData(processJson))) ~> managementRoute ~> check {
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
    Post("/deploy", toEntity(deploymentData(failingProcessJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/${procId.value}", toEntity(Request("", "d"))) ~> processesRoute ~> check {
        status shouldBe StatusCodes.InternalServerError
        responseAs[String] shouldBe "[{\"nodeId\":\"filter1\",\"message\":\"/ by zero\"}]"
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
    Post("/deploy", toEntity(deploymentData(processJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/${procId.value}", toEntity(req)) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "[]"
        Post("/deploy", toEntity(deploymentData(noFilterProcessJson))) ~> managementRoute ~> check {
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
    Post("/deploy", toEntity(deploymentData(invalidProcessJson))) ~> managementRoute ~> check {
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




}

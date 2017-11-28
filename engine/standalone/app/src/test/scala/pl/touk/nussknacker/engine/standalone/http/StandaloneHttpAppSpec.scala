package pl.touk.nussknacker.engine.standalone.http

import java.nio.file.Files
import java.util
import java.util.UUID

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.MethodRejection
import akka.http.scaladsl.testkit.ScalatestRouteTest
import argonaut.Argonaut._
import argonaut.{DecodeJson, EncodeJson, PrettyParams}
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.{fromAnyRef, fromIterable}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.deployment.ProcessState
import pl.touk.nussknacker.engine.build.StandaloneProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.standalone.api.DeploymentData
import pl.touk.nussknacker.engine.testing.ModelJarBuilder

class StandaloneHttpAppSpec extends FlatSpec with Matchers with ScalatestRouteTest with BeforeAndAfterEach {

  import argonaut.ArgonautShapeless._
  import spel.Implicits._

  var procId : String = _

  override protected def beforeEach() = {
    procId = UUID.randomUUID().toString
  }

  val processMarshaller = new ProcessMarshaller

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
    .filter("filter1", "1/#input.field1.length()")
    .sink("endNodeIID", "''", "response-sink"))



  def processToJson(espProcess: EspProcess): String = {
    val canonical = ProcessCanonizer.canonize(espProcess)
    processMarshaller.toJson(canonical, PrettyParams.spaces2)
  }


  val config = ConfigFactory.load()
    .withValue("standaloneEngineProcessLocation", fromAnyRef(Files.createTempDirectory("standaloneLocation")
      .toFile.getAbsolutePath))
    .withValue("standaloneConfig.classpath", fromIterable(
      util.Arrays.asList(
        ModelJarBuilder.buildJarWithConfigCreator[TestConfigCreator]().getAbsolutePath)))


  val exampleApp = new StandaloneHttpApp(config)

  val managementRoute = exampleApp.managementRoute.route
  val processesRoute = exampleApp.processRoute.route

  
  it should "deploy process and then run it" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(DeploymentData(procId, processJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Get(s"/checkStatus/$procId") ~> managementRoute ~> check {
        status shouldBe StatusCodes.OK
        val processState = responseAs[String].decodeOption[ProcessState].get
        processState.id shouldBe procId
        processState.status shouldBe "RUNNING"
      }
      Post(s"/$procId", toEntity(Request("a", "b"))) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "[\"b\"]"
        cancelProcess(procId)
      }
    }
  }

  it should "deploy process under specific path and then run it" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(DeploymentData(procId, processWithPathJson))) ~> managementRoute ~> check {
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
    Post("/deploy", toEntity(DeploymentData(procId, processJsonWithGet))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Get(s"/$procId?field1=a&field2=b") ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "{\"inputField1\":\"a\",\"list\":[\"b\"]}"
        cancelProcess(procId)
      }
    }
  }

  it should "not be able to invoke with GET for POST source" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(DeploymentData(procId, processJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Get(s"/$procId?field1=a&field2=b") ~> processesRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
        cancelProcess(procId)
      }
    }
  }


  it should "not be able to invoke with POST for GET source" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(DeploymentData(procId, processJsonWithGet))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/$procId", toEntity(Request("a", "b"))) ~> processesRoute ~> check {
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
    Post("/deploy", toEntity(DeploymentData(procId, processWithPathJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      LifecycleService.opened shouldBe true
      cancelProcess(procId)
      LifecycleService.closed shouldBe true
    }

  }

  it should "run process that produces empty response" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(DeploymentData(procId, processJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/$procId", toEntity(Request("c", "d"))) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "[]"
        cancelProcess(procId)
      }
    }
  }

  it should "display error messages for process" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(DeploymentData(procId, failingProcessJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/$procId", toEntity(Request("", "d"))) ~> processesRoute ~> check {
        status shouldBe StatusCodes.InternalServerError
        responseAs[String] shouldBe "[{\"message\":\"/ by zero\",\"nodeId\":\"filter1\"}]"
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
    Post("/deploy", toEntity(DeploymentData(procId, processJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/$procId", toEntity(req)) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "[]"
        Post("/deploy", toEntity(DeploymentData(procId, noFilterProcessJson))) ~> managementRoute ~> check {
          status shouldBe StatusCodes.OK
          Post(s"/$procId", toEntity(req)) ~> processesRoute ~> check {
            responseAs[String] shouldBe "[\"b\"]"
            cancelProcess(procId)
          }
        }
      }
    }
  }

  it should "not be able to deploy invalid process" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(DeploymentData(procId, invalidProcessJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }       

  def cancelProcess(id: String) = {
    Post(s"/cancel/$id") ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      assertProcessNotRunning(id)
    }
  }

  implicit def processStateCode = DecodeJson.derive[ProcessState]

  def assertProcessNotRunning(id: String) = {
    Get(s"/checkStatus/$id") ~> managementRoute ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  import argonaut.Argonaut._
  import argonaut.ArgonautShapeless._

  def toEntity[J : EncodeJson](jsonable: J): RequestEntity = {
    val json = jsonable.asJson.spaces2
    HttpEntity(ContentTypes.`application/json`, json)
  }



}

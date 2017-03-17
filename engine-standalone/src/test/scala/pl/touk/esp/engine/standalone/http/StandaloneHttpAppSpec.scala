package pl.touk.esp.engine.standalone.http

import java.io.File
import java.nio.file.Files
import java.util.UUID

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import argonaut.{DecodeJson, EncodeJson, PrettyParams}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.esp.engine.api.deployment.{DeploymentData, ProcessState}
import pl.touk.esp.engine.build.{EspProcessBuilder, StandaloneProcessBuilder}
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.engine.spel
import pl.touk.esp.engine.standalone.Request1

class StandaloneHttpAppSpec extends FlatSpec with Matchers with ScalatestRouteTest with BeforeAndAfterEach {

  import argonaut.Argonaut._
  import argonaut.ArgonautShapeless._
  import spel.Implicits._

  var procId : String = _

  override protected def beforeEach() = {
    new File(ConfigFactory.load().getString("standaloneEngineProcessLocation")).listFiles().foreach(_.delete())
    procId = UUID.randomUUID().toString
  }

  val processMarshaller = new ProcessMarshaller

  def processJson = processToJson(StandaloneProcessBuilder
    .id(procId)
    .exceptionHandler()
    .source("start", "request1-source")
    .filter("filter1", "#input.field1() == 'a'")
    .enricher("enricher", "var1", "enricherService")
    .processor("processor", "processorService")
    .sink("endNodeIID", "#var1", "response-sink"))


  def processWithPathJson = processToJson(StandaloneProcessBuilder
    .id(procId)
      .path(Some("customPath1"))
    .exceptionHandler()
    .source("start", "request1-source")
    .filter("filter1", "#input.field1() == 'a'")
    .enricher("enricher", "var1", "enricherService")
    .processor("processor", "processorService")
    .sink("endNodeIID", "#var1", "response-sink"))

  def noFilterProcessJson = processToJson(StandaloneProcessBuilder
    .id(procId)
    .exceptionHandler()
    .source("start", "request1-source")
    .enricher("enricher", "var1", "enricherService")
    .processor("processor", "processorService")
    .sink("endNodeIID", "#var1", "response-sink"))

  def invalidProcessJson = processToJson(StandaloneProcessBuilder
    .id(procId)
    .exceptionHandler()
    .source("start", "request1-source")
    .sink("endNodeIID", "#var1", "response-sink"))


  def processToJson(espProcess: EspProcess): String = {
    val canonical = ProcessCanonizer.canonize(espProcess)
    processMarshaller.toJson(canonical, PrettyParams.spaces2)
  }

  
  val exampleApp = StandaloneHttpApp

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
      Post(s"/$procId", toEntity(Request1("a", "b"))) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "[{\"field1\":\"alamakota\"}]"
        cancelProcess(procId)
      }
    }
  }

  it should "deploy process under specific path and then run it" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(DeploymentData(procId, processWithPathJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/customPath1", toEntity(Request1("a", "b"))) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "[{\"field1\":\"alamakota\"}]"
        cancelProcess(procId)
      }
    }
  }
       
  it should "not run not deployed process" in {
    assertProcessNotRunning(procId)
    Post("/proc1", toEntity(Request1("a", "b"))) ~> processesRoute ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  it should "run process that produces empty response" in {
    assertProcessNotRunning(procId)
    Post("/deploy", toEntity(DeploymentData(procId, processJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/$procId", toEntity(Request1("c", "d"))) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "[]"
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
    val req = Request1("c", "d")
    Post("/deploy", toEntity(DeploymentData(procId, processJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post(s"/$procId", toEntity(req)) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "[]"
        Post("/deploy", toEntity(DeploymentData(procId, noFilterProcessJson))) ~> managementRoute ~> check {
          status shouldBe StatusCodes.OK
          Post(s"/$procId", toEntity(req)) ~> processesRoute ~> check {
            responseAs[String] shouldBe "[{\"field1\":\"alamakota\"}]"
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

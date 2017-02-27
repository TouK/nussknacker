package pl.touk.esp.engine.standalone.http

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import argonaut.{DecodeJson, EncodeJson, PrettyParams}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.esp.engine.api.deployment.{DeploymentData, ProcessState}
import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.engine.spel
import pl.touk.esp.engine.standalone.Request1

class StandaloneHttpAppSpec extends FlatSpec with Matchers with ScalatestRouteTest with BeforeAndAfterEach {

  import argonaut.Argonaut._
  import argonaut.ArgonautShapeless._
  import spel.Implicits._

  val process = EspProcessBuilder
    .id("proc1")
    .exceptionHandler()
    .source("start", "request1-source")
    .filter("filter1", "#input.field1() == 'a'")
    .enricher("enricher", "var1", "enricherService")
    .processor("processor", "processorService")
    .sink("endNodeIID", "#var1", "response-sink")

  val noFilterProcess = EspProcessBuilder
    .id("proc1")
    .exceptionHandler()
    .source("start", "request1-source")
    .enricher("enricher", "var1", "enricherService")
    .processor("processor", "processorService")
    .sink("endNodeIID", "#var1", "response-sink")

  val invalidProcess = EspProcessBuilder
    .id("proc1")
    .exceptionHandler()
    .source("start", "request1-source")
    .sink("endNodeIID", "#var1", "response-sink")

  val processMarshaller = new ProcessMarshaller
  val processJson = processToJson(process)
  val noFilterProcessJson = processToJson(noFilterProcess)
  val invalidProcessJson = processToJson(invalidProcess)

  def processToJson(espProcess: EspProcess): String = {
    val canonical = ProcessCanonizer.canonize(espProcess)
    processMarshaller.toJson(canonical, PrettyParams.spaces2)
  }

  val exampleApp = StandaloneHttpApp

  val managementRoute = exampleApp.managementRoute
  val processesRoute = exampleApp.processRoute

  it should "deploy process and then run it" in {
    assertProcessNotRunning("proc1")
    Post("/deploy", toEntity(DeploymentData("proc1", processJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Get("/checkStatus/proc1") ~> managementRoute ~> check {
        status shouldBe StatusCodes.OK
        val processState = responseAs[String].decodeOption[ProcessState].get
        processState.id shouldBe "proc1"
        processState.status shouldBe "RUNNING"
      }
      Post("/proc1", toEntity(Request1("a", "b"))) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "\"alamakota\"" //fixme ee po co te ciapki?
        cancelProcess("proc1")
      }
    }
  }

  it should "not run not deployed process" in {
    assertProcessNotRunning("proc1")
    Post("/proc1", toEntity(Request1("a", "b"))) ~> processesRoute ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  it should "run process that produces empty response" in {
    assertProcessNotRunning("proc1")
    Post("/deploy", toEntity(DeploymentData("proc1", processJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post("/proc1", toEntity(Request1("c", "d"))) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "" //fixme co wlasciwie powinnismy zwracac kiedy event nie przeszedl calego procesu?
        cancelProcess("proc1")
      }
    }
  }

  it should "not be able to cancel process that does not exists" in {
    assertProcessNotRunning("proc1")
    Post("/cancel/proc1") ~> managementRoute ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  it should "redeploy process with different logic" in {
    assertProcessNotRunning("proc1")
    val req = Request1("c", "d")
    Post("/deploy", toEntity(DeploymentData("proc1", processJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      Post("/proc1", toEntity(req)) ~> processesRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "" //fixme co wlasciwie powinnismy zwracac kiedy event nie przeszedl calego procesu?
        Post("/deploy", toEntity(DeploymentData("proc1", noFilterProcessJson))) ~> managementRoute ~> check {
          status shouldBe StatusCodes.OK
          Post("/proc1", toEntity(req)) ~> processesRoute ~> check {
            responseAs[String] shouldBe "\"alamakota\"" //fixme ee po co te ciapki?
            cancelProcess("proc1")
          }
        }
      }
    }
  }

  it should "not be able to deploy invalid process" in {
    assertProcessNotRunning("proc1")
    Post("/deploy", toEntity(DeploymentData("proc1", invalidProcessJson))) ~> managementRoute ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  def cancelProcess(id: String) = {
    Post(s"/cancel/${id}") ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      assertProcessNotRunning(id)
    }
  }

  implicit def processStateCode = DecodeJson.derive[ProcessState]

  def assertProcessNotRunning(id: String) = {
    Get(s"/checkStatus/${id}") ~> managementRoute ~> check {
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

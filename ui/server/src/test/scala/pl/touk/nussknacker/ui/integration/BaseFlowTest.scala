package pl.touk.nussknacker.ui.integration

import java.util.UUID

import argonaut.Argonaut._
import argonaut.ArgonautShapeless._
import akka.http.javadsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.ui.NussknackerApp
import pl.touk.nussknacker.ui.api.UISettings
import pl.touk.nussknacker.ui.api.helpers.{TestFactory, TestProcessUtil}
import pl.touk.nussknacker.ui.process.uiconfig.SingleNodeConfig
import pl.touk.nussknacker.ui.util.MultipartUtils

class BaseFlowTest extends FunSuite with ScalatestRouteTest
  with Matchers with ScalaFutures with BeforeAndAfterEach with BeforeAndAfterAll {

  private val mainRoute = NussknackerApp.initializeRoute()

  private val credentials = HttpCredentials.createBasicHttpCredentials("admin", "admin")

  test("saves, updates and retrieves sample process") {
    val processId = UUID.randomUUID().toString
    val endpoint = s"/api/processes/$processId"

    val process = EspProcessBuilder
      .id(processId)
      .exceptionHandler()
      .source("source", "csv-source").processorEnd("end", "monitor")

    saveProcess(endpoint, process)
  }

  test("initializes custom processes") {
    Get("/api/processes/customProcess1") ~> addCredentials(credentials) ~> mainRoute ~> check {
      status shouldEqual StatusCodes.OK
    }
  }

  test("ensure config is properly parsed") {
    Get("/api/settings") ~> addCredentials(credentials) ~> mainRoute ~> check {
      val settings = responseAs[String].decodeOption[UISettings].get
      settings.nodes shouldBe
        Map(
          "test1" -> SingleNodeConfig(None, Some("Sink.svg")),
          "enricher" -> SingleNodeConfig(Some(Map("param" -> "'default value'")), Some("Filter.svg"))
        )
    }
  }

  import spel.Implicits._

  test("should test process with complexReturnObjectService") {
    val processId = "complexObjectProcess" + UUID.randomUUID().toString
    val endpoint = s"/api/processes/$processId"

    val process = EspProcessBuilder
      .id(processId)
      .exceptionHandler("param1" -> "''")
      .source("source", "csv-source")
      .enricher("enricher", "out", "complexReturnObjectService")
      .sink("end", "#input", "sendSms")

    saveProcess(endpoint, process)

    val multiPart = MultipartUtils.prepareMultiParts("testData" -> "record1|field2", "processJson" -> TestProcessUtil.toJson(process).nospaces)()
    Post(s"/api/processManagement/test/${process.id}", multiPart) ~> addCredentials(credentials) ~> mainRoute ~> check {
      handled shouldBe true
    }
  }

  private def saveProcess(endpoint: String, process: EspProcess) = {
    Post(s"$endpoint/Category1?isSubprocess=false") ~> addCredentials(credentials) ~> mainRoute ~> check {
      status shouldEqual StatusCodes.Created
      Put(endpoint, TestFactory.posting.toEntityAsProcessToSave(process)) ~> addCredentials(credentials) ~> mainRoute ~> check {
        status shouldEqual StatusCodes.OK
        Get(endpoint) ~> addCredentials(credentials) ~> mainRoute ~> check {
          status shouldEqual StatusCodes.OK
        }
      }
    }
  }

}

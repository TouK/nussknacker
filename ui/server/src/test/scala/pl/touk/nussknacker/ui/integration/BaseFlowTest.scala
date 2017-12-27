package pl.touk.nussknacker.ui.integration

import java.nio.file.Files
import java.util.UUID

import akka.http.javadsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.ui.NussknackerApp
import pl.touk.nussknacker.ui.api.helpers.TestFactory

class BaseFlowTest extends FunSuite with ScalatestRouteTest
  with Matchers with ScalaFutures with BeforeAndAfterEach with BeforeAndAfterAll {

  private val mainRoute = NussknackerApp.initializeRoute()

  private val processId = UUID.randomUUID().toString

  private val endpoint = s"/api/processes/$processId"

  private val credentials = HttpCredentials.createBasicHttpCredentials("admin", "admin")

  test("saves, updates and retrieves sample process") {

    val process = EspProcessBuilder
      .id(processId)
      .exceptionHandler()
      .source("source", "csv-source").processorEnd("end", "monitor")

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

  test("initializes custom processes") {
    Get("/api/processes/customProcess1") ~> addCredentials(credentials) ~> mainRoute ~> check {
      status shouldEqual StatusCodes.OK
    }
  }


}

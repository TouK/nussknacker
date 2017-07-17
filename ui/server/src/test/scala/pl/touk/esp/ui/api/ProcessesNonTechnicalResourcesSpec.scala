package pl.touk.esp.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import org.scalatest._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.esp.ui.api.helpers.EspItTest
import pl.touk.esp.ui.api.helpers.TestFactory._
import pl.touk.esp.ui.codec.UiCodecs._
import pl.touk.esp.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.esp.ui.security.{LoggedUser, Permission}

import scala.concurrent.duration._
import scala.language.higherKinds

class ProcessesNonTechnicalResourcesSpec extends FlatSpec with ScalatestRouteTest with Matchers with Inside
  with ScalaFutures with OptionValues with Eventually with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))
  implicit val testtimeout = RouteTestTimeout(2.seconds)

  val routeWithAllPermissions = withAllPermissions(processesRoute)
  implicit val loggedUser = LoggedUser("lu", "", List(), List(testCategory))

  it should "return simplified version of process for non technical user" in {
    val processToSave = ProcessTestData.technicalValidProcess
    val allNodeIds = List(
      "source",
      "var1",
      "filter1",
      "enricher1",
      "switch1",
      "filter2",
      "enricher2",
      "sink1",
      "filter3",
      "enricher3",
      "sink2"
    )
    val allEdges = List(
      ("source", "var1"),
      ("var1", "filter1"),
      ("filter1", "enricher1"),
      ("enricher1", "switch1"),
      ("switch1", "filter2"),
      ("filter2", "enricher2"),
      ("enricher2", "sink1"),
      ("switch1", "filter3"),
      ("filter3", "enricher3"),
      ("enricher3", "sink2")
    )

    val nonTechnicalNodeIds = allNodeIds.diff(List("var1", "enricher1", "enricher2", "enricher3"))
    val nonTechnicalAdges = List(
      ("source", "filter1"),
      ("filter1", "switch1"),
      ("switch1", "filter2"),
      ("filter2", "sink1"),
      ("switch1", "filter3"),
      ("filter3", "sink2")
    )

    saveProcess(processToSave.id, processToSave) {
      status shouldEqual StatusCodes.OK
      Get(s"/processes/${processToSave.id}/2?businessView=false") ~> routeWithAllPermissions ~> check {
        val processDetails = responseAs[String].decodeOption[ProcessDetails].get
        processDetails.json.get.nodes.map(_.id) shouldBe allNodeIds
        processDetails.json.get.edges.map(e => (e.from, e.to)) shouldBe allEdges
        processDetails.json.get.validationResult.get.isOk shouldBe true
      }

      Get(s"/processes/${processToSave.id}/2?businessView=true") ~> routeWithAllPermissions ~> check {
        val processDetails = responseAs[String].decodeOption[ProcessDetails].get
        processDetails.json.get.nodes.map(_.id) shouldBe nonTechnicalNodeIds
        processDetails.json.get.edges.map(e => (e.from, e.to)) shouldBe nonTechnicalAdges
        processDetails.json.get.validationResult.get.isOk shouldBe true
      }
    }
  }

}
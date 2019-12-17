package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.processdetails.ValidatedProcessDetails
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, ProcessTestData}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.language.higherKinds

class ProcessesNonTechnicalResourcesSpec extends FlatSpec with ScalatestRouteTest with Matchers with Inside with FailFastCirceSupport
  with PatientScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  val routeWithAllPermissions = withAllPermissions(processesRoute)
  implicit val loggedUser = LoggedUser("1", "lu", testPermissionEmpty)

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

    saveProcess(ProcessName(processToSave.id), processToSave) {
      status shouldEqual StatusCodes.OK
      Get(s"/processes/${processToSave.id}?businessView=false") ~> routeWithAllPermissions ~> check {
        val processDetails = responseAs[ValidatedProcessDetails]
        checkNonBusinessView(allNodeIds, allEdges, processDetails)
      }

      Get(s"/processes/${processToSave.id}/2?businessView=false") ~> routeWithAllPermissions ~> check {
        val processDetails = responseAs[ValidatedProcessDetails]
        checkNonBusinessView(allNodeIds, allEdges, processDetails)
      }

      Get(s"/processes/${processToSave.id}?businessView=true") ~> routeWithAllPermissions ~> check {
        val processDetails = responseAs[ValidatedProcessDetails]
        checkBusinessView(nonTechnicalNodeIds, nonTechnicalAdges, processDetails)
      }

      Get(s"/processes/${processToSave.id}/2?businessView=true") ~> routeWithAllPermissions ~> check {
        val processDetails = responseAs[ValidatedProcessDetails]
        checkBusinessView(nonTechnicalNodeIds, nonTechnicalAdges, processDetails)
      }
    }
  }

  private def checkBusinessView(nonTechnicalNodeIds: List[String], nonTechnicalAdges: List[(String, String)], processDetails: ValidatedProcessDetails) = {
    processDetails.json.get.nodes.map(_.id) shouldBe nonTechnicalNodeIds
    processDetails.json.get.edges.map(e => (e.from, e.to)) shouldBe nonTechnicalAdges
    processDetails.json.get.validationResult.isOk shouldBe true
  }

  private def checkNonBusinessView(allNodeIds: List[String], allEdges: List[(String, String)], processDetails: ValidatedProcessDetails) = {
    processDetails.json.get.nodes.map(_.id) shouldBe allNodeIds
    processDetails.json.get.edges.map(e => (e.from, e.to)) shouldBe allEdges
    processDetails.json.get.validationResult.isOk shouldBe true
  }
}
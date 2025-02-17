package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Inside}
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.utils.domain.TestFactory.withAllPermissions
import pl.touk.nussknacker.test.base.it.NuResourcesTest
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent._
import pl.touk.nussknacker.ui.security.api.LoggedUser

class ProcessesChangeListenerSpec
    extends AnyFunSuite
    with ScalatestRouteTest
    with Matchers
    with Inside
    with FailFastCirceSupport
    with PatientScalaFutures
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with NuResourcesTest {

  private val routeWithAllPermissions = withAllPermissions(processesRoute)
  implicit val loggedUser: LoggedUser = createLoggedUser("1", "lu", Permission.ALL_PERMISSIONS.toSeq: _*)

  private val processName = ProcessTestData.sampleScenario.name

  test("listen to process create") {
    createProcessRequest(processName) { _ =>
      eventually {
        processChangeListener.events.toArray.last should matchPattern { case OnSaved(_, VersionId(1L), false) => }
      }
    }
  }

  test("listen to process update") {
    val processId = createEmptyProcess(processName)

    updateCanonicalProcess(ProcessTestData.validProcess) {
      eventually {
        processChangeListener.events.toArray.last should matchPattern {
          case OnSaved(`processId`, VersionId(2L), false) =>
        }
      }
    }
  }

  test("listen to process archive / unarchive") {
    val processId = createEmptyProcess(processName)

    Post(s"/archive/$processName") ~> routeWithAllPermissions ~> checkEventually {
      processChangeListener.events.toArray.last should matchPattern { case OnArchived(`processId`) => }
      Post(s"/unarchive/$processName") ~> routeWithAllPermissions ~> checkEventually {
        processChangeListener.events.toArray.last should matchPattern { case OnUnarchived(`processId`) => }
      }
    }
  }

  test("listen to process rename") {
    val processId = createEmptyProcess(processName)
    val newName   = ProcessName("new_name")

    Put(s"/processes/$processName/rename/$newName") ~> routeWithAllPermissions ~> checkEventually {
      processChangeListener.events.toArray.last should matchPattern {
        case OnRenamed(`processId`, `processName`, `newName`) =>
      }
    }
  }

  test("listen to delete process") {
    val processId = createArchivedProcess(processName)

    Delete(s"/processes/$processName") ~> routeWithAllPermissions ~> check {
      status shouldBe StatusCodes.OK
      eventually {
        processChangeListener.events.toArray.last should matchPattern { case OnDeleted(`processId`) => }
      }
    }
  }

  test("listen to deployment success") {
    val processId = createValidProcess(processName)
    val comment   = Some("deployComment")

    deployProcess(
      processName,
      comment
    ) ~> checkEventually {
      processChangeListener.events.toArray.last should matchPattern {
        case OnActionSuccess(`processId`, VersionId(1L), Some(_), _, ScenarioActionName.Deploy) =>
      }
    }
  }

  test("listen to deployment failure") {
    val processId = createValidProcess(processName)

    deploymentManager.withFailingDeployment(processName) {
      deployProcess(processName) ~> checkEventually {
        processChangeListener.events.toArray.last should matchPattern { case OnActionFailed(`processId`, _, _) => }
      }
    }
  }

  test("listen to deployment cancel") {
    val processId = createDeployedExampleScenario(processName)
    val comment   = Some("cancelComment")

    cancelProcess(
      ProcessTestData.sampleScenario.name,
      comment
    ) ~> checkEventually {
      val head = processChangeListener.events.toArray.last
      head should matchPattern {
        case OnActionSuccess(`processId`, VersionId(1L), Some(_), _, ScenarioActionName.Cancel) =>
      }
    }
  }

  private def checkEventually[T](body: => T): RouteTestResult => T = check(eventually(body))
}

package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Inside}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent._
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.language.higherKinds

class ProcessesChangeListenerSpec extends AnyFunSuite with ScalatestRouteTest with Matchers with Inside with FailFastCirceSupport
  with PatientScalaFutures with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  import TestCategories._

  private val routeWithAllPermissions = withAllPermissions(processesRoute)
  private val routeWithAdminPermissions = withAdminPermissions(processesRoute)
  implicit val loggedUser: LoggedUser = LoggedUser("1", "lu", testCategory)

  private val processName = ProcessName(SampleProcess.process.id)

  test("listen to category change") {
    val processId = createEmptyProcess(processName, TestCat, false)

    Post(s"/processes/category/${processName.value}/$TestCat2") ~> routeWithAdminPermissions ~> checkEventually {
      processChangeListener.events.toArray.last should matchPattern { case OnCategoryChanged(`processId`, TestCat, TestCat2) => }
    }
  }

  test("listen to process create") {
    Post(s"/processes/${processName.value}/$TestCat?isSubprocess=false") ~> processesRouteWithAllPermissions ~> checkEventually {
      processChangeListener.events.toArray.last should matchPattern { case OnSaved(_, VersionId(1L)) => }
    }
  }

  test("listen to process update") {
    val processId = createEmptyProcess(processName, TestCat, false)

    updateProcess(processName, ProcessTestData.validProcess) {
      eventually {
        processChangeListener.events.toArray.last should matchPattern { case OnSaved(`processId`, VersionId(2L)) => }
      }
    }
  }

  test("listen to process archive / unarchive") {
    val processId = createEmptyProcess(processName, TestCat, false)

    Post(s"/archive/${processName.value}") ~> routeWithAllPermissions ~> checkEventually {
      processChangeListener.events.toArray.last should matchPattern { case OnArchived(`processId`) => }
      Post(s"/unarchive/${processName.value}") ~> routeWithAllPermissions ~> checkEventually {
        processChangeListener.events.toArray.last should matchPattern { case OnUnarchived(`processId`) => }
      }
    }
  }

  test("listen to process rename") {
    val processId = createEmptyProcess(processName, TestCat, false)
    val newName = ProcessName("new_name")

    Put(s"/processes/${processName.value}/rename/${newName.value}") ~> routeWithAllPermissions ~> checkEventually {
      processChangeListener.events.toArray.last should matchPattern { case OnRenamed(`processId`, `processName`, `newName`) => }
    }
  }

  test("listen to delete process") {
    val processId = createEmptyProcess(processName, TestCat, false)

    Delete(s"/processes/${processName.value}") ~> routeWithAllPermissions ~> checkEventually {
      processChangeListener.events.toArray.last should matchPattern { case OnDeleted(`processId`) => }
    }
  }

  test("listen to deployment success") {
    val processId = createValidProcess(processName, TestCat, false)
    val comment = Some("deployComment")

    deployProcess(processName.value, Some(DeploymentCommentSettings.unsafe(validationPattern = ".*", Some("exampleDeploy"))), comment) ~> checkEventually {
      processChangeListener.events.toArray.last should matchPattern { case OnDeployActionSuccess(`processId`, VersionId(1L), Some(_), _, ProcessActionType.Deploy) => }
    }
  }

  test("listen to deployment failure") {
    val processId = createValidProcess(processName, TestCat, false)

    deploymentManager.withFailingDeployment(processName) {
      deployProcess(processName.value) ~> checkEventually {
        processChangeListener.events.toArray.last should matchPattern { case OnDeployActionFailed(`processId`, _) => }
      }
    }
  }

  test("listen to deployment cancel") {
    val processId = createDeployedProcess(processName)
    val comment = Some("cancelComment")

    cancelProcess(SampleProcess.process.id, Some(DeploymentCommentSettings.unsafe(validationPattern = ".*", Some("exampleDeploy"))), comment) ~> checkEventually {
      val head = processChangeListener.events.toArray.last
      head should matchPattern
      { case OnDeployActionSuccess(`processId`, VersionId(1L), Some(_), _, ProcessActionType.Cancel) => }
    }
  }

  private def checkEventually[T](body: => T): RouteTestResult => T = check(eventually(body))
}

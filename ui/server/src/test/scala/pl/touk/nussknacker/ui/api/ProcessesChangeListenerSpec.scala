package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.processdetails.DeploymentAction
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent._
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.language.higherKinds

class ProcessesChangeListenerSpec extends FunSuite with ScalatestRouteTest with Matchers with Inside with FailFastCirceSupport
  with PatientScalaFutures with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  private val routeWithAllPermissions = withAllPermissions(processesRoute)
  private val routeWithAdminPermissions = withAdminPermissions(processesRoute)
  implicit val loggedUser = LoggedUser("1", "lu", testCategory)

  private val processName = ProcessName(SampleProcess.process.id)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    TestProcessChangeListener.clear()
  }

  test("listen to category change") {
    val newCategory = "expectedCategory"
    val processId = createProcess(processName, testCategoryName, false)

    Post(s"/processes/category/${processName.value}/$newCategory") ~> routeWithAdminPermissions ~> checkEventually {
      TestProcessChangeListener.events.head should matchPattern { case OnCategoryChanged(`processId`, `testCategoryName`, `newCategory`) => }
    }
  }

  test("listen to process create") {
    Post(s"/processes/${processName.value}/$testCategoryName?isSubprocess=false") ~> processesRouteWithAllPermissions ~> checkEventually {
      TestProcessChangeListener.events.head should matchPattern { case OnSaved(_, 1L) => }
    }
  }

  test("listen to process update") {
    val processId = createProcess(processName, testCategoryName, false)

    updateProcess(processName, ProcessTestData.validProcess) {
      eventually {
        TestProcessChangeListener.events.head should matchPattern { case OnSaved(`processId`, 2L) => }
      }
    }
  }


  test("listen to process archive / unarchive") {
    val processId = createProcess(processName, testCategoryName, false)

    Post(s"/archive/${processName.value}") ~> routeWithAllPermissions ~> checkEventually {
      TestProcessChangeListener.events.head should matchPattern { case OnArchived(`processId`) => }
      Post(s"/unarchive/${processName.value}") ~> routeWithAllPermissions ~> checkEventually {
        TestProcessChangeListener.events.head should matchPattern { case OnUnarchived(`processId`) => }
      }
    }
  }

  test("listen to process rename") {
    val processId = createProcess(processName, testCategoryName, false)
    val newName = ProcessName("new_name")

    Put(s"/processes/${processName.value}/rename/${newName.value}") ~> routeWithAllPermissions ~> checkEventually {
      TestProcessChangeListener.events.head should matchPattern { case OnRenamed(`processId`, `processName`, `newName`) => }
    }
  }

  test("listen to delete process") {
    val processId = createProcess(processName, testCategoryName, false)

    Delete(s"/processes/${processName.value}") ~> routeWithAllPermissions ~> checkEventually {
      TestProcessChangeListener.events.head should matchPattern { case OnDeleted(`processId`) => }
    }
  }

  test("listen to deployment success") {
    val processId = createProcess(processName, testCategoryName, false)
    val comment = Some("deployComment")

    deployProcess(processName.value, true, comment) ~> checkEventually {
      TestProcessChangeListener.events.head should matchPattern { case OnDeployActionSuccess(`processId`, 1L, `comment`, _, DeploymentAction.Deploy) => }
    }
  }
  test("listen to deployment failure") {
    val processId = createProcess(processName, testCategoryName, false)

    processManager.withFailingDeployment {
      deployProcess(processName.value) ~> checkEventually {
        TestProcessChangeListener.events.head should matchPattern { case OnDeployActionFailed(`processId`, _) => }
      }
    }
  }

  test("listen to deployment cancel") {
    val processId = createDeployedProcess(processName, testCategoryName, false)
    val comment = Some("deployComment")

    cancelProcess(SampleProcess.process.id, true, comment) ~> checkEventually {
      TestProcessChangeListener.events.head should matchPattern { case OnDeployActionSuccess(`processId`, 1L, `comment`, _, DeploymentAction.Cancel) => }
    }
  }

  private def checkEventually[T](body: ⇒ T): RouteTestResult ⇒ T = check(eventually(body))
}
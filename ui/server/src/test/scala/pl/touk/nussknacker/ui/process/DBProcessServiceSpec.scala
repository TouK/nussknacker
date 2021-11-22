package pl.touk.nussknacker.ui.process

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.Deploy
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{StubSubprocessRepository, processResolving}
import pl.touk.nussknacker.ui.api.helpers.{MockFetchingProcessRepository, TestFactory}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

import java.time.Duration
import scala.concurrent.ExecutionContext.Implicits.global

class DBProcessServiceSpec extends FlatSpec with Matchers with PatientScalaFutures {

  import org.scalatest.prop.TableDrivenPropertyChecks._
  import pl.touk.nussknacker.ui.api.helpers.TestCategories._
  import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil._

  //These users were created based on categoriesConfig at ui.conf
  private val adminUser = TestFactory.adminUser()
  private val categoriesUser = TestFactory.userWithCategoriesReadPermission(username = "categoriesUser", categories = catCategories)
  private val testUser = TestFactory.userWithCategoriesReadPermission(username = "categoriesUser", categories = testCategories)
  private val testReqRespUser = TestFactory.userWithCategoriesReadPermission(username = "testReqRespUser", categories = testCategories ++ reqResCategories)

  private val category1Process = createBasicProcess("category1Process", isSubprocess = false, isArchived = false, category = Category1, lastAction = Some(Deploy))
  private val category2ArchivedProcess = createBasicProcess("category2ArchivedProcess", isSubprocess = false, isArchived = true, category = Category2)
  private val testSubProcess = createBasicProcess("testSubProcess", isSubprocess = true, isArchived = false, category = TESTCAT)
  private val reqRespArchivedSubProcess = createBasicProcess("reqRespArchivedSubProcess", isSubprocess = true, isArchived = true, category = ReqRes)

  private val processes: List[ProcessWithoutJson] = List(
    category1Process, category2ArchivedProcess, testSubProcess, reqRespArchivedSubProcess,
  )

  private val processCategoryService = new ConfigProcessCategoryService(ConfigWithScalaVersion.config)
  private val newProcessPreparer = TestFactory.createNewProcessPreparer()

  private val DefaultRequestTimeLimit = Duration.ofMinutes(1)
  private val dummyManagerActor = TestFactory.newDummyManagerActor()
  private val dummyWriteProcessRepository = TestFactory.newDummyWriteProcessRepository()
  private val dummyActionRepository = TestFactory.newDummyActionRepository()
  private val dummyRepositoryManager = TestFactory.newDummyRepositoryManager()
  private val dummySubprocessRepository = StubSubprocessRepository(Set.empty)

  it should "return user processes" in {
    val mockRepository = MockFetchingProcessRepository(processes)

    val dBProcessService = new DBProcessService(
      dummyManagerActor, DefaultRequestTimeLimit, newProcessPreparer, processCategoryService, processResolving,
      dummyRepositoryManager, mockRepository, dummyActionRepository, dummyWriteProcessRepository, dummySubprocessRepository,
    )

    val testingData = Table(
      ("user", "expected"),
      (adminUser, processes),
      (categoriesUser, List(category1Process, category2ArchivedProcess)),
      (testUser, List(testSubProcess)),
      (testReqRespUser, List(testSubProcess, reqRespArchivedSubProcess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessWithoutJson]) =>
      val result = dBProcessService.getProcesses[Unit](user).futureValue
      result shouldBe expected
    }
  }
}

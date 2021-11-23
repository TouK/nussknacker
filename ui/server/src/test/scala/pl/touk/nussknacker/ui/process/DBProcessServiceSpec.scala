package pl.touk.nussknacker.ui.process

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.Deploy
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.StubSubprocessRepository
import pl.touk.nussknacker.ui.api.helpers.{MockFetchingProcessRepository, TestFactory}
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.subprocess.SubprocessDetails
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

  private val processes: List[BaseProcessDetails[Unit]] = List(
    category1Process, category2ArchivedProcess, testSubProcess, reqRespArchivedSubProcess
  )

  private val subprocessCategory1 = createSubprocess("subprocessCategory1", Category1)
  private val subprocessCategory2 = createSubprocess("subprocessCategory2", Category2)
  private val subprocessTest = createSubprocess("subprocessTest", TESTCAT)
  private val subprocessReqResp = createSubprocess("subprocessReqResp", ReqRes)

  private val subprocesses: Set[SubprocessDetails] = Set(
    subprocessCategory1, subprocessCategory2, subprocessTest, subprocessReqResp
  )

  private val processCategoryService = new ConfigProcessCategoryService(ConfigWithScalaVersion.config)

  it should "return user processes" in {
    val dBProcessService = createDbProcessService(processes)

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

  it should "return user subprocesses" in {
    val dBProcessService = createDbProcessService[Unit](Nil, subprocesses)

    val testingData = Table(
      ("user", "subprocesses"),
      (adminUser, subprocesses),
      (categoriesUser, Set(subprocessCategory1, subprocessCategory2)),
      (testUser, Set(subprocessTest)),
      (testReqRespUser, Set(subprocessTest, subprocessReqResp)),
    )

    forAll(testingData) { (user: LoggedUser, expected: Set[SubprocessDetails] ) =>
      val result = dBProcessService.getSubProcesses(user)
      result shouldBe expected
    }
  }

  private def createSubprocess(name: String, category: Category) = {
    val metaData = MetaData(name, FragmentSpecificData())
    val exceptionHandler = ExceptionHandlerRef(List())
    SubprocessDetails(CanonicalProcess(metaData, exceptionHandler, Nil, Nil), category)
  }

  private def createDbProcessService[T: ProcessShapeFetchStrategy](processes: List[BaseProcessDetails[T]] = Nil, subprocesses: Set[SubprocessDetails] = Set.empty): DBProcessService =
    new DBProcessService(
      managerActor = TestFactory.newDummyManagerActor(),
      requestTimeLimit = Duration.ofMinutes(1),
      newProcessPreparer = TestFactory.createNewProcessPreparer(),
      processCategoryService = processCategoryService,
      processResolving = TestFactory.processResolving,
      repositoryManager = TestFactory.newDummyRepositoryManager(),
      fetchingProcessRepository = MockFetchingProcessRepository(processes),
      processActionRepository = TestFactory.newDummyActionRepository(),
      processRepository = TestFactory.newDummyWriteProcessRepository(),
      subprocessRepository = new StubSubprocessRepository(subprocesses)
    )
}

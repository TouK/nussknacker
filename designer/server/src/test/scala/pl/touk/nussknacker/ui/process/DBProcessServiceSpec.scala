package pl.touk.nussknacker.ui.process

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.Deploy
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.variables.MetaVariables
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeTypingData, ValidationResult}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.api.ProcessesResources.UnmarshallError
import pl.touk.nussknacker.ui.api.helpers.{MockFetchingProcessRepository, ProcessTestData, TestFactory}
import pl.touk.nussknacker.ui.process.exception.ProcessIllegalAction
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.subprocess.SubprocessDetails
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class DBProcessServiceSpec extends AnyFlatSpec with Matchers with PatientScalaFutures {

  import io.circe.syntax._
  import org.scalatest.prop.TableDrivenPropertyChecks._
  import pl.touk.nussknacker.ui.api.helpers.TestCategories._
  import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil._

  //These users were created based on categoriesConfig at designer.conf
  private val adminUser = TestFactory.adminUser()
  private val categoriesUser = TestFactory.userWithCategoriesReadPermission(username = "categoriesUser", categories = CategoryCategories)
  private val testUser = TestFactory.userWithCategoriesReadPermission(username = "categoriesUser", categories = TestCategories)
  private val testReqRespUser = TestFactory.userWithCategoriesReadPermission(username = "testReqRespUser", categories = TestCategories ++ ReqResCategories)

  private val category1Process = createBasicProcess("category1Process", category = Category1, lastAction = Some(Deploy))
  private val category2ArchivedProcess = createBasicProcess("category2ArchivedProcess", isArchived = true, category = Category2)
  private val testSubProcess = createSubProcess("testSubProcess", category = TestCat)
  private val reqRespArchivedSubProcess = createBasicProcess("reqRespArchivedSubProcess", isArchived = true, category = ReqRes)

  private val processes: List[ProcessWithJson] = List(
    category1Process, category2ArchivedProcess, testSubProcess, reqRespArchivedSubProcess
  )

  private val subprocessCategory1 = createSubProcess("subprocessCategory1", category = Category1)
  private val subprocessCategory2 = createSubProcess("subprocessCategory2", category = Category2)
  private val subprocessTest = createSubProcess("subprocessTest", category = TestCat)
  private val subprocessReqResp = createSubProcess("subprocessReqResp", category = ReqRes)

  private val subprocesses = Set(
    subprocessCategory1, subprocessCategory2, subprocessTest, subprocessReqResp
  )

  private val processCategoryService = new ConfigProcessCategoryService(ConfigWithScalaVersion.TestsConfig)

  it should "return user processes" in {
    val dBProcessService = createDbProcessService(processes)

    val testingData = Table(
      ("user", "expected"),
      (adminUser, processes.filter(_.isArchived == false)),
      (categoriesUser, List(category1Process)),
      (testUser, List(testSubProcess)),
      (testReqRespUser, List(testSubProcess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessWithJson]) =>
      val result = dBProcessService.getProcesses[DisplayableProcess](user).futureValue
      result shouldBe expected
    }
  }

  it should "return user archived processes" in {
    val dBProcessService = createDbProcessService(processes)

    val testingData = Table(
      ("user", "expected"),
      (adminUser, processes.filter(_.isArchived == true)),
      (categoriesUser, List(category2ArchivedProcess)),
      (testUser, Nil),
      (testReqRespUser, List(reqRespArchivedSubProcess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessWithJson]) =>
      val result = dBProcessService.getArchivedProcesses[DisplayableProcess](user).futureValue
      result shouldBe expected
    }
  }

  it should "return user subprocesses" in {
    val dBProcessService = createDbProcessService[DisplayableProcess](subprocesses.toList)

    val testingData = Table(
      ("user", "subprocesses"),
      (adminUser, subprocesses),
      (categoriesUser, Set(subprocessCategory1, subprocessCategory2)),
      (testUser, Set(subprocessTest)),
      (testReqRespUser, Set(subprocessTest, subprocessReqResp)),
    )

    forAll(testingData) { (user: LoggedUser, expected: Set[ProcessWithJson] ) =>
      val result = dBProcessService.getSubProcesses(None)(user).futureValue
      val subprocessDetails = expected.map(convertBasicProcessToSubprocessDetails)
      result shouldBe subprocessDetails
    }
  }

  it should "import process" in {
    val dBProcessService = createDbProcessService(processes)

    val categoryDisplayable = ProcessTestData.sampleDisplayableProcess.copy(id = category1Process.name, category = Some(Category1))
    val categoryStringData = ProcessConverter.fromDisplayable(categoryDisplayable).asJson.spaces2
    val baseProcessData = ProcessConverter.fromDisplayable(ProcessTestData.sampleDisplayableProcess).asJson.spaces2

    val testingData = Table(
      ("processId", "data", "expected"),
      (category1Process.idWithName, categoryStringData, importSuccess(categoryDisplayable)), //importing data with the same id
      (category1Process.idWithName, baseProcessData, importSuccess(categoryDisplayable)), //importing data with different id
      (category2ArchivedProcess.idWithName, baseProcessData, Left(ProcessIllegalAction("Import is not allowed for archived process."))),
      (category1Process.idWithName, "bad-string", Left(UnmarshallError("expected json value got 'bad-st...' (line 1, column 1)"))),
    )

    forAll(testingData) { (idWithName: ProcessIdWithName, data: String, expected: XError[ValidatedDisplayableProcess]) =>
      val result = dBProcessService.importProcess(idWithName, data)(adminUser).futureValue

      result shouldBe expected
    }
  }

  private def convertBasicProcessToSubprocessDetails(process: ProcessWithJson) =
    SubprocessDetails(ProcessConverter.fromDisplayable(process.json), process.processCategory)

  private def importSuccess(displayableProcess: DisplayableProcess): Right[EspError, ValidatedDisplayableProcess] = {
    val meta = MetaVariables.typingResult(displayableProcess.metaData)

    val nodeResults = Map(
      "sinkId" -> NodeTypingData(Map("input" -> Unknown, "meta" -> meta), None, Map.empty),
      "sourceId" -> NodeTypingData(Map("meta" -> meta), None, Map.empty)
    )

    Right(new ValidatedDisplayableProcess(displayableProcess, ValidationResult.success.copy(nodeResults = nodeResults)))
  }

  private def createDbProcessService[T: ProcessShapeFetchStrategy](processes: List[BaseProcessDetails[T]] = Nil): DBProcessService =
    new DBProcessService(
      managerActor = TestFactory.newDummyManagerActor(),
      requestTimeLimit = 1 minute,
      newProcessPreparer = TestFactory.createNewProcessPreparer(),
      processCategoryService = processCategoryService,
      processResolving = TestFactory.processResolving,
      repositoryManager = TestFactory.newDummyRepositoryManager(),
      fetchingProcessRepository = new MockFetchingProcessRepository(processes),
      processActionRepository = TestFactory.newDummyActionRepository(),
      processRepository = TestFactory.newDummyWriteProcessRepository(),
      processValidation = TestFactory.processValidation
    )
}

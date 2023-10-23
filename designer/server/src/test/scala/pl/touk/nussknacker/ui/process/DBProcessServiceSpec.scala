package pl.touk.nussknacker.ui.process

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.Deploy
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.variables.MetaVariables
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.processdetails.ProcessDetails
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeTypingData, ValidationResult}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.api.ProcessesResources.UnmarshallError
import pl.touk.nussknacker.ui.api.helpers.{MockFetchingProcessRepository, ProcessTestData, TestFactory}
import pl.touk.nussknacker.ui.fixedvaluespresets.TestFixedValuesPresetProvider
import pl.touk.nussknacker.ui.process.exception.ProcessIllegalAction
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.fragment.FragmentDetails
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

import scala.concurrent.ExecutionContext.Implicits.global

class DBProcessServiceSpec extends AnyFlatSpec with Matchers with PatientScalaFutures {

  import io.circe.syntax._
  import org.scalatest.prop.TableDrivenPropertyChecks._
  import pl.touk.nussknacker.ui.api.helpers.TestCategories._
  import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil._

  // These users were created based on categories configuration at designer.conf
  private val adminUser = TestFactory.adminUser()
  private val categoriesUser =
    TestFactory.userWithCategoriesReadPermission(username = "categoriesUser", categories = CategoryCategories)
  private val testUser =
    TestFactory.userWithCategoriesReadPermission(username = "categoriesUser", categories = TestCategories)

  private val testReqRespUser = TestFactory.userWithCategoriesReadPermission(
    username = "testReqRespUser",
    categories = TestCategories ++ ReqResCategories
  )

  private val category1Process = createBasicProcess("category1Process", category = Category1, lastAction = Some(Deploy))
  private val category2ArchivedProcess =
    createBasicProcess("category2ArchivedProcess", isArchived = true, category = Category2)
  private val testfragment = createFragment("testfragment", category = TestCat)
  private val reqRespArchivedfragment =
    createBasicProcess("reqRespArchivedfragment", isArchived = true, category = ReqRes)

  private val processes: List[ProcessDetails] = List(
    category1Process,
    category2ArchivedProcess,
    testfragment,
    reqRespArchivedfragment
  )

  private val fragmentCategory1 = createFragment("fragmentCategory1", category = Category1)
  private val fragmentCategory2 = createFragment("fragmentCategory2", category = Category2)
  private val fragmentTest      = createFragment("fragmentTest", category = TestCat)
  private val fragmentReqResp   = createFragment("fragmentReqResp", category = ReqRes)

  private val fragments = Set(
    fragmentCategory1,
    fragmentCategory2,
    fragmentTest,
    fragmentReqResp
  )

  private val processCategoryService = TestFactory.createCategoryService(ConfigWithScalaVersion.TestsConfig)

  it should "return user processes" in {
    val dBProcessService = createDbProcessService(processes)

    val testingData = Table(
      ("user", "expected"),
      (adminUser, processes.filter(_.isArchived == false)),
      (categoriesUser, List(category1Process)),
      (testUser, List(testfragment)),
      (testReqRespUser, List(testfragment)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessDetails]) =>
      implicit val loggedUser: LoggedUser = user

      val result = dBProcessService.getProcessesAndFragments[DisplayableProcess].futureValue
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
      (testReqRespUser, List(reqRespArchivedfragment)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessDetails]) =>
      implicit val loggedUser: LoggedUser = user

      val result = dBProcessService.getArchivedProcessesAndFragments[DisplayableProcess].futureValue
      result shouldBe expected
    }
  }

  it should "return user fragments" in {
    val dBProcessService = createDbProcessService(fragments.toList)

    val testingData = Table(
      ("user", "fragments"),
      (adminUser, fragments),
      (categoriesUser, Set(fragmentCategory1, fragmentCategory2)),
      (testUser, Set(fragmentTest)),
      (testReqRespUser, Set(fragmentTest, fragmentReqResp)),
    )

    forAll(testingData) { (user: LoggedUser, expected: Set[ProcessDetails]) =>
      val result          = dBProcessService.getFragmentsDetails(None)(user).futureValue
      val fragmentDetails = expected.map(convertBasicProcessToFragmentDetails)
      result shouldBe fragmentDetails
    }
  }

  it should "import process" in {
    val dBProcessService = createDbProcessService(processes)

    val categoryDisplayable =
      ProcessTestData.sampleDisplayableProcess.copy(id = category1Process.name, category = Category1)
    val categoryStringData = ProcessConverter.fromDisplayable(categoryDisplayable).asJson.spaces2
    val baseProcessData    = ProcessConverter.fromDisplayable(ProcessTestData.sampleDisplayableProcess).asJson.spaces2

    val testingData = Table(
      ("processId", "data", "expected"),
      (
        category1Process.idWithName,
        categoryStringData,
        importSuccess(categoryDisplayable)
      ), // importing data with the same id
      (
        category1Process.idWithName,
        baseProcessData,
        importSuccess(categoryDisplayable)
      ), // importing data with different id
      (
        category2ArchivedProcess.idWithName,
        baseProcessData,
        Left(ProcessIllegalAction("Import is not allowed for archived process."))
      ),
      (
        category1Process.idWithName,
        "bad-string",
        Left(UnmarshallError("expected json value got 'bad-st...' (line 1, column 1)"))
      ),
    )

    forAll(testingData) {
      (idWithName: ProcessIdWithName, data: String, expected: XError[ValidatedDisplayableProcess]) =>
        val result = dBProcessService.importProcess(idWithName, data)(adminUser).futureValue

        result shouldBe expected
    }
  }

  private def convertBasicProcessToFragmentDetails(process: ProcessDetails) =
    FragmentDetails(ProcessConverter.fromDisplayable(process.json), process.processCategory)

  private def importSuccess(displayableProcess: DisplayableProcess): Right[EspError, ValidatedDisplayableProcess] = {
    val meta = MetaVariables.typingResult(displayableProcess.metaData)

    val nodeResults = Map(
      "sinkId"   -> NodeTypingData(Map("input" -> Unknown, "meta" -> meta), Some(List.empty), Map.empty),
      "sourceId" -> NodeTypingData(Map("meta" -> meta), Some(List.empty), Map.empty)
    )

    Right(new ValidatedDisplayableProcess(displayableProcess, ValidationResult.success.copy(nodeResults = nodeResults)))
  }

  private def createDbProcessService(processes: List[ProcessDetails] = Nil): DBProcessService =
    new DBProcessService(
      deploymentService = TestFactory.deploymentService(),
      newProcessPreparer = TestFactory.createNewProcessPreparer(),
      getProcessCategoryService = () => processCategoryService,
      processResolving = TestFactory.processResolving,
      dbioRunner = TestFactory.newDummyDBIOActionRunner(),
      fetchingProcessRepository = MockFetchingProcessRepository.withProcessesDetails(processes),
      processActionRepository = TestFactory.newDummyActionRepository(),
      processRepository = TestFactory.newDummyWriteProcessRepository(),
      processValidation = TestFactory.processValidation,
      fixedValuesPresetProvider = TestFixedValuesPresetProvider
    )

}

package pl.touk.nussknacker.ui.process

import org.scalatest.OptionValues
import org.scalatest.exceptions.TestFailedException
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.Deploy
import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.restmodel.validation.ValidatedDisplayableProcess
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeTypingData, ValidationResult}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.NuDesignerError.XError
import pl.touk.nussknacker.ui.api.ProcessesResources.ProcessUnmarshallingError
import pl.touk.nussknacker.ui.api.helpers.{MockFetchingProcessRepository, ProcessTestData, TestFactory}
import pl.touk.nussknacker.ui.process.exception.ProcessIllegalAction
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

import scala.concurrent.ExecutionContext.Implicits.global

class DBProcessServiceSpec extends AnyFlatSpec with Matchers with PatientScalaFutures with OptionValues {

  import io.circe.syntax._
  import org.scalatest.prop.TableDrivenPropertyChecks._
  import pl.touk.nussknacker.ui.api.helpers.TestCategories._
  import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil._

  // These users were created based on categories configuration at designer.conf
  private val adminUser = TestFactory.adminUser()
  private val allCategoriesUser =
    TestFactory.userWithCategoriesReadPermission(username = "allCategoriesUser", categories = AllCategories)
  private val category1User =
    TestFactory.userWithCategoriesReadPermission(username = "testUser", categories = List(Category1))

  private val category1Process =
    createScenarioEntity("category1Process", category = Category1, lastAction = Some(Deploy))
  private val category1Fragment = createFragmentEntity("category1Fragment", category = Category1)
  private val category1ArchivedFragment =
    createScenarioEntity("category1ArchivedFragment", isArchived = true, category = Category1)
  private val category2ArchivedProcess =
    createScenarioEntity("category2ArchivedProcess", isArchived = true, category = Category2)

  private val processes: List[ScenarioWithDetailsEntity[DisplayableProcess]] = List(
    category1Process,
    category1Fragment,
    category1ArchivedFragment,
    category2ArchivedProcess,
  )

  private val fragmentCategory1 = createFragmentEntity("fragmentCategory1", category = Category1)
  private val fragmentCategory2 = createFragmentEntity("fragmentCategory2", category = Category2)

  private val fragments = List(
    fragmentCategory1,
    fragmentCategory2,
  )

  private val processCategoryService = TestFactory.createCategoryService(ConfigWithScalaVersion.TestsConfig)

  it should "return user processes" in {
    val dBProcessService = createDbProcessService(processes)

    val testingData = Table(
      ("user", "expected"),
      (adminUser, processes.filter(_.isArchived == false)),
      (allCategoriesUser, processes.filter(_.isArchived == false)),
      (category1User, List(category1Process, category1Fragment)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ScenarioWithDetailsEntity[DisplayableProcess]]) =>
      implicit val loggedUser: LoggedUser = user

      val result = dBProcessService
        .getLatestRawProcessesWithDetails[DisplayableProcess](ScenarioQuery(isArchived = Some(false)))
        .futureValue
      result shouldBe expected
    }
  }

  it should "return user archived processes" in {
    val dBProcessService = createDbProcessService(processes)

    val testingData = Table(
      ("user", "expected"),
      (adminUser, processes.filter(_.isArchived == true)),
      (allCategoriesUser, processes.filter(_.isArchived == true)),
      (category1User, List(category1ArchivedFragment)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ScenarioWithDetailsEntity[DisplayableProcess]]) =>
      implicit val loggedUser: LoggedUser = user

      val result = dBProcessService
        .getLatestRawProcessesWithDetails[DisplayableProcess](ScenarioQuery(isArchived = Some(true)))
        .futureValue
      result shouldBe expected
    }
  }

  it should "return user fragments" in {
    val dBProcessService = createDbProcessService(fragments)

    val testingData = Table(
      ("user", "fragments"),
      (adminUser, fragments),
      (allCategoriesUser, fragments),
      (category1User, List(fragmentCategory1)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ScenarioWithDetailsEntity[DisplayableProcess]]) =>
      implicit val implicitUser: LoggedUser = user
      val result = dBProcessService
        .getLatestRawProcessesWithDetails[DisplayableProcess](
          ScenarioQuery(isFragment = Some(true), isArchived = Some(false))
        )
        .futureValue
      result shouldBe expected
    }
  }

  it should "import process" in {
    val dBProcessService = createDbProcessService(processes)

    val categoryStringData =
      ProcessConverter.fromDisplayable(ProcessTestData.sampleDisplayableProcess, category1Process.name).asJson.spaces2
    val baseProcessData = ProcessConverter
      .fromDisplayable(ProcessTestData.sampleDisplayableProcess, ProcessTestData.sampleProcessName)
      .asJson
      .spaces2

    val testingData = Table(
      ("processId", "data", "expected"),
      (
        category1Process.idWithName,
        categoryStringData,
        importSuccess(ProcessTestData.sampleDisplayableProcess)
      ), // importing data with the same id
      (
        category1Process.idWithName,
        baseProcessData,
        importSuccess(ProcessTestData.sampleDisplayableProcess)
      ), // importing data with different id
      (
        category1ArchivedFragment.idWithName,
        baseProcessData,
        Left(ProcessIllegalAction("Import is not allowed for archived process."))
      ),
      (
        category1Process.idWithName,
        "bad-string",
        Left(ProcessUnmarshallingError("expected json value got 'bad-st...' (line 1, column 1)"))
      ),
    )

    forAll(testingData) {
      (idWithName: ProcessIdWithName, data: String, expected: XError[ValidatedDisplayableProcess]) =>
        def doImport() = dBProcessService.importProcess(idWithName, data)(adminUser).futureValue

        expected match {
          case Right(expectedValue) => doImport() shouldEqual expectedValue
          case Left(expectedError) =>
            (the[TestFailedException] thrownBy {
              doImport()
            }).cause.value shouldEqual expectedError
        }
    }
  }

  private def importSuccess(
      displayableProcess: DisplayableProcess
  ): Right[NuDesignerError, ValidatedDisplayableProcess] = {
    val nodeResults = Map(
      "sinkId"   -> NodeTypingData(Map("input" -> Unknown), Some(List.empty), Map.empty),
      "sourceId" -> NodeTypingData(Map.empty, Some(List.empty), Map.empty)
    )

    Right(
      ValidatedDisplayableProcess.withValidationResult(
        displayableProcess,
        ValidationResult.success.copy(nodeResults = nodeResults)
      )
    )
  }

  private def createDbProcessService(
      processes: List[ScenarioWithDetailsEntity[DisplayableProcess]] = Nil
  ): DBProcessService =
    new DBProcessService(
      deploymentService = TestFactory.deploymentService(),
      newProcessPreparers = TestFactory.newProcessPreparerByProcessingType,
      getProcessCategoryService = () => processCategoryService,
      processResolverByProcessingType = TestFactory.processResolverByProcessingType,
      dbioRunner = TestFactory.newDummyDBIOActionRunner(),
      fetchingProcessRepository = MockFetchingProcessRepository.withProcessesDetails(processes),
      processActionRepository = TestFactory.newDummyActionRepository(),
      processRepository = TestFactory.newDummyWriteProcessRepository()
    )

}

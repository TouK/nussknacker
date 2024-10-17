package pl.touk.nussknacker.ui.process

import org.scalatest.OptionValues
import org.scalatest.exceptions.TestFailedException
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName.Deploy
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.restmodel.validation.ScenarioGraphWithValidationResult
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeTypingData, ValidationResult}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory.{Category1, Category2}
import pl.touk.nussknacker.test.mock.MockFetchingProcessRepository
import pl.touk.nussknacker.test.utils.domain.TestProcessUtil.{createFragmentEntity, createScenarioEntity}
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory}
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.NuDesignerError.XError
import pl.touk.nussknacker.ui.api.ProcessesResources.ProcessUnmarshallingError
import pl.touk.nussknacker.ui.process.exception.ProcessIllegalAction
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity
import pl.touk.nussknacker.ui.security.api.{LoggedUser, RealLoggedUser}

import java.time.Clock
import scala.concurrent.ExecutionContext.Implicits.global

class DBProcessServiceSpec extends AnyFlatSpec with Matchers with PatientScalaFutures with OptionValues {

  import io.circe.syntax._
  import org.scalatest.prop.TableDrivenPropertyChecks._

  // These users were created based on categories configuration at designer.conf
  private val adminUser = TestFactory.adminUser()

  private val allCategoriesUser = RealLoggedUser(
    id = "allCategoriesUser",
    username = "allCategoriesUser",
    categoryPermissions = TestCategory.values.map(c => c.stringify -> Set(Permission.Read)).toMap
  )

  private val category1User = RealLoggedUser(
    id = "testUser",
    username = "testUser",
    categoryPermissions = Map(Category1.stringify -> Set(Permission.Read))
  )

  private val category1Process =
    createScenarioEntity("category1Process", category = Category1.stringify, lastAction = Some(Deploy))
  private val category1Fragment = createFragmentEntity("category1Fragment", category = Category1.stringify)
  private val category1ArchivedFragment =
    createScenarioEntity("category1ArchivedFragment", isArchived = true, category = Category1.stringify)
  private val category2ArchivedProcess =
    createScenarioEntity("category2ArchivedProcess", isArchived = true, category = Category2.stringify)

  private val processes: List[ScenarioWithDetailsEntity[ScenarioGraph]] = List(
    category1Process,
    category1Fragment,
    category1ArchivedFragment,
    category2ArchivedProcess,
  )

  private val fragmentCategory1 = createFragmentEntity("fragmentCategory1", category = Category1.stringify)
  private val fragmentCategory2 = createFragmentEntity("fragmentCategory2", category = Category2.stringify)

  private val fragments = List(
    fragmentCategory1,
    fragmentCategory2,
  )

  it should "return user processes" in {
    val dBProcessService = createDbProcessService(processes)

    val testingData = Table(
      ("user", "expected"),
      (adminUser, processes.filter(_.isArchived == false)),
      (allCategoriesUser, processes.filter(_.isArchived == false)),
      (category1User, List(category1Process, category1Fragment)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ScenarioWithDetailsEntity[ScenarioGraph]]) =>
      implicit val loggedUser: LoggedUser = user

      val result = dBProcessService
        .getLatestRawProcessesWithDetails[ScenarioGraph](ScenarioQuery(isArchived = Some(false)))
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

    forAll(testingData) { (user: LoggedUser, expected: List[ScenarioWithDetailsEntity[ScenarioGraph]]) =>
      implicit val loggedUser: LoggedUser = user

      val result = dBProcessService
        .getLatestRawProcessesWithDetails[ScenarioGraph](ScenarioQuery(isArchived = Some(true)))
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

    forAll(testingData) { (user: LoggedUser, expected: List[ScenarioWithDetailsEntity[ScenarioGraph]]) =>
      implicit val implicitUser: LoggedUser = user
      val result = dBProcessService
        .getLatestRawProcessesWithDetails[ScenarioGraph](
          ScenarioQuery(isFragment = Some(true), isArchived = Some(false))
        )
        .futureValue
      result shouldBe expected
    }
  }

  it should "import process" in {
    val dBProcessService = createDbProcessService(processes)

    val categoryStringData =
      CanonicalProcessConverter
        .fromScenarioGraph(ProcessTestData.sampleScenarioGraph, category1Process.name)
        .asJson
        .spaces2
    val baseProcessData = CanonicalProcessConverter
      .fromScenarioGraph(ProcessTestData.sampleScenarioGraph, ProcessTestData.sampleProcessName)
      .asJson
      .spaces2

    val testingData = Table(
      ("processId", "data", "expected"),
      (
        category1Process.idWithName,
        categoryStringData,
        importSuccess(ProcessTestData.sampleScenarioGraph)
      ), // importing data with the same id
      (
        category1Process.idWithName,
        baseProcessData,
        importSuccess(ProcessTestData.sampleScenarioGraph)
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
      (idWithName: ProcessIdWithName, data: String, expected: XError[ScenarioGraphWithValidationResult]) =>
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
      scenarioGraph: ScenarioGraph
  ): Right[NuDesignerError, ScenarioGraphWithValidationResult] = {
    val nodeResults = Map(
      "sinkId"   -> NodeTypingData(Map("input" -> Unknown), Some(List.empty), Map.empty),
      "sourceId" -> NodeTypingData(Map.empty, Some(List.empty), Map.empty)
    )

    Right(
      ScenarioGraphWithValidationResult(
        scenarioGraph,
        ValidationResult.success.copy(nodeResults = nodeResults)
      )
    )
  }

  private def createDbProcessService(
      processes: List[ScenarioWithDetailsEntity[ScenarioGraph]] = Nil
  ): DBProcessService =
    new DBProcessService(
      processStateProvider = TestFactory.processStateProvider(),
      newProcessPreparers = TestFactory.newProcessPreparerByProcessingType,
      scenarioParametersServiceProvider = TestFactory.scenarioParametersServiceProvider,
      processResolverByProcessingType = TestFactory.processResolverByProcessingType,
      dbioRunner = TestFactory.newDummyDBIOActionRunner(),
      fetchingProcessRepository = MockFetchingProcessRepository.withProcessesDetails(processes),
      scenarioActionRepository = TestFactory.newDummyActionRepository(),
      scenarioActivityRepository = TestFactory.newDummyScenarioActivityRepository(),
      processRepository = TestFactory.newDummyWriteProcessRepository(),
      clock = Clock.systemUTC(),
    )

}

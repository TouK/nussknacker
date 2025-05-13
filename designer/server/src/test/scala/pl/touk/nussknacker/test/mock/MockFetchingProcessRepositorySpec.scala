package pl.touk.nussknacker.test.mock

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName._
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.base.it.ProcessesQueryEnrichments._
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory}
import pl.touk.nussknacker.test.utils.domain.TestProcessUtil.{createFragmentEntity, createScenarioEntity}
import pl.touk.nussknacker.ui.process.ScenarioQuery
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.repository.{ScenarioShapeFetchStrategy, ScenarioWithDetailsEntity}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, RealLoggedUser}

import scala.util.Try

class MockFetchingProcessRepositorySpec extends AnyFlatSpec with Matchers with ScalaFutures {

  import org.scalatest.prop.TableDrivenPropertyChecks._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val categoryMarketing   = "marketing"
  private val categoryFraud       = "fraud"
  private val categoryFraudSecond = "fraudSecond"
  private val categorySecret      = "secret"

  private val processingTypeStreaming = "streaming"
  private val processingTypeFraud     = "fraud"

  private val scenarioGraph = ProcessTestData.sampleScenarioGraph
  private val fragmentGraph = CanonicalProcessConverter.toScenarioGraph(ProcessTestData.sampleFragment)

  private val someVersion = VersionId(666L)

  private val marketingProcess = createScenarioEntity(
    "marketingProcess",
    category = categoryMarketing,
    lastAction = Some(Deploy),
    json = Some(scenarioGraph)
  )

  private val marketingFragment = createFragmentEntity(
    "marketingFragment",
    category = categoryMarketing,
    json = Some(fragmentGraph)
  )

  private val marketingArchivedFragment = createFragmentEntity(
    "marketingArchivedFragment",
    isArchived = true,
    category = categoryMarketing,
    lastAction = Some(Archive)
  )

  private val marketingArchivedProcess = createScenarioEntity(
    "marketingArchivedProcess",
    isArchived = true,
    category = categoryMarketing,
    lastAction = Some(Archive)
  )

  private val fraudProcess = createScenarioEntity(
    "fraudProcess",
    category = categoryFraud,
    processingType = processingTypeFraud,
    lastAction = Some(Deploy)
  )

  private val fraudArchivedProcess = createScenarioEntity(
    "fraudArchivedProcess",
    isArchived = true,
    category = categoryFraudSecond,
    processingType = processingTypeFraud,
    lastAction = Some(Archive),
    json = Some(scenarioGraph)
  )

  private val fraudFragment = createFragmentEntity(
    "fraudFragment",
    category = categoryFraud,
    processingType = processingTypeFraud,
    json = Some(scenarioGraph)
  )

  private val fraudArchivedFragment = createFragmentEntity(
    "fraudArchivedFragment",
    isArchived = true,
    category = categoryFraud,
    processingType = processingTypeFraud,
    json = Some(fragmentGraph)
  )

  private val fraudSecondProcess = createScenarioEntity(
    "fraudSecondProcess",
    category = categoryFraudSecond,
    processingType = processingTypeFraud,
    lastAction = Some(Cancel),
    json = Some(scenarioGraph)
  )

  private val fraudSecondFragment = createFragmentEntity(
    "fraudSecondFragment",
    category = categoryFraudSecond,
    processingType = processingTypeFraud
  )

  private val secretProcess  = createScenarioEntity("secretProcess", category = categorySecret)
  private val secretFragment = createFragmentEntity("secretFragment", category = categorySecret)

  private val secretArchivedFragment = createFragmentEntity(
    "secretArchivedFragment",
    isArchived = true,
    category = categorySecret,
    lastAction = Some(Archive)
  )

  private val secretArchivedProcess = createScenarioEntity(
    "secretArchivedProcess",
    isArchived = true,
    category = categorySecret,
    lastAction = Some(Archive),
    json = Some(scenarioGraph)
  )

  private val processes: List[ScenarioWithDetailsEntity[ScenarioGraph]] = List(
    marketingProcess,
    marketingArchivedProcess,
    marketingFragment,
    marketingArchivedFragment,
    fraudProcess,
    fraudArchivedProcess,
    fraudFragment,
    fraudArchivedFragment,
    fraudSecondProcess,
    fraudSecondFragment,
    secretProcess,
    secretArchivedProcess,
    secretFragment,
    secretArchivedFragment
  )

  private val admin: LoggedUser = TestFactory.adminUser()

  private val marketingUser: LoggedUser = RealLoggedUser(
    id = "1",
    username = "marketingUser",
    categoryPermissions = Map(categoryMarketing -> Set(Permission.Read))
  )

  private val fraudUser: LoggedUser = RealLoggedUser(
    id = "2",
    username = "fraudUser",
    categoryPermissions = Map(categoryFraud -> Set(Permission.Read), categoryFraudSecond -> Set(Permission.Read))
  )

  private val DisplayableShape = ScenarioShapeFetchStrategy.FetchScenarioGraph
  private val CanonicalShape   = ScenarioShapeFetchStrategy.FetchCanonical
  private val NoneShape        = ScenarioShapeFetchStrategy.NotFetch

  private val mockRepository = MockFetchingProcessRepository.withProcessesDetails(processes)

  it should "fetchProcessesDetails for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingProcess, fraudProcess, fraudSecondProcess, secretProcess)),
      (marketingUser, List(marketingProcess)),
      (fraudUser, List(fraudProcess, fraudSecondProcess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ScenarioWithDetailsEntity[ScenarioGraph]]) =>
      val result = mockRepository
        .fetchLatestProcessesDetails(ScenarioQuery.unarchivedProcesses)(DisplayableShape, user, global)
        .futureValue
      result shouldBe expected
    }
  }

  it should "fetchDeployedProcessesDetails for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingProcess, fraudProcess)),
      (marketingUser, List(marketingProcess)),
      (fraudUser, List(fraudProcess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ScenarioWithDetailsEntity[ScenarioGraph]]) =>
      val result = mockRepository
        .fetchLatestProcessesDetails(ScenarioQuery.deployed)(DisplayableShape, user, global)
        .futureValue
      result shouldBe expected
    }
  }

  it should "fetchProcessesDetails by names for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingProcess, fraudProcess, fraudSecondProcess, secretProcess)),
      (marketingUser, List(marketingProcess)),
      (fraudUser, List(fraudProcess, fraudSecondProcess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ScenarioWithDetailsEntity[ScenarioGraph]]) =>
      val names = processes.map(_.name)
      val result = mockRepository
        .fetchLatestProcessesDetails(
          ScenarioQuery(names = Some(names), isArchived = Some(false), isFragment = Some(false))
        )(DisplayableShape, user, global)
        .futureValue
      result shouldBe expected
    }
  }

  it should "fetchFragmentsDetails for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingFragment, fraudFragment, fraudSecondFragment, secretFragment)),
      (marketingUser, List(marketingFragment)),
      (fraudUser, List(fraudFragment, fraudSecondFragment)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ScenarioWithDetailsEntity[ScenarioGraph]]) =>
      val result = mockRepository
        .fetchLatestProcessesDetails(ScenarioQuery.unarchivedFragments)(DisplayableShape, user, global)
        .futureValue
      result shouldBe expected
    }
  }

  it should "fetchFragmentsDetails with each processing shape strategy" in {
    val fragments      = List(marketingFragment, fraudFragment, fraudSecondFragment, secretFragment)
    val mockRepository = MockFetchingProcessRepository.withProcessesDetails(fragments)

    val fragmentGraphs = List(marketingFragment, fraudFragment, fraudSecondFragment, secretFragment)
    val fragmentCanonicals =
      fragmentGraphs.map(p => p.copy(json = CanonicalProcessConverter.fromScenarioGraph(p.json, p.name)))
    val noneFragments = fragmentGraphs.map(p => p.copy(json = ()))

    mockRepository
      .fetchLatestProcessesDetails(ScenarioQuery.unarchivedFragments)(DisplayableShape, admin, global)
      .futureValue shouldBe fragmentGraphs
    mockRepository
      .fetchLatestProcessesDetails(ScenarioQuery.unarchivedFragments)(CanonicalShape, admin, global)
      .futureValue shouldBe fragmentCanonicals
    mockRepository
      .fetchLatestProcessesDetails(ScenarioQuery.unarchivedFragments)(NoneShape, admin, global)
      .futureValue shouldBe noneFragments
  }

  it should "fetchAllProcessesDetails for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (
        admin,
        List(
          marketingProcess,
          marketingFragment,
          fraudProcess,
          fraudFragment,
          fraudSecondProcess,
          fraudSecondFragment,
          secretProcess,
          secretFragment
        )
      ),
      (marketingUser, List(marketingProcess, marketingFragment)),
      (fraudUser, List(fraudProcess, fraudFragment, fraudSecondProcess, fraudSecondFragment)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ScenarioWithDetailsEntity[ScenarioGraph]]) =>
      val result = mockRepository
        .fetchLatestProcessesDetails(ScenarioQuery.unarchived)(DisplayableShape, user, global)
        .futureValue
      result shouldBe expected
    }
  }

  it should "fetchLatestProcessDetailsForProcessId for each user" in {
    val testingData = Table(
      ("user", "ProcessWithoutNodes", "expected"),
      (admin, secretFragment, Some(secretFragment)),
      (marketingUser, marketingProcess, Some(marketingProcess)),
      (marketingUser, marketingArchivedProcess, Some(marketingArchivedProcess)),
      (marketingUser, marketingArchivedFragment, Some(marketingArchivedFragment)),
      (fraudUser, marketingProcess, None),
    )

    forAll(testingData) {
      (
          user: LoggedUser,
          process: ScenarioWithDetailsEntity[ScenarioGraph],
          expected: Option[ScenarioWithDetailsEntity[ScenarioGraph]]
      ) =>
        val result = mockRepository
          .fetchLatestProcessDetailsForProcessId(process.processId)(DisplayableShape, user, global)
          .futureValue
        result shouldBe expected
    }
  }

  it should "fetchProcessDetailsForId for each user" in {
    val testingData = Table(
      ("user", "processId", "versionId", "expected"),
      (admin, secretFragment.processId, secretFragment.processVersionId, Some(secretFragment)),
      (admin, secretFragment.processId, someVersion, None),
      (marketingUser, marketingProcess.processId, marketingProcess.processVersionId, Some(marketingProcess)),
      (marketingUser, marketingProcess.processId, someVersion, None),
      (
        marketingUser,
        marketingArchivedProcess.processId,
        marketingArchivedProcess.processVersionId,
        Some(marketingArchivedProcess)
      ),
      (marketingUser, marketingArchivedProcess.processId, someVersion, None),
      (
        marketingUser,
        marketingArchivedFragment.processId,
        marketingArchivedFragment.processVersionId,
        Some(marketingArchivedFragment)
      ),
      (marketingUser, marketingArchivedFragment.processId, someVersion, None),
      (fraudUser, marketingProcess.processId, marketingProcess.processVersionId, None),
    )

    forAll(testingData) {
      (
          user: LoggedUser,
          processId: ProcessId,
          versionId: VersionId,
          expected: Option[ScenarioWithDetailsEntity[ScenarioGraph]]
      ) =>
        val result =
          mockRepository.fetchProcessDetailsForId(processId, versionId)(DisplayableShape, user, global).futureValue
        result shouldBe expected
    }
  }

  it should "return ProcessId for ProcessName" in {
    val data = processes.map(p => (p.name, Some(p.processId))) ++ List((ProcessName("not-exist-name"), None))

    data.foreach { case (processName, processId) =>
      val result = mockRepository.fetchProcessId(processName).futureValue
      result shouldBe processId
    }
  }

  it should "return ProcessName for ProcessId" in {
    val data = processes.map(p => (p.processId, Some(p.name))) ++ List((ProcessId(666), None))

    data.foreach { case (processId, processName) =>
      val result = mockRepository.fetchProcessName(processId).futureValue
      result shouldBe processName
    }
  }

  it should "return ProcessingType for ProcessId" in {
    val testingData = Table(
      ("user", "userProcesses"),
      (admin, processes),
      (marketingUser, List(marketingProcess, marketingArchivedProcess, marketingFragment, marketingArchivedFragment)),
      (
        fraudUser,
        List(
          fraudProcess,
          fraudFragment,
          fraudSecondProcess,
          fraudSecondFragment,
          fraudArchivedProcess,
          fraudArchivedFragment
        )
      ),
    )

    forAll(testingData) { (user: LoggedUser, userProcesses: List[ScenarioWithDetailsEntity[ScenarioGraph]]) =>
      processes.foreach(process => {
        val result         = mockRepository.fetchProcessingType(process.idWithName)(user, global)
        val processingType = Try(result.futureValue).toOption
        val expected       = if (userProcesses.contains(process)) Some(process.processingType) else None
        processingType shouldBe expected
      })
    }
  }

  it should "fetchProcesses for each user by mixed FetchQuery" in {
    // given
    val allProcessesQuery = ScenarioQuery()
    val allProcessesCategoryQuery =
      allProcessesQuery.withRawCategories(List(categoryMarketing, categoryFraud, categoryFraudSecond))
    val allProcessesCategoryTypesQuery = allProcessesCategoryQuery.withRawProcessingTypes(List(processingTypeStreaming))

    val processesQuery         = ScenarioQuery(isFragment = Some(false), isArchived = Some(false))
    val deployedProcessesQuery = processesQuery.copy(isDeployed = Some(true))
    val deployedProcessesCategoryQuery =
      deployedProcessesQuery.withRawCategories(List(categoryMarketing, categoryFraud, categoryFraudSecond))
    val deployedProcessesCategoryProcessingTypesQuery =
      deployedProcessesCategoryQuery.withRawProcessingTypes(List(processingTypeStreaming))

    val notDeployedProcessesQuery = processesQuery.copy(isDeployed = Some(false))
    val notDeployedProcessesCategoryQuery =
      notDeployedProcessesQuery.withRawCategories(List(categoryMarketing, categoryFraud, categoryFraudSecond))
    val notDeployedProcessesCategoryProcessingTypesQuery =
      notDeployedProcessesCategoryQuery.withRawProcessingTypes(List(processingTypeStreaming))

    val archivedQuery          = ScenarioQuery(isArchived = Some(true))
    val archivedProcessesQuery = archivedQuery.copy(isFragment = Some(false))
    val archivedProcessesCategoryQuery =
      archivedProcessesQuery.withRawCategories(List(categoryMarketing, categoryFraud, categoryFraudSecond))
    val archivedProcessesCategoryProcessingTypesQuery =
      archivedProcessesCategoryQuery.withRawProcessingTypes(List(processingTypeStreaming))

    val allFragmentsQuery = ScenarioQuery(isFragment = Some(true))
    val fragmentsQuery    = allFragmentsQuery.copy(isArchived = Some(false))
    val fragmentsCategoryQuery =
      fragmentsQuery.withRawCategories(List(categoryMarketing, categoryFraud, categoryFraudSecond))
    val fragmentsCategoryTypesQuery = fragmentsCategoryQuery.withRawProcessingTypes(List(processingTypeStreaming))

    val archivedFragmentsQuery = ScenarioQuery(isFragment = Some(true), isArchived = Some(true))
    val archivedFragmentsCategoryQuery =
      archivedFragmentsQuery.withRawCategories(List(categoryMarketing, categoryFraud, categoryFraudSecond))
    val archivedFragmentsCategoryTypesQuery =
      archivedFragmentsCategoryQuery.withRawProcessingTypes(List(processingTypeStreaming))

    // when
    val testingData = Table(
      ("user", "query", "expected"),
      // admin user
      (admin, allProcessesQuery, processes),
      (
        admin,
        allProcessesCategoryQuery,
        List(
          marketingProcess,
          marketingArchivedProcess,
          marketingFragment,
          marketingArchivedFragment,
          fraudProcess,
          fraudArchivedProcess,
          fraudFragment,
          fraudArchivedFragment,
          fraudSecondProcess,
          fraudSecondFragment
        )
      ),
      (
        admin,
        allProcessesCategoryTypesQuery,
        List(marketingProcess, marketingArchivedProcess, marketingFragment, marketingArchivedFragment)
      ),
      (admin, processesQuery, List(marketingProcess, fraudProcess, fraudSecondProcess, secretProcess)),
      (admin, deployedProcessesQuery, List(marketingProcess, fraudProcess)),
      (admin, deployedProcessesCategoryQuery, List(marketingProcess, fraudProcess)),
      (admin, deployedProcessesCategoryProcessingTypesQuery, List(marketingProcess)),
      (admin, notDeployedProcessesQuery, List(fraudSecondProcess, secretProcess)),
      (admin, notDeployedProcessesCategoryQuery, List(fraudSecondProcess)),
      (admin, notDeployedProcessesCategoryProcessingTypesQuery, List()),
      (
        admin,
        archivedQuery,
        List(
          marketingArchivedProcess,
          marketingArchivedFragment,
          fraudArchivedProcess,
          fraudArchivedFragment,
          secretArchivedProcess,
          secretArchivedFragment
        )
      ),
      (admin, archivedProcessesQuery, List(marketingArchivedProcess, fraudArchivedProcess, secretArchivedProcess)),
      (admin, archivedProcessesCategoryQuery, List(marketingArchivedProcess, fraudArchivedProcess)),
      (admin, archivedProcessesCategoryProcessingTypesQuery, List(marketingArchivedProcess)),
      (
        admin,
        allFragmentsQuery,
        List(
          marketingFragment,
          marketingArchivedFragment,
          fraudFragment,
          fraudArchivedFragment,
          fraudSecondFragment,
          secretFragment,
          secretArchivedFragment
        )
      ),
      (admin, fragmentsQuery, List(marketingFragment, fraudFragment, fraudSecondFragment, secretFragment)),
      (admin, fragmentsCategoryQuery, List(marketingFragment, fraudFragment, fraudSecondFragment)),
      (admin, fragmentsCategoryTypesQuery, List(marketingFragment)),
      (admin, archivedFragmentsQuery, List(marketingArchivedFragment, fraudArchivedFragment, secretArchivedFragment)),
      (admin, archivedFragmentsCategoryQuery, List(marketingArchivedFragment, fraudArchivedFragment)),
      (admin, archivedFragmentsCategoryTypesQuery, List(marketingArchivedFragment)),

      // marketing user
      (
        marketingUser,
        allProcessesQuery,
        List(marketingProcess, marketingArchivedProcess, marketingFragment, marketingArchivedFragment)
      ),
      (
        marketingUser,
        allProcessesCategoryQuery,
        List(marketingProcess, marketingArchivedProcess, marketingFragment, marketingArchivedFragment)
      ),
      (
        marketingUser,
        allProcessesCategoryTypesQuery,
        List(marketingProcess, marketingArchivedProcess, marketingFragment, marketingArchivedFragment)
      ),
      (marketingUser, processesQuery, List(marketingProcess)),
      (marketingUser, deployedProcessesQuery, List(marketingProcess)),
      (marketingUser, deployedProcessesCategoryQuery, List(marketingProcess)),
      (marketingUser, deployedProcessesCategoryProcessingTypesQuery, List(marketingProcess)),
      (marketingUser, notDeployedProcessesQuery, List()),
      (marketingUser, notDeployedProcessesCategoryQuery, List()),
      (marketingUser, notDeployedProcessesCategoryProcessingTypesQuery, List()),
      (marketingUser, archivedQuery, List(marketingArchivedProcess, marketingArchivedFragment)),
      (marketingUser, archivedProcessesQuery, List(marketingArchivedProcess)),
      (marketingUser, archivedProcessesCategoryQuery, List(marketingArchivedProcess)),
      (marketingUser, archivedProcessesCategoryProcessingTypesQuery, List(marketingArchivedProcess)),
      (marketingUser, allFragmentsQuery, List(marketingFragment, marketingArchivedFragment)),
      (marketingUser, fragmentsQuery, List(marketingFragment)),
      (marketingUser, fragmentsCategoryQuery, List(marketingFragment)),
      (marketingUser, fragmentsCategoryTypesQuery, List(marketingFragment)),
      (marketingUser, archivedFragmentsQuery, List(marketingArchivedFragment)),
      (marketingUser, archivedFragmentsCategoryQuery, List(marketingArchivedFragment)),
      (marketingUser, archivedFragmentsCategoryTypesQuery, List(marketingArchivedFragment)),

      // fraud user
      (
        fraudUser,
        allProcessesQuery,
        List(
          fraudProcess,
          fraudArchivedProcess,
          fraudFragment,
          fraudArchivedFragment,
          fraudSecondProcess,
          fraudSecondFragment
        )
      ),
      (
        fraudUser,
        allProcessesCategoryQuery,
        List(
          fraudProcess,
          fraudArchivedProcess,
          fraudFragment,
          fraudArchivedFragment,
          fraudSecondProcess,
          fraudSecondFragment
        )
      ),
      (fraudUser, allProcessesCategoryTypesQuery, List()),
      (fraudUser, processesQuery, List(fraudProcess, fraudSecondProcess)),
      (fraudUser, deployedProcessesQuery, List(fraudProcess)),
      (fraudUser, deployedProcessesCategoryQuery, List(fraudProcess)),
      (fraudUser, deployedProcessesCategoryProcessingTypesQuery, List()),
      (fraudUser, notDeployedProcessesQuery, List(fraudSecondProcess)),
      (fraudUser, notDeployedProcessesCategoryQuery, List(fraudSecondProcess)),
      (fraudUser, notDeployedProcessesCategoryProcessingTypesQuery, List()),
      (fraudUser, archivedQuery, List(fraudArchivedProcess, fraudArchivedFragment)),
      (fraudUser, archivedProcessesQuery, List(fraudArchivedProcess)),
      (fraudUser, archivedProcessesCategoryQuery, List(fraudArchivedProcess)),
      (fraudUser, archivedProcessesCategoryProcessingTypesQuery, List()),
      (fraudUser, allFragmentsQuery, List(fraudFragment, fraudArchivedFragment, fraudSecondFragment)),
      (fraudUser, fragmentsQuery, List(fraudFragment, fraudSecondFragment)),
      (fraudUser, fragmentsCategoryQuery, List(fraudFragment, fraudSecondFragment)),
      (fraudUser, fragmentsCategoryTypesQuery, List()),
      (fraudUser, archivedFragmentsQuery, List(fraudArchivedFragment)),
      (fraudUser, archivedFragmentsQuery, List(fraudArchivedFragment)),
      (fraudUser, archivedFragmentsCategoryQuery, List(fraudArchivedFragment)),
      (fraudUser, archivedFragmentsCategoryTypesQuery, List()),
    )

    forAll(testingData) {
      (user: LoggedUser, query: ScenarioQuery, expected: List[ScenarioWithDetailsEntity[ScenarioGraph]]) =>
        val result = mockRepository.fetchLatestProcessesDetails(query)(DisplayableShape, user, global).futureValue

        // then
        result shouldBe expected
    }
  }

}

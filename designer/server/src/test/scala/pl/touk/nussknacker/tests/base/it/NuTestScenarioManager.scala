package pl.touk.nussknacker.tests.base.it

import org.scalatest.concurrent.ScalaFutures
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.tests.TestFactory._
import pl.touk.nussknacker.tests.base.db.WithTestDb
import pl.touk.nussknacker.tests.{ConfigWithScalaVersion, ProcessTestData, TestFactory}
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// note: intention of this trait is to replace NuResourcesTest. In the future, in this trait we need to have only methods
//       that are supposed to configure Nussknacker DB state (using repositories or through HTTP API).
trait NuTestScenarioManager extends ScalaFutures {
  this: WithTestDb =>

  private implicit val user: LoggedUser                    = TestFactory.adminUser("user")
  private val dbioRunner: DBIOActionRunner                 = newDBIOActionRunner(testDbRef)
  private val actionRepository: DbProcessActionRepository  = newActionProcessRepository(testDbRef)
  private val writeScenarioRepository: DBProcessRepository = newWriteProcessRepository(testDbRef)
  protected val futureFetchingScenarioRepository: DBFetchingProcessRepository[Future] =
    newFutureFetchingScenarioRepository(testDbRef)

  protected val scenarioCategoryService: ProcessCategoryService =
    TestFactory.createCategoryService(ConfigWithScalaVersion.TestsConfig)

  protected def createSavedScenario(
      scenario: CanonicalProcess,
      category: TestCategory,
      isFragment: Boolean = false
  ): ProcessId = {
    saveAndGetId(scenario, category, isFragment).futureValue
  }

  def createDeployedExampleScenario(scenarioName: ProcessName, category: TestCategory): ProcessId = {
    (for {
      id <- prepareValidScenario(scenarioName, category)
      _  <- prepareDeploy(id, processingTypeBy(category))
    } yield id).futureValue
  }

  def createDeployedScenario(
      scenario: CanonicalProcess,
      category: TestCategory,
      isFragment: Boolean = false
  ): ProcessId = {
    (for {
      id <- Future(createSavedScenario(scenario, category, isFragment))
      _  <- prepareDeploy(id, processingTypeBy(category))
    } yield id).futureValue
  }

  def createDeployedCanceledExampleScenario(scenarioName: ProcessName, category: TestCategory): ProcessId = {
    (for {
      id <- prepareValidScenario(scenarioName, category)
      _  <- prepareDeploy(id, processingTypeBy(category))
      _  <- prepareCancel(id)
    } yield id).futureValue
  }

  private def prepareDeploy(scenarioId: ProcessId, processingType: TestProcessingType): Future[_] = {
    val actionType = ProcessActionType.Deploy
    val comment    = DeploymentComment.unsafe("Deploy comment").toComment(actionType)
    dbioRunner.run(
      actionRepository.addInstantAction(
        scenarioId,
        VersionId.initialVersionId,
        actionType,
        Some(comment),
        Some(processingType.stringify)
      )
    )
  }

  private def prepareCancel(scenarioId: ProcessId): Future[_] = {
    val actionType = ProcessActionType.Cancel
    val comment    = DeploymentComment.unsafe("Cancel comment").toComment(actionType)
    dbioRunner.run(
      actionRepository.addInstantAction(scenarioId, VersionId.initialVersionId, actionType, Some(comment), None)
    )
  }

  private def prepareValidScenario(
      scenarioName: ProcessName,
      category: TestCategory,
  ): Future[ProcessId] = {
    val validScenario = ProcessTestData.sampleScenario
    val withNameSet   = validScenario.withProcessName(scenarioName)
    saveAndGetId(withNameSet, category, isFragment = false)
  }

  private def saveAndGetId(
      scenario: CanonicalProcess,
      category: TestCategory,
      isFragment: Boolean
  ): Future[ProcessId] = {
    val scenarioName = scenario.name
    val action =
      CreateProcessAction(
        scenarioName,
        category.stringify,
        scenario,
        processingTypeBy(category).stringify,
        isFragment,
        forwardedUserName = None
      )
    for {
      _  <- dbioRunner.runInTransaction(writeScenarioRepository.saveNewProcess(action))
      id <- futureFetchingScenarioRepository.fetchProcessId(scenarioName).map(_.get)
    } yield id
  }

}

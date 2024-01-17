package pl.touk.nussknacker.ui.api.helpers

import db.util.DBIOActionInstances.DB
import org.scalatest.concurrent.ScalaFutures
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{
  newActionProcessRepository,
  newDBIOActionRunner,
  newFutureFetchingScenarioRepository,
  newWriteProcessRepository
}
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.Streaming
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// note: intention of this trait is to replace NuResourcesTest. In the future, in this trait we need to have only methods
//       that are supposed to configure Nussknacker DB state (using repositories or through HTTP API).
trait NuTestScenarioManager extends ScalaFutures {
  this: WithTestDb =>

  private implicit val user: LoggedUser                       = TestFactory.adminUser("user")
  private val dbioRunner: DBIOActionRunner                    = newDBIOActionRunner(testDbRef)
  private val actionRepository: DbProcessActionRepository[DB] = newActionProcessRepository(testDbRef)
  private val writeScenarioRepository: DBProcessRepository    = newWriteProcessRepository(testDbRef)
  protected val futureFetchingScenarioRepository: DBFetchingProcessRepository[Future] =
    newFutureFetchingScenarioRepository(testDbRef)

  protected implicit val scenarioCategoryService: ProcessCategoryService =
    TestFactory.createCategoryService(ConfigWithScalaVersion.TestsConfig)

  protected def createSavedScenario(
      scenario: CanonicalProcess
  ): ProcessId = {
    saveAndGetId(scenario, scenario.metaData.isFragment).futureValue
  }

  def createDeployedExampleScenario(scenarioName: ProcessName): ProcessId = {
    (for {
      id <- prepareValidScenario(scenarioName)
      _  <- prepareDeploy(id)
    } yield id).futureValue
  }

  def createDeployedScenario(
      scenario: CanonicalProcess
  ): ProcessId = {
    (for {
      id <- Future(createSavedScenario(scenario))
      _  <- prepareDeploy(id)
    } yield id).futureValue
  }

  def createDeployedCanceledExampleScenario(scenarioName: ProcessName): ProcessId = {
    (for {
      id <- prepareValidScenario(scenarioName)
      _  <- prepareDeploy(id)
      _  <- prepareCancel(id)
    } yield id).futureValue
  }

  private def prepareDeploy(scenarioId: ProcessId): Future[_] = {
    val actionType = ProcessActionType.Deploy
    val comment    = DeploymentComment.unsafe("Deploy comment").toComment(actionType)
    dbioRunner.run(
      actionRepository.addInstantAction(
        scenarioId,
        VersionId.initialVersionId,
        actionType,
        Some(comment),
        Some(Streaming)
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
      scenarioName: ProcessName
  ): Future[ProcessId] = {
    val validScenario = ProcessTestData.sampleScenario
    val withNameSet   = validScenario.withProcessName(scenarioName)
    saveAndGetId(withNameSet, isFragment = false)
  }

  private def saveAndGetId(
      scenario: CanonicalProcess,
      isFragment: Boolean
  ): Future[ProcessId] = {
    val scenarioName = scenario.name
    val action =
      CreateProcessAction(
        scenarioName,
        TestCategories.Category1,
        scenario,
        TestProcessingTypes.Streaming,
        isFragment,
        forwardedUserName = None
      )
    for {
      _  <- dbioRunner.runInTransaction(writeScenarioRepository.saveNewProcess(action))
      id <- futureFetchingScenarioRepository.fetchProcessId(scenarioName).map(_.get)
    } yield id
  }

}

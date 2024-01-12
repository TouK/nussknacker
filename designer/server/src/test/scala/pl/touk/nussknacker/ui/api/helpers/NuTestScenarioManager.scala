package pl.touk.nussknacker.ui.api.helpers

import db.util.DBIOActionInstances.DB
import org.scalatest.concurrent.ScalaFutures
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessingType, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.api.helpers.TestCategories.Category1
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
      scenario: CanonicalProcess,
      category: String,
      processingType: ProcessingType
  ): ProcessId = {
    saveAndGetId(scenario, category, scenario.metaData.isFragment, processingType).futureValue
  }

  def createDeployedExampleScenario(scenarioName: String, category: String = Category1): ProcessId = {
    (for {
      id <- prepareValidScenario(scenarioName, category, isFragment = false)
      _  <- prepareDeploy(id)
    } yield id).futureValue
  }

  def createDeployedScenario(
      scenario: CanonicalProcess,
      category: String = Category1,
      processingType: String = Streaming
  ): ProcessId = {
    (for {
      id <- Future(createSavedScenario(scenario, category, processingType))
      _  <- prepareDeploy(id)
    } yield id).futureValue
  }

  def createDeployedCanceledExampleScenario(scenarioName: String, category: String = Category1): ProcessId = {
    (for {
      id <- prepareValidScenario(scenarioName, category, isFragment = false)
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
      scenarioName: String,
      category: String,
      isFragment: Boolean
  ): Future[ProcessId] = {
    val validScenario: CanonicalProcess = if (isFragment) SampleFragment.fragment else SampleScenario.scenario
    val withNameSet                     = validScenario.copy(metaData = validScenario.metaData.copy(id = scenarioName))
    saveAndGetId(withNameSet, category, isFragment)
  }

  private def saveAndGetId(
      scenario: CanonicalProcess,
      category: String,
      isFragment: Boolean,
      processingType: ProcessingType = Streaming
  ): Future[ProcessId] = {
    val scenarioName = scenario.name
    val action =
      CreateProcessAction(scenarioName, category, scenario, processingType, isFragment, forwardedUserName = None)
    for {
      _  <- dbioRunner.runInTransaction(writeScenarioRepository.saveNewProcess(action))
      id <- futureFetchingScenarioRepository.fetchProcessId(scenarioName).map(_.get)
    } yield id
  }

}

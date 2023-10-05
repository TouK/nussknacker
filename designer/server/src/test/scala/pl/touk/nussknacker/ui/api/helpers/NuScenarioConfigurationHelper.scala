package pl.touk.nussknacker.ui.api.helpers

import db.util.DBIOActionInstances.DB
import org.scalatest.concurrent.ScalaFutures
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.api.helpers.TestCategories.TestCat
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{
  newActionProcessRepository,
  newDBIOActionRunner,
  newFutureFetchingProcessRepository,
  newWriteProcessRepository
}
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.Streaming
import pl.touk.nussknacker.ui.process.{ConfigProcessCategoryService, ProcessCategoryService}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// note: intention of this trait is to replace NuResourcesTest. In the future, in this trait we need to have only methods
//       that are supposed to configure Nussknacker DB state (using repositories or through HTTP API).
trait NuScenarioConfigurationHelper extends ScalaFutures {
  this: WithTestDb =>

  private implicit val user: LoggedUser = TestFactory.adminUser("user")

  private val dbioRunner: DBIOActionRunner                    = newDBIOActionRunner(testDbRef)
  private val actionRepository: DbProcessActionRepository[DB] = newActionProcessRepository(testDbRef)
  private val writeProcessRepository: DBProcessRepository     = newWriteProcessRepository(testDbRef)
  protected val futureFetchingProcessRepository: DBFetchingProcessRepository[Future] =
    newFutureFetchingProcessRepository(testDbRef)

  protected implicit val processCategoryService: ProcessCategoryService =
    new ConfigProcessCategoryService(ConfigWithScalaVersion.TestsConfig)

  def createDeployedProcess(processName: ProcessName, category: String = TestCat): ProcessId = {
    (for {
      id <- prepareValidProcess(processName, category, isFragment = false)
      _  <- prepareDeploy(id)
    } yield id).futureValue
  }

  def createDeployedCanceledProcess(processName: ProcessName, category: String = TestCat): ProcessId = {
    (for {
      id <- prepareValidProcess(processName, category, isFragment = false)
      _  <- prepareDeploy(id)
      _  <- prepareCancel(id)
    } yield id).futureValue
  }

  def prepareDeploy(id: ProcessId): Future[_] = {
    val actionType = ProcessActionType.Deploy
    val comment    = DeploymentComment.unsafe("Deploy comment").toComment(actionType)
    dbioRunner.run(
      actionRepository.addInstantAction(id, VersionId.initialVersionId, actionType, Some(comment), Some(Streaming))
    )
  }

  def prepareCancel(id: ProcessId): Future[_] = {
    val actionType = ProcessActionType.Cancel
    val comment    = DeploymentComment.unsafe("Cancel comment").toComment(actionType)
    dbioRunner.run(actionRepository.addInstantAction(id, VersionId.initialVersionId, actionType, Some(comment), None))
  }

  private def prepareValidProcess(
      processName: ProcessName,
      category: String,
      isFragment: Boolean
  ): Future[ProcessId] = {
    val validProcess: CanonicalProcess = if (isFragment) SampleFragment.fragment else SampleProcess.process
    val withNameSet = validProcess.copy(metaData = validProcess.metaData.copy(id = processName.value))
    saveAndGetId(withNameSet, category, isFragment)
  }

  private def saveAndGetId(
      process: CanonicalProcess,
      category: String,
      isFragment: Boolean,
      processingType: ProcessingType = Streaming
  ): Future[ProcessId] = {
    val processName = ProcessName(process.id)
    val action =
      CreateProcessAction(processName, category, process, processingType, isFragment, forwardedUserName = None)
    for {
      _  <- dbioRunner.runInTransaction(writeProcessRepository.saveNewProcess(action))
      id <- futureFetchingProcessRepository.fetchProcessId(processName).map(_.get)
    } yield id
  }

}

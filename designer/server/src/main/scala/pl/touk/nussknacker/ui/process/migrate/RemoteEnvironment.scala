package pl.touk.nussknacker.ui.process.migrate

import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessName, ScenarioVersion, VersionId}
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetailsForMigrations
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.ui.{FatalError, NuDesignerError}
import pl.touk.nussknacker.ui.process.VersionsWithDifferencesService.VersionsWithDifferences
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ScenarioGraphComparator.Difference

import scala.concurrent.{ExecutionContext, Future}

final case class RemoteScenarioVersions(versions: List[ScenarioVersion], remoteUnavailable: Boolean)

trait RemoteEnvironment {

  def environmentId: String

  def compare(
      localGraph: ScenarioGraph,
      remoteProcessName: ProcessName,
      remoteProcessVersion: Option[VersionId]
  ): Future[Either[NuDesignerError, Map[String, Difference]]]

  def processVersions(processName: ProcessName): Future[RemoteScenarioVersions]

  /**
   * The remote does the comparing, so this sends one graph and receives a summary - including its own
   * versions' comments - rather than pulling its full graphs across the network. `None` means the answer
   * could not be obtained at all.
   */
  def versionsWithDifferences(
      processName: ProcessName,
      scenarioGraph: ScenarioGraph,
      limit: Int
  ): Future[Option[VersionsWithDifferences]]

  def migrate(
      processingMode: ProcessingMode,
      engineSetupName: EngineSetupName,
      processCategory: String,
      scenarioLabels: List[String],
      scenarioGraph: ScenarioGraph,
      localScenarioVersionId: VersionId,
      processName: ProcessName,
      isFragment: Boolean
  )(
      implicit loggedUser: LoggedUser
  ): Future[Either[NuDesignerError, Unit]]

  // TODO This method is used by an external project. We should move it to some api module
  def testMigration(
      processToInclude: ScenarioWithDetailsForMigrations => Boolean = _ => true,
      batchingExecutionContext: ExecutionContext
  )(
      implicit loggedUser: LoggedUser
  ): Future[Either[NuDesignerError, List[TestMigrationResult]]]

}

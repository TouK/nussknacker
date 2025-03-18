package pl.touk.nussknacker.ui.process.migrate

import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessName, ScenarioVersion, VersionId}
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetailsForMigrations
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.ui.{FatalError, NuDesignerError}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ScenarioGraphComparator.Difference

import scala.concurrent.{ExecutionContext, Future}

trait RemoteEnvironment {

  def environmentId: String

  def compare(
      localGraph: ScenarioGraph,
      remoteProcessName: ProcessName,
      remoteProcessVersion: Option[VersionId]
  )(
      implicit ec: ExecutionContext
  ): Future[Either[NuDesignerError, Map[String, Difference]]]

  def processVersions(processName: ProcessName)(implicit ec: ExecutionContext): Future[List[ScenarioVersion]]

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
      implicit ec: ExecutionContext,
      loggedUser: LoggedUser
  ): Future[Either[NuDesignerError, Unit]]

  // TODO This method is used by an external project. We should move it to some api module
  def testMigration(
      processToInclude: ScenarioWithDetailsForMigrations => Boolean = _ => true,
      batchingExecutionContext: ExecutionContext
  )(
      implicit ec: ExecutionContext,
      loggedUser: LoggedUser
  ): Future[Either[NuDesignerError, List[TestMigrationResult]]]

}

final case class MigrationValidationError(errors: ValidationErrors)
    extends FatalError({
      val messages = errors.globalErrors.map(_.error.message) ++
        errors.processPropertiesErrors.map(_.message) ++ errors.invalidNodes.map { case (node, nerror) =>
          s"$node - ${nerror.map(_.message).mkString(", ")}"
        }
      s"Cannot migrate, following errors occurred: ${messages.mkString(", ")}"
    })

final case class MigrationToArchivedError(processName: ProcessName, environment: String)
    extends FatalError(
      s"Cannot migrate, scenario $processName is archived on $environment. You have to unarchive scenario on $environment in order to migrate."
    )

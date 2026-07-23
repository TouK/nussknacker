package pl.touk.nussknacker.ui.process.migrate

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessName, ScenarioVersion, VersionId}
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetailsForMigrations
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.ui.{FatalError, NuDesignerError}
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.ScenarioActivity
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ScenarioGraphComparator.Difference

import scala.concurrent.{ExecutionContext, Future}

@JsonCodec final case class VersionGraph(versionId: VersionId, scenarioGraph: ScenarioGraph)

@JsonCodec final case class VersionGraphs(versions: List[VersionGraph])

trait RemoteEnvironment {

  def environmentId: String

  def compare(
      localGraph: ScenarioGraph,
      remoteProcessName: ProcessName,
      remoteProcessVersion: Option[VersionId]
  ): Future[Either[NuDesignerError, Map[String, Difference]]]

  // Implementations must not fail this Future to signal that the remote environment is unreachable or
  // doesn't support this call (e.g. an older Nussknacker version) - callers treat an empty List the same
  // as "no versions", so a failed Future would surface as a 500 instead.
  def processVersions(processName: ProcessName): Future[List[ScenarioVersion]]

  // Same must-not-fail contract as processVersions: a version missing from the result is treated as
  // "couldn't be fetched", not as an error.
  def scenarioGraphsForVersions(
      processName: ProcessName,
      versionIds: List[VersionId]
  ): Future[Map[VersionId, ScenarioGraph]]

  // Same must-not-fail contract as processVersions.
  def activities(processName: ProcessName): Future[List[ScenarioActivity]]

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

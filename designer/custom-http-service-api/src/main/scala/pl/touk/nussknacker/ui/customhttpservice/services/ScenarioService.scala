package pl.touk.nussknacker.ui.customhttpservice.services

import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ScenarioActionName, UserName}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.ui.customhttpservice.services.ScenarioService._
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.net.URI
import java.time.Instant
import scala.language.higherKinds

trait ScenarioService[M[_]] {

  def getLatestProcessesWithDetails(query: ScenarioQuery)(
      implicit user: LoggedUser
  ): M[List[ScenarioWithDetails]]

  def getLatestVersionForProcesses(query: ScenarioQuery, scenarioVersionQuery: ScenarioVersionQuery)(
      implicit user: LoggedUser
  ): M[Map[ProcessId, ScenarioVersionMetadata]]

}

object ScenarioService {

  final case class ScenarioWithDetails(
      name: ProcessName,
      processId: Option[ProcessId],
      processVersionId: VersionId,
      isLatestVersion: Boolean,
      description: Option[String],
      isArchived: Boolean,
      isFragment: Boolean,
      processingType: ProcessingType,
      processCategory: String,
      processingMode: ProcessingMode,
      engineSetupName: EngineSetupName,
      modifiedAt: Instant,
      modifiedBy: String,
      createdAt: Instant,
      createdBy: String,
      labels: List[String],
      // Actions are deprecated
      lastDeployedAction: Option[ProcessAction],
      lastStateAction: Option[ProcessAction],
      lastAction: Option[ProcessAction],
      //
      modelVersion: Option[Int],
      state: Option[ScenarioStatus],
  )

  final case class ScenarioStatus(
      status: String,
      visibleActions: List[ScenarioActionName],
      allowedActions: List[ScenarioActionName],
      actionTooltips: Map[ScenarioActionName, String],
      icon: URI,
      tooltip: String,
      description: String,
  )

  final case class ScenarioQuery(
      isFragment: Option[Boolean] = None,
      isArchived: Option[Boolean] = None,
      isDeployed: Option[Boolean] = None,
      categories: Option[Seq[String]] = None,
      processingTypes: Option[Seq[String]] = None,
      names: Option[Seq[ProcessName]] = None,
  )

  final case class ScenarioVersionQuery(
      excludedUserNames: Option[Seq[String]] = None,
  )

  final case class ScenarioVersionMetadata(
      versionId: VersionId,
      createdAt: Instant,
      createdByUser: UserName,
  )

}

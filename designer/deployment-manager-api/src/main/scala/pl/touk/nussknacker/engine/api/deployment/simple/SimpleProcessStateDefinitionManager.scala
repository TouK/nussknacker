package pl.touk.nussknacker.engine.api.deployment.simple

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.{ProcessActionType, defaultActions}
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.{ProblemStateStatus, statusActionsPF}
import pl.touk.nussknacker.engine.api.deployment.{ProcessState, ProcessStateDefinitionManager, StateDefinitionDetails, StateStatus}
import pl.touk.nussknacker.engine.api.process.VersionId

/**
  * Base [[ProcessStateDefinitionManager]] with basic state definitions and state transitions.
  * Provides methods to handle erroneous edge cases.
  * @see [[SimpleStateStatus]]
  */
object SimpleProcessStateDefinitionManager extends ProcessStateDefinitionManager {

  override def statusActions(stateStatus: StateStatus): List[ProcessActionType] =
    statusActionsPF.applyOrElse(stateStatus, (_: StateStatus) => defaultActions)

  override def statusDescription(stateStatus: StateStatus): Option[String] = stateStatus match {
    case _@ProblemStateStatus(message, _) => Some(message)
    case _ => SimpleStateStatus.definitions(stateStatus.name).description
  }

  override def statusTooltip(stateStatus: StateStatus): Option[String] = stateStatus match {
    case _@ProblemStateStatus(message, _) => Some(message)
    case _ => SimpleStateStatus.definitions(stateStatus.name).tooltip
  }

  override def stateDefinitions: Map[StatusName, StateDefinitionDetails] =
    SimpleStateStatus.definitions

  def errorFailedToGet: ProcessState =
    processState(failedToGet)

  def errorShouldBeRunningState(deployedVersionId: VersionId, user: String): ProcessState =
    processState(ProblemStateStatus.shouldBeRunning(deployedVersionId, user))

  def errorMismatchDeployedVersionState(deployedVersionId: VersionId, exceptedVersionId: VersionId, user: String): ProcessState =
    processState(ProblemStateStatus.mismatchDeployedVersion(deployedVersionId, exceptedVersionId, user))

  def warningShouldNotBeRunningState(deployed: Boolean): ProcessState =
    processState(ProblemStateStatus.shouldNotBeRunning(deployed))

  def warningMissingDeployedVersionState(exceptedVersionId: VersionId, user: String): ProcessState =
    processState(ProblemStateStatus.missingDeployedVersion(exceptedVersionId, user))

  def warningProcessWithoutActionState: ProcessState =
    processState(ProblemStateStatus.processWithoutAction)
}

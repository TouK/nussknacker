package pl.touk.nussknacker.ui.db.entity

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.db.NuJdbcProfile
import pl.touk.nussknacker.engine.api.deployment.{
  DeploymentStatusName,
  ProcessActionId,
  ProcessActionState,
  ScenarioActionName,
  ScenarioActivityId
}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionState.ProcessActionState
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.newdeployment.DeploymentId

import java.util.UUID

trait BaseEntityFactory extends LazyLogging {

  protected val profile: NuJdbcProfile
  import profile.apiWithEnforcedSchema._

  implicit def processIdMapping: BaseColumnType[ProcessId] =
    MappedColumnType.base[ProcessId, Long](_.value, ProcessId.apply)

  implicit def processNameMapping: BaseColumnType[ProcessName] =
    MappedColumnType.base[ProcessName, String](_.value, ProcessName.apply)

  implicit def versionIdMapping: BaseColumnType[VersionId] =
    MappedColumnType.base[VersionId, Long](_.value, VersionId(_))

  implicit def processActionIdMapping: BaseColumnType[ProcessActionId] =
    MappedColumnType.base[ProcessActionId, UUID](_.value, ProcessActionId.apply)

  implicit def scenarioActionName: BaseColumnType[ScenarioActionName] =
    MappedColumnType.base[ScenarioActionName, String](_.toString, ScenarioActionName.withNameWithFallback)

  implicit def processActionState: BaseColumnType[ProcessActionState] =
    MappedColumnType.base[ProcessActionState, String](_.toString, ProcessActionState.withName)

  protected implicit def deploymentIdMapping: BaseColumnType[DeploymentId] =
    MappedColumnType.base[DeploymentId, UUID](_.value, DeploymentId.apply)

  implicit def deploymentStatusName: BaseColumnType[DeploymentStatusName] =
    MappedColumnType.base[DeploymentStatusName, String](_.value, DeploymentStatusName.apply)

  implicit def scenarioActivityIdMapper: BaseColumnType[ScenarioActivityId] =
    MappedColumnType.base[ScenarioActivityId, UUID](_.value, ScenarioActivityId.apply)

}

package pl.touk.nussknacker.ui.db.entity

import pl.touk.nussknacker.engine.api.deployment.ProcessActionState.ProcessActionState
import pl.touk.nussknacker.engine.api.deployment.{ProcessActionId, ProcessActionState, ScenarioActionName}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import slick.ast.BaseTypedType
import slick.jdbc.{JdbcProfile, JdbcType}

import java.util.UUID

trait BaseEntityFactory {
  protected val profile: JdbcProfile
  import profile.api._

  implicit def processIdMapping: BaseColumnType[ProcessId] =
    MappedColumnType.base[ProcessId, Long](_.value, ProcessId.apply)

  implicit def processNameMapping: BaseColumnType[ProcessName] =
    MappedColumnType.base[ProcessName, String](_.value, ProcessName.apply)

  implicit def versionIdMapping: BaseColumnType[VersionId] =
    MappedColumnType.base[VersionId, Long](_.value, VersionId(_))

  implicit def processActionIdMapping: BaseColumnType[ProcessActionId] =
    MappedColumnType.base[ProcessActionId, UUID](_.value, ProcessActionId.apply)

  implicit def scenarioActionName: JdbcType[ScenarioActionName] with BaseTypedType[ScenarioActionName] =
    MappedColumnType.base[ScenarioActionName, String](_.toString, ScenarioActionName.apply)

  implicit def processActionState: JdbcType[ProcessActionState] with BaseTypedType[ProcessActionState] =
    MappedColumnType.base[ProcessActionState, String](_.toString, ProcessActionState.withName)

}

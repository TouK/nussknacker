package pl.touk.nussknacker.ui.db.entity

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import slick.ast.BaseTypedType
import slick.jdbc.{JdbcProfile, JdbcType}

trait BaseEntityFactory {
  protected val profile: JdbcProfile
  import profile.api._

  implicit def procesIdMapping: BaseColumnType[ProcessId] =
    MappedColumnType.base[ProcessId, Long](_.value, ProcessId.apply)

  implicit def versionIdMapping: BaseColumnType[VersionId] =
    MappedColumnType.base[VersionId, Long](_.value, VersionId(_))

  implicit def deploymentMapper: JdbcType[ProcessActionType] with BaseTypedType[ProcessActionType] =
    MappedColumnType.base[ProcessActionType, String](_.toString, ProcessActionType.withName)

}

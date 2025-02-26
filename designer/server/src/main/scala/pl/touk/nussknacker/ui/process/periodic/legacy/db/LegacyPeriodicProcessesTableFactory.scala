package pl.touk.nussknacker.ui.process.periodic.legacy.db

import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessId
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.time.LocalDateTime
import java.util.UUID

trait LegacyPeriodicProcessesTableFactory {

  protected val profile: JdbcProfile

  import profile.api._

  implicit val periodicProcessIdMapping: BaseColumnType[PeriodicProcessId] =
    MappedColumnType.base[PeriodicProcessId, Long](_.value, PeriodicProcessId.apply)

  implicit val processNameMapping: BaseColumnType[ProcessName] =
    MappedColumnType.base[ProcessName, String](_.value, ProcessName.apply)

  implicit val versionIdMapping: BaseColumnType[VersionId] =
    MappedColumnType.base[VersionId, Long](_.value, VersionId.apply)

  implicit val processActionIdMapping: BaseColumnType[ProcessActionId] =
    MappedColumnType.base[ProcessActionId, UUID](_.value, ProcessActionId.apply)

  abstract class PeriodicProcessesTable[ENTITY <: PeriodicProcessEntity](tag: Tag)
      extends Table[ENTITY](tag, "periodic_processes") {

    def id: Rep[PeriodicProcessId] = column[PeriodicProcessId]("id", O.PrimaryKey, O.AutoInc)

    def processName: Rep[ProcessName] = column[ProcessName]("process_name", NotNull)

    def processVersionId: Rep[VersionId] = column[VersionId]("process_version_id", NotNull)

    def processingType: Rep[String] = column[String]("processing_type", NotNull)

    def jarFileName: Rep[String] = column[String]("jar_file_name", NotNull)

    def scheduleProperty: Rep[String] = column[String]("schedule_property", NotNull)

    def active: Rep[Boolean] = column[Boolean]("active", NotNull)

    def createdAt: Rep[LocalDateTime] = column[LocalDateTime]("created_at", NotNull)

    def processActionId: Rep[Option[ProcessActionId]] = column[Option[ProcessActionId]]("process_action_id")

  }

  class PeriodicProcessesWithJsonTable(tag: Tag) extends PeriodicProcessesTable[PeriodicProcessEntityWithJson](tag) {

    def processJson: Rep[String] = column[String]("process_json", NotNull)

    def inputConfigDuringExecutionJson: Rep[String] = column[String]("input_config_during_execution", NotNull)

    override def * : ProvenShape[PeriodicProcessEntityWithJson] = (
      id,
      processName,
      processVersionId,
      processingType,
      processJson,
      inputConfigDuringExecutionJson,
      jarFileName,
      scheduleProperty,
      active,
      createdAt,
      processActionId
    ) <> (
      (PeriodicProcessEntity.createWithJson _).tupled,
      (e: PeriodicProcessEntityWithJson) =>
        PeriodicProcessEntityWithJson.unapply(e).map {
          case (
                id,
                processName,
                versionId,
                processingType,
                processJson,
                inputConfigDuringExecutionJson,
                jarFileName,
                scheduleProperty,
                active,
                createdAt,
                processActionId
              ) =>
            (
              id,
              processName,
              versionId,
              processingType,
              processJson.asJson.noSpaces,
              inputConfigDuringExecutionJson,
              jarFileName,
              scheduleProperty,
              active,
              createdAt,
              processActionId
            )
        }
    )

  }

  class PeriodicProcessWithoutJson(tag: Tag) extends PeriodicProcessesTable[PeriodicProcessEntityWithoutJson](tag) {

    override def * : ProvenShape[PeriodicProcessEntityWithoutJson] = (
      id,
      processName,
      processVersionId,
      processingType,
      jarFileName,
      scheduleProperty,
      active,
      createdAt,
      processActionId
    ) <> (
      (PeriodicProcessEntity.createWithoutJson _).tupled,
      (e: PeriodicProcessEntityWithoutJson) =>
        PeriodicProcessEntityWithoutJson.unapply(e).map {
          case (
                id,
                processName,
                versionId,
                processingType,
                jarFileName,
                scheduleProperty,
                active,
                createdAt,
                processActionId
              ) =>
            (
              id,
              processName,
              versionId,
              processingType,
              jarFileName,
              scheduleProperty,
              active,
              createdAt,
              processActionId
            )
        }
    )

  }

  object PeriodicProcessesWithJson extends TableQuery(new PeriodicProcessesWithJsonTable(_))

  object PeriodicProcessesWithoutJson extends TableQuery(new PeriodicProcessWithoutJson(_))

}

object PeriodicProcessEntity {

  def createWithJson(
      id: PeriodicProcessId,
      processName: ProcessName,
      processVersionId: VersionId,
      processingType: String,
      processJson: String,
      inputConfigDuringExecutionJson: String,
      jarFileName: String,
      scheduleProperty: String,
      active: Boolean,
      createdAt: LocalDateTime,
      processActionId: Option[ProcessActionId]
  ): PeriodicProcessEntityWithJson =
    PeriodicProcessEntityWithJson(
      id,
      processName,
      processVersionId,
      processingType,
      ProcessMarshaller.fromJsonUnsafe(processJson),
      inputConfigDuringExecutionJson,
      jarFileName,
      scheduleProperty,
      active,
      createdAt,
      processActionId
    )

  def createWithoutJson(
      id: PeriodicProcessId,
      processName: ProcessName,
      processVersionId: VersionId,
      processingType: String,
      jarFileName: String,
      scheduleProperty: String,
      active: Boolean,
      createdAt: LocalDateTime,
      processActionId: Option[ProcessActionId]
  ): PeriodicProcessEntityWithoutJson =
    PeriodicProcessEntityWithoutJson(
      id,
      processName,
      processVersionId,
      processingType,
      jarFileName,
      scheduleProperty,
      active,
      createdAt,
      processActionId
    )

}

trait PeriodicProcessEntity {

  def id: PeriodicProcessId

  def processName: ProcessName

  def processVersionId: VersionId

  def processingType: String

  def jarFileName: String

  def scheduleProperty: String

  def active: Boolean

  def createdAt: LocalDateTime

  def processActionId: Option[ProcessActionId]

}

case class PeriodicProcessEntityWithJson(
    id: PeriodicProcessId,
    processName: ProcessName,
    processVersionId: VersionId,
    processingType: String,
    processJson: CanonicalProcess,
    inputConfigDuringExecutionJson: String,
    jarFileName: String,
    scheduleProperty: String,
    active: Boolean,
    createdAt: LocalDateTime,
    processActionId: Option[ProcessActionId]
) extends PeriodicProcessEntity

case class PeriodicProcessEntityWithoutJson(
    id: PeriodicProcessId,
    processName: ProcessName,
    processVersionId: VersionId,
    processingType: String,
    jarFileName: String,
    scheduleProperty: String,
    active: Boolean,
    createdAt: LocalDateTime,
    processActionId: Option[ProcessActionId]
) extends PeriodicProcessEntity

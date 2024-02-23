package pl.touk.nussknacker.engine.management.periodic.db

import io.circe.syntax._
import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessId
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import slick.ast.TypedType
import slick.jdbc.JdbcProfile
import slick.lifted.MappedToBase.mappedToIsomorphism
import slick.lifted.ProvenShape
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.time.LocalDateTime
import java.util.UUID

trait PeriodicProcessesTableFactory {

  protected val profile: JdbcProfile

  import profile.api._

  implicit val ProcessActionIdTypedType: TypedType[ProcessActionId] =
    MappedColumnType.base[ProcessActionId, UUID](
      _.value,
      ProcessActionId(_)
    )

  abstract class PeriodicProcessesTable(tag: Tag) extends Table[PeriodicProcessEntity](tag, "periodic_processes") {

    def id: Rep[PeriodicProcessId] = column[PeriodicProcessId]("id", O.PrimaryKey, O.AutoInc)

    def processName: Rep[String] = column[String]("process_name", NotNull)

    def processVersionId: Rep[Long] = column[Long]("process_version_id", NotNull)

    def processingType: Rep[String] = column[String]("processing_type", NotNull)

    def inputConfigDuringExecutionJson: Rep[String] = column[String]("input_config_during_execution", NotNull)

    def jarFileName: Rep[String] = column[String]("jar_file_name", NotNull)

    def scheduleProperty: Rep[String] = column[String]("schedule_property", NotNull)

    def active: Rep[Boolean] = column[Boolean]("active", NotNull)

    def createdAt: Rep[LocalDateTime] = column[LocalDateTime]("created_at", NotNull)

    def processActionId: Rep[Option[ProcessActionId]] = column[Option[ProcessActionId]]("process_action_id")

  }

  class PeriodicProcessesWithJsonTable(tag: Tag) extends PeriodicProcessesTable(tag) {

    def processJson: Rep[String] = column[String]("process_json", NotNull)

    override def * : ProvenShape[PeriodicProcessEntity] = (
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
      (e: PeriodicProcessEntity) =>
        PeriodicProcessEntity.unapply(e).map {
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
              processName.value,
              versionId.value,
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

  class PeriodicProcessesWithUnitTable(tag: Tag) extends PeriodicProcessesTable(tag) {

    override def * : ProvenShape[PeriodicProcessEntity] = (
      id,
      processName,
      processVersionId,
      processingType,
      inputConfigDuringExecutionJson,
      jarFileName,
      scheduleProperty,
      active,
      createdAt,
      processActionId
    ) <> (
      (PeriodicProcessEntity.createWithoutJson _).tupled,
      (e: PeriodicProcessEntity) =>
        PeriodicProcessEntity.unapply(e).map {
          case (
                id,
                processName,
                versionId,
                processingType,
                _,
                inputConfigDuringExecutionJson,
                jarFileName,
                scheduleProperty,
                active,
                createdAt,
                processActionId
              ) =>
            (
              id,
              processName.value,
              versionId.value,
              processingType,
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

  object PeriodicProcessesWithJson extends TableQuery(new PeriodicProcessesWithJsonTable(_))

  object PeriodicProcessesWithUnit extends TableQuery(new PeriodicProcessesWithUnitTable(_))

}

object PeriodicProcessEntity {

  def createWithJson(
      id: PeriodicProcessId,
      processName: String,
      processVersionId: Long,
      processingType: String,
      processJson: String,
      inputConfigDuringExecutionJson: String,
      jarFileName: String,
      scheduleProperty: String,
      active: Boolean,
      createdAt: LocalDateTime,
      processActionId: Option[ProcessActionId]
  ): PeriodicProcessEntity =
    PeriodicProcessEntity(
      id,
      ProcessName(processName),
      VersionId(processVersionId),
      processingType,
      Some(ProcessMarshaller.fromJsonUnsafe(processJson)),
      inputConfigDuringExecutionJson,
      jarFileName,
      scheduleProperty,
      active,
      createdAt,
      processActionId
    )

  def createWithoutJson(
      id: PeriodicProcessId,
      processName: String,
      processVersionId: Long,
      processingType: String,
      inputConfigDuringExecutionJson: String,
      jarFileName: String,
      scheduleProperty: String,
      active: Boolean,
      createdAt: LocalDateTime,
      processActionId: Option[ProcessActionId]
  ): PeriodicProcessEntity =
    PeriodicProcessEntity(
      id,
      ProcessName(processName),
      VersionId(processVersionId),
      processingType,
      None,
      inputConfigDuringExecutionJson,
      jarFileName,
      scheduleProperty,
      active,
      createdAt,
      processActionId
    )

}

case class PeriodicProcessEntity(
    id: PeriodicProcessId,
    processName: ProcessName,
    processVersionId: VersionId,
    processingType: String,
    processJson: Option[CanonicalProcess],
    inputConfigDuringExecutionJson: String,
    jarFileName: String,
    scheduleProperty: String,
    active: Boolean,
    createdAt: LocalDateTime,
    processActionId: Option[ProcessActionId]
)

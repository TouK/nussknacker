package pl.touk.nussknacker.ui.db.entity

import io.circe.Decoder
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.deployment.PeriodicDeploymentHandler.RuntimeParams
import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessId
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.time.LocalDateTime
import java.util.UUID

trait PeriodicProcessesTableFactory extends BaseEntityFactory {

  protected val profile: JdbcProfile

  import profile.api._

  implicit val periodicProcessIdMapping: BaseColumnType[PeriodicProcessId] =
    MappedColumnType.base[PeriodicProcessId, Long](_.value, PeriodicProcessId.apply)

  private implicit val processActionIdTypedType: BaseColumnType[ProcessActionId] =
    MappedColumnType.base[ProcessActionId, UUID](
      _.value,
      ProcessActionId(_)
    )

  implicit val runtimeParamsTypedType: BaseColumnType[RuntimeParams] =
    MappedColumnType.base[RuntimeParams, String](
      _.params.asJson.noSpaces,
      jsonStr =>
        io.circe.parser.parse(jsonStr).flatMap(Decoder[Map[String, String]].decodeJson) match {
          case Right(params) => RuntimeParams(params)
          case Left(error)   => throw error
        }
    )

  abstract class PeriodicProcessesTable[ENTITY <: PeriodicProcessEntity](tag: Tag)
      extends Table[ENTITY](tag, "periodic_scenarios") {

    def id: Rep[PeriodicProcessId] = column[PeriodicProcessId]("id", O.PrimaryKey, O.AutoInc)

    def processId: Rep[Option[ProcessId]] = column[Option[ProcessId]]("process_id")

    def processName: Rep[ProcessName] = column[ProcessName]("process_name", NotNull)

    def processVersionId: Rep[VersionId] = column[VersionId]("process_version_id", NotNull)

    def processingType: Rep[String] = column[String]("processing_type", NotNull)

    def runtimeParams: Rep[RuntimeParams] = column[RuntimeParams]("runtime_params")

    def scheduleProperty: Rep[String] = column[String]("schedule_property", NotNull)

    def active: Rep[Boolean] = column[Boolean]("active", NotNull)

    def createdAt: Rep[LocalDateTime] = column[LocalDateTime]("created_at", NotNull)

    def processActionId: Rep[Option[ProcessActionId]] = column[Option[ProcessActionId]]("process_action_id")

  }

  class PeriodicProcessesWithInputConfigJsonTable(tag: Tag)
      extends PeriodicProcessesTable[PeriodicProcessEntityWithInputConfigJson](tag) {

    def inputConfigDuringExecutionJson: Rep[String] = column[String]("input_config_during_execution", NotNull)

    override def * : ProvenShape[PeriodicProcessEntityWithInputConfigJson] = (
      id,
      processId,
      processName,
      processVersionId,
      processingType,
      runtimeParams,
      scheduleProperty,
      active,
      createdAt,
      processActionId,
      inputConfigDuringExecutionJson,
    ) <> (PeriodicProcessEntityWithInputConfigJson.apply _ tupled, PeriodicProcessEntityWithInputConfigJson.unapply)

  }

  class PeriodicProcessesWithoutInputConfigJsonTable(tag: Tag)
      extends PeriodicProcessesTable[PeriodicProcessEntityWithoutInputConfigJson](tag) {

    override def * : ProvenShape[PeriodicProcessEntityWithoutInputConfigJson] = (
      id,
      processId,
      processName,
      processVersionId,
      processingType,
      runtimeParams,
      scheduleProperty,
      active,
      createdAt,
      processActionId
    ) <> (PeriodicProcessEntityWithoutInputConfigJson.apply _ tupled, PeriodicProcessEntityWithoutInputConfigJson.unapply)

  }

  object PeriodicProcessesWithoutInputConfig extends TableQuery(new PeriodicProcessesWithoutInputConfigJsonTable(_))

  object PeriodicProcessesWithInputConfig extends TableQuery(new PeriodicProcessesWithInputConfigJsonTable(_))

}

trait PeriodicProcessEntity {

  def id: PeriodicProcessId

  def processId: Option[ProcessId]

  def processName: ProcessName

  def processVersionId: VersionId

  def processingType: String

  def runtimeParams: RuntimeParams

  def scheduleProperty: String

  def active: Boolean

  def createdAt: LocalDateTime

  def processActionId: Option[ProcessActionId]

}

case class PeriodicProcessEntityWithInputConfigJson(
    id: PeriodicProcessId,
    processId: Option[ProcessId],
    processName: ProcessName,
    processVersionId: VersionId,
    processingType: String,
    runtimeParams: RuntimeParams,
    scheduleProperty: String,
    active: Boolean,
    createdAt: LocalDateTime,
    processActionId: Option[ProcessActionId],
    inputConfigDuringExecutionJson: String,
) extends PeriodicProcessEntity

case class PeriodicProcessEntityWithoutInputConfigJson(
    id: PeriodicProcessId,
    processId: Option[ProcessId],
    processName: ProcessName,
    processVersionId: VersionId,
    processingType: String,
    runtimeParams: RuntimeParams,
    scheduleProperty: String,
    active: Boolean,
    createdAt: LocalDateTime,
    processActionId: Option[ProcessActionId]
) extends PeriodicProcessEntity

package pl.touk.nussknacker.ui.db.entity

import io.circe.Decoder
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.deployment.scheduler.model.RuntimeParams
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
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

  implicit val canonicalProcessTypedType: BaseColumnType[CanonicalProcess] =
    MappedColumnType.base[CanonicalProcess, String](
      _.asJson.noSpaces,
      ProcessMarshaller.fromJsonUnsafe
    )

  abstract class PeriodicProcessesTable[ENTITY <: PeriodicProcessEntity](tag: Tag)
      extends Table[ENTITY](tag, "scheduled_scenarios") {

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

  class PeriodicProcessesWithDeploymentDetailsTable(tag: Tag)
      extends PeriodicProcessesTable[PeriodicProcessEntityWithDeploymentDetails](tag) {

    def inputConfigDuringExecutionJson: Rep[Option[String]] = column[Option[String]]("input_config_during_execution")

    def resolvedScenario: Rep[Option[CanonicalProcess]] = column[Option[CanonicalProcess]]("resolved_scenario_json")

    override def * : ProvenShape[PeriodicProcessEntityWithDeploymentDetails] = (
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
      resolvedScenario,
    ) <> (PeriodicProcessEntityWithDeploymentDetails.apply _ tupled, PeriodicProcessEntityWithDeploymentDetails.unapply)

  }

  class PeriodicProcessesWithoutDeploymentDetailsTable(tag: Tag)
      extends PeriodicProcessesTable[PeriodicProcessEntityWithoutDeploymentDetails](tag) {

    override def * : ProvenShape[PeriodicProcessEntityWithoutDeploymentDetails] = (
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
    ) <> (PeriodicProcessEntityWithoutDeploymentDetails.apply _ tupled, PeriodicProcessEntityWithoutDeploymentDetails.unapply)

  }

  object PeriodicProcessesWithoutDeploymentDetails
      extends TableQuery(new PeriodicProcessesWithoutDeploymentDetailsTable(_))

  object PeriodicProcessesWithDeploymentDetails extends TableQuery(new PeriodicProcessesWithDeploymentDetailsTable(_))

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

case class PeriodicProcessEntityWithDeploymentDetails(
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
    inputConfigDuringExecutionJson: Option[String],
    resolvedScenario: Option[CanonicalProcess],
) extends PeriodicProcessEntity

case class PeriodicProcessEntityWithoutDeploymentDetails(
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

package pl.touk.nussknacker.ui.db.entity

import io.circe.Decoder
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.deployment.periodic.model.{PeriodicProcessId, RuntimeParams}
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
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
      extends Table[ENTITY](tag, "periodic_processes") {

    def id: Rep[PeriodicProcessId] = column[PeriodicProcessId]("id", O.PrimaryKey, O.AutoInc)

    def processName: Rep[ProcessName] = column[ProcessName]("process_name", NotNull)

    def processVersionId: Rep[VersionId] = column[VersionId]("process_version_id", NotNull)

    def processingType: Rep[String] = column[String]("processing_type", NotNull)

    def jarFileName: Rep[Option[String]] = column[Option[String]]("jar_file_name")

    def runtimeParams: Rep[RuntimeParams] = column[RuntimeParams]("runtime_params")

    def scheduleProperty: Rep[String] = column[String]("schedule_property", NotNull)

    def active: Rep[Boolean] = column[Boolean]("active", NotNull)

    def createdAt: Rep[LocalDateTime] = column[LocalDateTime]("created_at", NotNull)

    def processActionId: Rep[Option[ProcessActionId]] = column[Option[ProcessActionId]]("process_action_id")

  }

  class PeriodicProcessesWithJsonTable(tag: Tag) extends PeriodicProcessesTable[PeriodicProcessEntityWithJson](tag) {

    def inputConfigDuringExecutionJson: Rep[String] = column[String]("input_config_during_execution", NotNull)

    override def * : ProvenShape[PeriodicProcessEntityWithJson] = (
      id,
      processName,
      processVersionId,
      processingType,
      inputConfigDuringExecutionJson,
      jarFileName,
      runtimeParams,
      scheduleProperty,
      active,
      createdAt,
      processActionId
    ) <> (
      tuple =>
        PeriodicProcessEntityWithJson(
          id = tuple._1,
          processName = tuple._2,
          processVersionId = tuple._3,
          processingType = tuple._4,
          inputConfigDuringExecutionJson = tuple._5,
          runtimeParams =
            RuntimeParams(tuple._6.map(f => Map("jarFileName" -> f)).getOrElse(Map.empty) ++ tuple._7.params),
          scheduleProperty = tuple._8,
          active = tuple._9,
          createdAt = tuple._10,
          processActionId = tuple._11,
        ),
      (e: PeriodicProcessEntityWithJson) =>
        PeriodicProcessEntityWithJson.unapply(e).map {
          case (
                id,
                processName,
                versionId,
                processingType,
                inputConfigDuringExecutionJson,
                runtimeParams,
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
              inputConfigDuringExecutionJson,
              None,
              runtimeParams,
              scheduleProperty,
              active,
              createdAt,
              processActionId
            )
        }
    )

  }

  class PeriodicProcessesWithoutJsonTable(tag: Tag)
      extends PeriodicProcessesTable[PeriodicProcessEntityWithoutJson](tag) {

    override def * : ProvenShape[PeriodicProcessEntityWithoutJson] = (
      id,
      processName,
      processVersionId,
      processingType,
      runtimeParams,
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

  object PeriodicProcessesWithoutJson extends TableQuery(new PeriodicProcessesWithoutJsonTable(_))

}

object PeriodicProcessEntity {

  def createWithJson(
      id: PeriodicProcessId,
      processName: ProcessName,
      processVersionId: VersionId,
      processingType: String,
      inputConfigDuringExecutionJson: String,
      runtimeParams: RuntimeParams,
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
      inputConfigDuringExecutionJson,
      runtimeParams,
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
      runtimeParams: RuntimeParams,
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
      runtimeParams,
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

  def runtimeParams: RuntimeParams

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
    inputConfigDuringExecutionJson: String,
    runtimeParams: RuntimeParams,
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
    runtimeParams: RuntimeParams,
    scheduleProperty: String,
    active: Boolean,
    createdAt: LocalDateTime,
    processActionId: Option[ProcessActionId]
) extends PeriodicProcessEntity

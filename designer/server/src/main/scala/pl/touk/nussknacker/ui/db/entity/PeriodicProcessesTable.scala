package pl.touk.nussknacker.ui.db.entity

import io.circe.Decoder
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.periodic.RuntimeParams
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessId
import slick.jdbc.JdbcProfile
import slick.lifted.MappedToBase.mappedToIsomorphism
import slick.lifted.ProvenShape
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.time.LocalDateTime
import java.util.UUID

trait PeriodicProcessesTableFactory {

  protected val profile: JdbcProfile

  import profile.api._

  private implicit val processNameMapping: BaseColumnType[ProcessName] =
    MappedColumnType.base[ProcessName, String](_.value, ProcessName.apply)

  private implicit val versionIdMapping: BaseColumnType[VersionId] =
    MappedColumnType.base[VersionId, Long](_.value, VersionId(_))

  private implicit val ProcessActionIdTypedType: BaseColumnType[ProcessActionId] =
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

  class PeriodicProcessesTable(tag: Tag) extends Table[PeriodicProcessEntity](tag, "periodic_processes") {

    def id: Rep[PeriodicProcessId] = column[PeriodicProcessId]("id", O.PrimaryKey, O.AutoInc)

    def processName: Rep[ProcessName] = column[ProcessName]("process_name", NotNull)

    def processVersionId: Rep[VersionId] = column[VersionId]("process_version_id", NotNull)

    def processingType: Rep[String] = column[String]("processing_type", NotNull)

    def inputConfigDuringExecutionJson: Rep[String] = column[String]("input_config_during_execution", NotNull)

    // This is a legacy column left after migrating periodic processes to core
    // The periodic deployment manager is now decoupled from Flink, and its runtime params are stored in runtime_params column
    def jarFileName: Rep[Option[String]] = column[Option[String]]("jar_file_name")

    def runtimeParams: Rep[RuntimeParams] = column[RuntimeParams]("runtime_params")

    def scheduleProperty: Rep[String] = column[String]("schedule_property", NotNull)

    def active: Rep[Boolean] = column[Boolean]("active", NotNull)

    def createdAt: Rep[LocalDateTime] = column[LocalDateTime]("created_at", NotNull)

    def processActionId: Rep[Option[ProcessActionId]] = column[Option[ProcessActionId]]("process_action_id")

    override def * : ProvenShape[PeriodicProcessEntity] = (
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
        PeriodicProcessEntity(
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
      (e: PeriodicProcessEntity) =>
        PeriodicProcessEntity.unapply(e).map {
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

  object PeriodicProcesses extends TableQuery(new PeriodicProcessesTable(_))

}

final case class PeriodicProcessEntity(
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
)

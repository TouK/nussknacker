package pl.touk.nussknacker.engine.management.periodic.db

import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessId
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.time.LocalDateTime

trait PeriodicProcessesTableFactory {

  protected val profile: JdbcProfile

  import profile.api._

  class PeriodicProcessesTable(tag: Tag) extends Table[PeriodicProcessEntity](tag, "periodic_processes") {

    def id: Rep[PeriodicProcessId] = column[PeriodicProcessId]("id", O.PrimaryKey, O.AutoInc)

    def processName: Rep[String] = column[String]("process_name", NotNull)

    def processVersionId: Rep[Long] = column[Long]("process_version_id", NotNull)

    def processJson: Rep[String] = column[String]("process_json", NotNull)

    def inputConfigDuringExecutionJson: Rep[String] = column[String]("input_config_during_execution", NotNull)

    def jarFileName: Rep[String] = column[String]("jar_file_name", NotNull)

    def scheduleProperty: Rep[String] = column[String]("schedule_property", NotNull)

    def active: Rep[Boolean] = column[Boolean]("active", NotNull)

    def createdAt: Rep[LocalDateTime] = column[LocalDateTime]("created_at", NotNull)

    override def * : ProvenShape[PeriodicProcessEntity] = (id, processName, processVersionId, processJson, inputConfigDuringExecutionJson, jarFileName, scheduleProperty, active, createdAt) <> (
      (PeriodicProcessEntity.create _).tupled,
      (e: PeriodicProcessEntity) => PeriodicProcessEntity.unapply(e).map { t => (t._1, t._2.value, t._3.value, t._4, t._5, t._6, t._7, t._8, t._9) }
    )
  }

  object PeriodicProcesses extends TableQuery(new PeriodicProcessesTable(_))

}

object PeriodicProcessEntity {
  def create(id: PeriodicProcessId, processName: String, processVersionId: Long, processJson: String, inputConfigDuringExecutionJson: String,
             jarFileName: String, scheduleProperty: String, active: Boolean, createdAt: LocalDateTime): PeriodicProcessEntity =
    PeriodicProcessEntity(id, ProcessName(processName), VersionId(processVersionId), processJson, inputConfigDuringExecutionJson, jarFileName, scheduleProperty, active, createdAt)
}

case class PeriodicProcessEntity(id: PeriodicProcessId,
                                 processName: ProcessName,
                                 processVersionId: VersionId,
                                 processJson: String,
                                 inputConfigDuringExecutionJson: String,
                                 jarFileName: String,
                                 scheduleProperty: String,
                                 active: Boolean,
                                 createdAt: LocalDateTime) {
  lazy val graphProcess: GraphProcess = GraphProcess(processJson)
}

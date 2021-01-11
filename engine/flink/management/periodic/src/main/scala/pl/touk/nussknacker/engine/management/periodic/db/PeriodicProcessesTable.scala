package pl.touk.nussknacker.engine.management.periodic.db

import java.time.LocalDateTime

import pl.touk.nussknacker.engine.management.periodic.PeriodicProcessId
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape
import slick.sql.SqlProfile.ColumnOption.NotNull

trait PeriodicProcessesTableFactory {

  protected val profile: JdbcProfile

  import profile.api._

  class PeriodicProcessesTable(tag: Tag) extends Table[PeriodicProcessEntity](tag, "periodic_processes") {

    def id: Rep[PeriodicProcessId] = column[PeriodicProcessId]("id", O.PrimaryKey, O.AutoInc)

    def processName: Rep[String] = column[String]("process_name", NotNull)

    def processVersionId: Rep[Long] = column[Long]("process_version_id", NotNull)

    def processJson: Rep[String] = column[String]("process_json", NotNull)

    def modelConfig: Rep[String] = column[String]("model_config", NotNull)

    def buildInfoJson: Rep[String] = column[String]("build_info_json", NotNull)

    def jarFileName: Rep[String] = column[String]("jar_file_name", NotNull)

    def periodicProperty: Rep[String] = column[String]("periodic_property", NotNull)

    def active: Rep[Boolean] = column[Boolean]("active", NotNull)

    def createdAt: Rep[LocalDateTime] = column[LocalDateTime]("created_at", NotNull)

    override def * : ProvenShape[PeriodicProcessEntity] = (id, processName, processVersionId, processJson, modelConfig, buildInfoJson, jarFileName, periodicProperty, active, createdAt) <>
      ((PeriodicProcessEntity.apply _).tupled, PeriodicProcessEntity.unapply)
  }

  object PeriodicProcesses extends TableQuery(new PeriodicProcessesTable(_))

}

case class PeriodicProcessEntity(id: PeriodicProcessId,
                                 processName: String,
                                 processVersionId: Long,
                                 processJson: String,
                                 modelConfig: String,
                                 buildInfoJson: String,
                                 jarFileName: String,
                                 periodicProperty: String,
                                 active: Boolean,
                                 createdAt: LocalDateTime)

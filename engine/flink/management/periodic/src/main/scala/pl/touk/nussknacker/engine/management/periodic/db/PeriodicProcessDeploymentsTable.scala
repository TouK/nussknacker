package pl.touk.nussknacker.engine.management.periodic.db

import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic.model.{PeriodicProcessDeploymentId, PeriodicProcessDeploymentStatus, PeriodicProcessId}
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.time.LocalDateTime

trait PeriodicProcessDeploymentsTableFactory extends PeriodicProcessesTableFactory {

  protected val profile: JdbcProfile

  import profile.api._

  implicit val periodicProcessDeploymentStatusColumnTyped = MappedColumnType.base[PeriodicProcessDeploymentStatus, String](_.toString, PeriodicProcessDeploymentStatus.withName)

  class PeriodicProcessDeploymentsTable(tag: Tag) extends Table[PeriodicProcessDeploymentEntity](tag, "periodic_process_deployments") {

    def id: Rep[PeriodicProcessDeploymentId] = column[PeriodicProcessDeploymentId]("id", O.PrimaryKey, O.AutoInc)

    def periodicProcessId: Rep[PeriodicProcessId] = column[PeriodicProcessId]("periodic_process_id", NotNull)

    def createdAt: Rep[LocalDateTime] = column[LocalDateTime]("created_at", NotNull)

    def runAt: Rep[LocalDateTime] = column[LocalDateTime]("run_at", NotNull)

    def scheduleName: Rep[Option[String]] = column[Option[String]]("schedule_name")

    def deployedAt: Rep[Option[LocalDateTime]] = column[Option[LocalDateTime]]("deployed_at")

    def completedAt: Rep[Option[LocalDateTime]] = column[Option[LocalDateTime]]("completed_at")

    def retriesLeft: Rep[Int] = column[Int]("retries_left")

    def nextRetryAt: Rep[Option[LocalDateTime]] = column[Option[LocalDateTime]]("next_retry_at")

    def status: Rep[PeriodicProcessDeploymentStatus] = column[PeriodicProcessDeploymentStatus]("status", NotNull)

    override def * : ProvenShape[PeriodicProcessDeploymentEntity] = (id, periodicProcessId, createdAt, runAt, scheduleName, deployedAt, completedAt, retriesLeft, nextRetryAt, status) <>
      ((PeriodicProcessDeploymentEntity.apply _).tupled, PeriodicProcessDeploymentEntity.unapply)
  }

  object PeriodicProcessDeployments extends TableQuery(new PeriodicProcessDeploymentsTable(_))
}

case class PeriodicProcessDeploymentEntity(id: PeriodicProcessDeploymentId,
                                           periodicProcessId: PeriodicProcessId,
                                           createdAt: LocalDateTime,
                                           runAt: LocalDateTime,
                                           scheduleName: Option[String],
                                           deployedAt: Option[LocalDateTime],
                                           completedAt: Option[LocalDateTime],
                                           retriesLeft: Int,
                                           nextRetryAt: Option[LocalDateTime],
                                           status: PeriodicProcessDeploymentStatus)

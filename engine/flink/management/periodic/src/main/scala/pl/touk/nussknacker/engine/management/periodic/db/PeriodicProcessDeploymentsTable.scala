package pl.touk.nussknacker.engine.management.periodic.db

import java.time.LocalDateTime

import pl.touk.nussknacker.engine.management.periodic.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic.{PeriodicProcessDeploymentId, PeriodicProcessDeploymentStatus, PeriodicProcessId}
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape
import slick.sql.SqlProfile.ColumnOption.NotNull

trait PeriodicProcessDeploymentsTableFactory {

  protected val profile: JdbcProfile

  import profile.api._

  implicit val periodicProcessDeploymentStatusColumnTyped = MappedColumnType.base[PeriodicProcessDeploymentStatus, String](_.toString, PeriodicProcessDeploymentStatus.withName)

  class PeriodicProcessDeploymentsTable(tag: Tag) extends Table[PeriodicProcessDeploymentEntity](tag, "periodic_process_deployments") {

    def id: Rep[PeriodicProcessDeploymentId] = column[PeriodicProcessDeploymentId]("id", O.PrimaryKey, O.AutoInc)

    def periodicProcessId: Rep[PeriodicProcessId] = column[PeriodicProcessId]("periodic_process_id", NotNull)

    def createdAt: Rep[LocalDateTime] = column[LocalDateTime]("created_at", NotNull)

    def runAt: Rep[LocalDateTime] = column[LocalDateTime]("run_at", NotNull)

    def deployedAt: Rep[Option[LocalDateTime]] = column[Option[LocalDateTime]]("deployed_at")

    def completedAt: Rep[Option[LocalDateTime]] = column[Option[LocalDateTime]]("completed_at")

    def status: Rep[PeriodicProcessDeploymentStatus] = column[PeriodicProcessDeploymentStatus]("status", NotNull)

    override def * : ProvenShape[PeriodicProcessDeploymentEntity] = (id, periodicProcessId, createdAt, runAt, deployedAt, completedAt, status) <>
      ((PeriodicProcessDeploymentEntity.apply _).tupled, PeriodicProcessDeploymentEntity.unapply)
  }

  object PeriodicProcessDeployments extends TableQuery(new PeriodicProcessDeploymentsTable(_)) {
    val findToBeDeployed = this.filter(e => e.runAt <= LocalDateTime.now() && e.status === (PeriodicProcessDeploymentStatus.Scheduled : PeriodicProcessDeploymentStatus))
  }

}

case class PeriodicProcessDeploymentEntity(id: PeriodicProcessDeploymentId,
                                           periodicProcessId: PeriodicProcessId,
                                           createdAt: LocalDateTime,
                                           runAt: LocalDateTime,
                                           deployedAt: Option[LocalDateTime],
                                           completedAt: Option[LocalDateTime],
                                           status: PeriodicProcessDeploymentStatus)

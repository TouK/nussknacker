package pl.touk.nussknacker.engine.management.periodic.db

import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic.model.{
  PeriodicProcessDeploymentId,
  PeriodicProcessDeploymentStatus,
  PeriodicProcessId
}
import slick.jdbc.{JdbcProfile, JdbcType}
import slick.lifted.ProvenShape
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.time.LocalDateTime

trait PeriodicProcessDeploymentsTableFactory extends PeriodicProcessesTableFactory {

  protected val profile: JdbcProfile

  import profile.api._

  implicit val periodicProcessDeploymentStatusColumnTyped: JdbcType[PeriodicProcessDeploymentStatus] =
    MappedColumnType.base[PeriodicProcessDeploymentStatus, String](_.toString, PeriodicProcessDeploymentStatus.withName)

  implicit val externalDeploymentIdColumnTyped: JdbcType[ExternalDeploymentId] =
    MappedColumnType.base[ExternalDeploymentId, String](_.value, ExternalDeploymentId(_))

  class PeriodicProcessDeploymentsTable(tag: Tag)
      extends Table[PeriodicProcessDeploymentEntity](tag, "periodic_process_deployments") {

    def id: Rep[PeriodicProcessDeploymentId] = column[PeriodicProcessDeploymentId]("id", O.PrimaryKey, O.AutoInc)

    def periodicProcessId: Rep[PeriodicProcessId] = column[PeriodicProcessId]("periodic_process_id", NotNull)

    def createdAt: Rep[LocalDateTime] = column[LocalDateTime]("created_at", NotNull)

    def runAt: Rep[LocalDateTime] = column[LocalDateTime]("run_at", NotNull)

    def scheduleName: Rep[Option[String]] = column[Option[String]]("schedule_name")

    def deployedAt: Rep[Option[LocalDateTime]] = column[Option[LocalDateTime]]("deployed_at")

    def externalDeploymentId: Rep[Option[ExternalDeploymentId]] =
      column[Option[ExternalDeploymentId]]("job_id") // TODO: add this column via migration

    def completedAt: Rep[Option[LocalDateTime]] = column[Option[LocalDateTime]]("completed_at")

    def retriesLeft: Rep[Int] = column[Int]("retries_left")

    def nextRetryAt: Rep[Option[LocalDateTime]] = column[Option[LocalDateTime]]("next_retry_at")

    def status: Rep[PeriodicProcessDeploymentStatus] = column[PeriodicProcessDeploymentStatus]("status", NotNull)

    override def * : ProvenShape[PeriodicProcessDeploymentEntity] = (
      id,
      periodicProcessId,
      createdAt,
      runAt,
      scheduleName,
      deployedAt,
      externalDeploymentId,
      completedAt,
      retriesLeft,
      nextRetryAt,
      status
    ) <>
      ((PeriodicProcessDeploymentEntity.apply _).tupled, PeriodicProcessDeploymentEntity.unapply)

  }

  object PeriodicProcessDeployments extends TableQuery(new PeriodicProcessDeploymentsTable(_))
}

case class PeriodicProcessDeploymentEntity(
    id: PeriodicProcessDeploymentId,
    periodicProcessId: PeriodicProcessId,
    createdAt: LocalDateTime,
    runAt: LocalDateTime,
    scheduleName: Option[String],
    deployedAt: Option[LocalDateTime],
    externalDeploymentId: Option[ExternalDeploymentId],
    completedAt: Option[LocalDateTime],
    retriesLeft: Int,
    nextRetryAt: Option[LocalDateTime],
    status: PeriodicProcessDeploymentStatus
)

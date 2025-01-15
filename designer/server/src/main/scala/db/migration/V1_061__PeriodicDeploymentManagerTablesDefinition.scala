package db.migration

import com.typesafe.scalalogging.LazyLogging
import db.migration.V1_061__PeriodicDeploymentManagerTablesDefinition.Definitions
import pl.touk.nussknacker.ui.db.migration.SlickMigration
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

trait V1_061__PeriodicDeploymentManagerTablesDefinition extends SlickMigration with LazyLogging {

  import profile.api._

  private val definitions = new Definitions(profile)

  override def migrateActions: DBIOAction[Any, NoStream, Effect.All] = {
    logger.info("Starting migration V1_061__PeriodicDeploymentManagerTablesDefinition")
    for {
      _ <- definitions.periodicProcessesTable.schema.create
      _ <- definitions.periodicProcessDeploymentsTable.schema.create
    } yield ()
  }

}

object V1_061__PeriodicDeploymentManagerTablesDefinition {

  class Definitions(val profile: JdbcProfile) {
    import profile.api._

    val periodicProcessDeploymentsTable = TableQuery[PeriodicProcessDeploymentsTable]

    class PeriodicProcessDeploymentsTable(tag: Tag)
        extends Table[PeriodicProcessDeploymentEntity](tag, "periodic_scenario_deployments") {

      def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)

      def periodicProcessId: Rep[Long] = column[Long]("periodic_process_id", NotNull)

      def createdAt: Rep[LocalDateTime] = column[LocalDateTime]("created_at", NotNull)

      def runAt: Rep[LocalDateTime] = column[LocalDateTime]("run_at", NotNull)

      def scheduleName: Rep[Option[String]] = column[Option[String]]("schedule_name")

      def deployedAt: Rep[Option[LocalDateTime]] = column[Option[LocalDateTime]]("deployed_at")

      def completedAt: Rep[Option[LocalDateTime]] = column[Option[LocalDateTime]]("completed_at")

      def retriesLeft: Rep[Int] = column[Int]("retries_left")

      def nextRetryAt: Rep[Option[LocalDateTime]] = column[Option[LocalDateTime]]("next_retry_at")

      def status: Rep[String] = column[String]("status", NotNull)

      def periodicProcessIdIndex = index("periodic_scenario_deployments_periodic_process_id_idx", periodicProcessId)
      def createdAtIndex         = index("periodic_scenario_deployments_created_at_idx", createdAt)
      def runAtIndex             = index("periodic_scenario_deployments_run_at_idx", runAt)

      override def * : ProvenShape[PeriodicProcessDeploymentEntity] = (
        id,
        periodicProcessId,
        createdAt,
        runAt,
        scheduleName,
        deployedAt,
        completedAt,
        retriesLeft,
        nextRetryAt,
        status
      ) <>
        ((PeriodicProcessDeploymentEntity.apply _).tupled, PeriodicProcessDeploymentEntity.unapply)

    }

    case class PeriodicProcessDeploymentEntity(
        id: Long,
        periodicProcessId: Long,
        createdAt: LocalDateTime,
        runAt: LocalDateTime,
        scheduleName: Option[String],
        deployedAt: Option[LocalDateTime],
        completedAt: Option[LocalDateTime],
        retriesLeft: Int,
        nextRetryAt: Option[LocalDateTime],
        status: String
    )

    val periodicProcessesTable = TableQuery[PeriodicProcessesTable]

    class PeriodicProcessesTable(tag: Tag) extends Table[PeriodicProcessEntity](tag, "periodic_scenarios") {

      def periodicProcessId: Rep[Long] = column[Long]("id", O.Unique, O.AutoInc)

      def processId: Rep[Option[Long]] = column[Option[Long]]("process_id")

      def processName: Rep[String] = column[String]("process_name", NotNull)

      def processVersionId: Rep[Long] = column[Long]("process_version_id", NotNull)

      def processingType: Rep[String] = column[String]("processing_type", NotNull)

      def runtimeParams: Rep[String] = column[String]("runtime_params")

      def scheduleProperty: Rep[String] = column[String]("schedule_property", NotNull)

      def active: Rep[Boolean] = column[Boolean]("active", NotNull)

      def createdAt: Rep[LocalDateTime] = column[LocalDateTime]("created_at", NotNull)

      def processActionId: Rep[Option[UUID]] = column[Option[UUID]]("process_action_id")

      def inputConfigDuringExecutionJson: Rep[String] = column[String]("input_config_during_execution", NotNull)

      def processNameAndActiveIndex = index("periodic_scenarios_process_name_active_idx", (processName, active))

      override def * : ProvenShape[PeriodicProcessEntity] = (
        periodicProcessId,
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
      ) <> (PeriodicProcessEntity.apply _ tupled, PeriodicProcessEntity.unapply)

    }

    case class PeriodicProcessEntity(
        id: Long,
        processId: Option[Long],
        processName: String,
        processVersionId: Long,
        processingType: String,
        runtimeParams: String,
        scheduleProperty: String,
        active: Boolean,
        createdAt: LocalDateTime,
        processActionId: Option[UUID],
        inputConfigDuringExecutionJson: String,
    )

  }

}

package pl.touk.nussknacker.ui.process.newdeployment

import cats.implicits.{toFoldableOps, toTraverseOps}
import db.util.DBIOActionInstances._
import org.postgresql.util.{PSQLException, PSQLState}
import pl.touk.nussknacker.engine.api.deployment.{DeploymentStatus, DeploymentStatusName, ProblemDeploymentStatus}
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.engine.newdeployment.DeploymentId
import pl.touk.nussknacker.ui.db.entity.ProcessEntityData
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentEntityFactory.DeploymentEntityData
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentRepository.{
  ConflictingDeploymentIdError,
  DeploymentWithScenarioMetadata
}
import slick.jdbc.JdbcProfile

import java.sql.{SQLIntegrityConstraintViolationException, Timestamp}
import java.time.Clock
import scala.concurrent.ExecutionContext

class DeploymentRepository(dbRef: DbRef, clock: Clock)(implicit ec: ExecutionContext) extends NuTables {

  override protected val profile: JdbcProfile = dbRef.profile

  import profile.api._

  def getScenarioDeploymentsInNotMatchingStatus(scenarioId: ProcessId, statusNames: Set[DeploymentStatusName]) = {
    toEffectAll(deploymentsTable.filter(d => d.scenarioId === scenarioId && !(d.statusName inSet statusNames)).result)
  }

  def saveDeployment(deployment: DeploymentEntityData): DB[Either[ConflictingDeploymentIdError, Unit]] = {
    toEffectAll(deploymentsTable += deployment).asTry.map(
      _.map(_ => Right(()))
        .recover {
          // for postgres
          case e: PSQLException if e.getSQLState == PSQLState.UNIQUE_VIOLATION.getState =>
            Left(ConflictingDeploymentIdError(deployment.id))
          // for other dbs, e.g. hsql
          case _: SQLIntegrityConstraintViolationException =>
            Left(ConflictingDeploymentIdError(deployment.id))
        }
        .get
    )
  }

  def getDeploymentById(id: DeploymentId): DB[Option[DeploymentWithScenarioMetadata]] = {
    toEffectAll(
      deploymentsTable
        .filter(_.id === id)
        .join(processesTable)
        .on(_.scenarioId === _.id)
        .take(1)
        .result
        .headOption
        .map(_.map(DeploymentWithScenarioMetadata.apply _ tupled))
    )
  }

  def updateDeploymentStatuses(statusesToUpdate: Map[DeploymentId, DeploymentStatus]): DB[Set[DeploymentId]] = {
    statusesToUpdate.toList
      .map { case (id, status) =>
        val problemDescription = ProblemDeploymentStatus.extractDescription(status)
        toEffectAll(
          deploymentsTable
            .filter(d =>
              d.id === id && (d.statusName =!= status.name || d.statusProblemDescription =!= problemDescription)
            )
            .map(d => (d.statusName, d.statusProblemDescription, d.statusModifiedAt))
            .update((status.name, problemDescription, Timestamp.from(clock.instant())))
            .map { result =>
              if (result > 0) Set(id) else Set.empty[DeploymentId]
            }
        )
      }
      .sequence
      .map(_.combineAll)
      // For the performance reasons it is better to run all updates in the one session, transactionally should enforce it
      .transactionally
  }

}

object DeploymentRepository {

  final case class ConflictingDeploymentIdError(id: DeploymentId)

  final case class DeploymentWithScenarioMetadata(deployment: DeploymentEntityData, scenarioMetadata: ProcessEntityData)

}

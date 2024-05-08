package pl.touk.nussknacker.ui.process.newdeployment

import db.util.DBIOActionInstances._
import pl.touk.nussknacker.ui.db.entity.ProcessEntityData
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentEntityFactory.DeploymentEntityData
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentRepository.{
  ConflictingDeploymentIdError,
  DeploymentWithScenarioMetadata
}
import slick.jdbc.JdbcProfile

import java.sql.SQLIntegrityConstraintViolationException
import scala.concurrent.ExecutionContext

class DeploymentRepository(dbRef: DbRef)(implicit ec: ExecutionContext) extends NuTables {

  override protected val profile: JdbcProfile = dbRef.profile

  import profile.api._

  def saveDeployment(deployment: DeploymentEntityData): DB[Either[ConflictingDeploymentIdError, Unit]] = {
    toEffectAll(deploymentsTable += deployment).asTry.map(
      _.map(_ => Right(()))
        .recover { case _: SQLIntegrityConstraintViolationException =>
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

}

object DeploymentRepository {

  final case class ConflictingDeploymentIdError(id: DeploymentId)

  final case class DeploymentWithScenarioMetadata(deployment: DeploymentEntityData, scenarioMetadata: ProcessEntityData)

}

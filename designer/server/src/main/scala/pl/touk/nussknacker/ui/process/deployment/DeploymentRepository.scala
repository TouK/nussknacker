package pl.touk.nussknacker.ui.process.deployment

import db.util.DBIOActionInstances._
import pl.touk.nussknacker.ui.db.entity.ProcessEntityData
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import pl.touk.nussknacker.ui.error.DeploymentNotFoundError
import pl.touk.nussknacker.ui.process.deployment.DeploymentEntityFactory.DeploymentEntityData
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext

class DeploymentRepository(dbRef: DbRef)(implicit ec: ExecutionContext) extends NuTables {

  override protected val profile: JdbcProfile = dbRef.profile

  import profile.api._

  // TODO: handle constraint violated
  def saveDeployment(deployment: DeploymentEntityData): DB[Unit] = {
    toEffectAll(deploymentsTable += deployment).map(_ => ())
  }

  def getDeploymentById(id: NewDeploymentId): DB[Either[DeploymentNotFoundError, DeploymentWithScenarioMetadata]] = {
    toEffectAll(
      deploymentsTable
        .filter(_.id === id)
        .join(processesTable)
        .on(_.scenarioId === _.id)
        .take(1)
        .result
        .headOption
        .map(_.toRight(DeploymentNotFoundError(id)).map(DeploymentWithScenarioMetadata.apply _ tupled))
    )
  }

}

case class DeploymentWithScenarioMetadata(deployment: DeploymentEntityData, scenarioMetadata: ProcessEntityData)

package pl.touk.nussknacker.ui.process.newdeployment

import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentEntityFactory.DeploymentEntityData
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

class DeploymentRepository(dbRef: DbRef)(implicit ec: ExecutionContext) extends NuTables {

  override protected val profile: JdbcProfile = dbRef.profile

  import profile.api._

  def insertDeployment(deployment: DeploymentEntityData): Future[Int] = {
    dbRef.db.run(deploymentsTable += deployment)
  }

  def getDeploymentById(id: NewDeploymentId): Future[DeploymentEntityData] = {
    dbRef.db.run(deploymentsTable.filter(_.id === id).take(1).result.head)
  }

}

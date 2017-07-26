package pl.touk.nussknacker.ui.process.repository

import java.sql.Timestamp
import java.time.LocalDateTime

import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.EspTables
import pl.touk.nussknacker.ui.db.EspTables._
import pl.touk.nussknacker.ui.db.entity.ProcessDeploymentInfoEntity.{DeployedProcessVersionEntityData, DeploymentAction}
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import slick.jdbc.{JdbcBackend, JdbcProfile}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.higherKinds

class DeployedProcessRepository(db: JdbcBackend.Database,
                                driver: JdbcProfile,
                                buildInfo: Map[String, String]) {
  import driver.api._

  def markProcessAsDeployed(processVersion: ProcessVersionEntityData, userId: String, environment: String)
                           (implicit ec: ExecutionContext): Future[Unit] = {
    val insertAction = EspTables.deployedProcessesTable += DeployedProcessVersionEntityData(
      processVersion.processId,
      Some(processVersion.id),
      environment,
      userId,
      Timestamp.valueOf(LocalDateTime.now()),
      DeploymentAction.Deploy,
      Some(BuildInfo.writeAsJson(buildInfo))
    )
    db.run(insertAction).map(_ => ())
  }

  def markProcessAsCancelled(processId: String, userId: String, environment: String)
                             (implicit ec: ExecutionContext): Future[Unit] = {

    val insertAction = EspTables.deployedProcessesTable += DeployedProcessVersionEntityData(
      processId,
      None,
      environment,
      userId,
      Timestamp.valueOf(LocalDateTime.now()),
      DeploymentAction.Cancel,
      None
    )
    db.run(insertAction).map(_ => ())
  }

}

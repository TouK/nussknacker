package pl.touk.esp.ui.process.repository

import java.sql.Timestamp
import java.time.LocalDateTime

import pl.touk.esp.ui.app.BuildInfoHolder
import pl.touk.esp.ui.db.EspTables
import pl.touk.esp.ui.db.entity.DeployedProcessVersionEntity.DeployedProcessVersionEntityData
import pl.touk.esp.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import slick.jdbc.{JdbcBackend, JdbcProfile}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

class DeployedProcessRepository(db: JdbcBackend.Database,
                                driver: JdbcProfile,
                                buildInfoHolder: BuildInfoHolder) {
  import driver.api._

  def markProcessAsDeployed(processVersion: ProcessVersionEntityData, userId: String, environment: String)
                           (implicit ec: ExecutionContext): Future[Unit] = {
    val insertAction = EspTables.deployedProcessesTable += DeployedProcessVersionEntityData(
      processVersion.processId,
      processVersion.id,
      environment,
      userId,
      Timestamp.valueOf(LocalDateTime.now()),
      Some(buildInfoHolder.buildInfoAsJson)
    )
    db.run(insertAction).map(_ => ())
  }

}

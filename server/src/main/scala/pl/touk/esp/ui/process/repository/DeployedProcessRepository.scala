package pl.touk.esp.ui.process.repository

import java.sql.Timestamp
import java.time.LocalDateTime

import pl.touk.esp.ui.db.migration.CreateDeployedProcessesMigration
import pl.touk.esp.ui.db.migration.CreateDeployedProcessesMigration.DeployedProcessVersionEntityData
import pl.touk.esp.ui.db.migration.CreateProcessesMigration.ProcessVersionEntityData
import slick.jdbc.{JdbcBackend, JdbcProfile}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

class DeployedProcessRepository(db: JdbcBackend.Database,
                                driver: JdbcProfile) {

  private val deployedProcessesMigration = new CreateDeployedProcessesMigration {
    override protected val profile: JdbcProfile = DeployedProcessRepository.this.driver
  }

  import deployedProcessesMigration._
  import driver.api._

  def markProcessAsDeployed(processVersion: ProcessVersionEntityData, userId: String, environment: String)
                           (implicit ec: ExecutionContext): Future[Unit] = {
    val insertAction = deployedProcessesTable += DeployedProcessVersionEntityData(
      processVersion.processId,
      processVersion.id,
      environment,
      userId,
      Timestamp.valueOf(LocalDateTime.now())
    )
    db.run(insertAction).map(_ => ())
  }

}
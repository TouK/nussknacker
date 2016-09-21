package pl.touk.esp.ui.process.repository

import java.sql.Timestamp
import java.time.LocalDateTime

import pl.touk.esp.ui.db.migration.CreateDeployedProcessesMigration
import pl.touk.esp.ui.db.migration.CreateDeployedProcessesMigration.DeployedProcessEntityData
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

  def saveDeployedProcess(id: String, json: String)(implicit ec: ExecutionContext): Future[Unit] = {
    val insertAction = deployedProcessesTable += DeployedProcessEntityData(id, Timestamp.valueOf(LocalDateTime.now()), json)
    db.run(insertAction).map(_ => ())
  }

  def fetchDeployedProcessById(id: String)(implicit ec: ExecutionContext): Future[Option[DeployedProcessEntityData]] = {
    val action = deployedProcessesTable.filter(_.id === id).result.headOption
    db.run(action)
  }

}
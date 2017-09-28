package pl.touk.nussknacker.ui.process.repository

import java.sql.Timestamp
import java.time.LocalDateTime

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.{DbConfig, EspTables}
import pl.touk.nussknacker.ui.db.entity.ProcessDeploymentInfoEntity.{DeployedProcessVersionEntityData, DeploymentAction}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object DeployedProcessRepository {
  def create(dbConfig: DbConfig, modelData: Map[ProcessingType, ModelData]) : DeployedProcessRepository = {
    new DeployedProcessRepository(dbConfig, modelData.mapValues(_.configCreator.buildInfo()))
  }
}

case class DeployedProcessRepository(dbConfig: DbConfig,
                                buildInfos: Map[ProcessingType, Map[String, String]]) extends BasicRepository {
  import driver.api._

  def markProcessAsDeployed(processingType: ProcessingType,
                             processVersion: ProcessVersionEntityData, userId: String, environment: String)
                           (implicit ec: ExecutionContext): Future[Unit] = {
    val insertAction = EspTables.deployedProcessesTable += DeployedProcessVersionEntityData(
      processVersion.processId,
      Some(processVersion.id),
      environment,
      userId,
      Timestamp.valueOf(LocalDateTime.now()),
      DeploymentAction.Deploy,
      buildInfos.get(processingType).map(BuildInfo.writeAsJson)
    )
    run(insertAction).map(_ => ())
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
    run(insertAction).map(_ => ())
  }

}

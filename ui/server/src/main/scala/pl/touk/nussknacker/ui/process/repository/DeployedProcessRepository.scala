package pl.touk.nussknacker.ui.process.repository

import java.sql.Timestamp
import java.time.LocalDateTime

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.{DbConfig, EspTables}
import pl.touk.nussknacker.ui.db.entity.ProcessDeploymentInfoEntity.{DeployedProcessVersionEntityData, DeploymentAction}
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import pl.touk.nussknacker.ui.process.ProcessId

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

  def markProcessAsDeployed(processId: ProcessId, processVersion: Long, processingType: ProcessingType,
                            userId: String, environment: String)
                           (implicit ec: ExecutionContext): Future[Unit] = {
    val insertAction = EspTables.deployedProcessesTable += DeployedProcessVersionEntityData(
      processId = processId.value,
      processVersionId = Some(processVersion),
      environment = environment,
      user = userId,
      deployedAt = Timestamp.valueOf(LocalDateTime.now()),
      deploymentAction = DeploymentAction.Deploy,
      buildInfo = buildInfos.get(processingType).map(BuildInfo.writeAsJson)
    )
    run(insertAction).map(_ => ())
  }

  def markProcessAsCancelled(processId: ProcessId, userId: String, environment: String)
                            (implicit ec: ExecutionContext): Future[Unit] = {

    val insertAction = EspTables.deployedProcessesTable += DeployedProcessVersionEntityData(
      processId = processId.value,
      processVersionId = None,
      environment = environment,
      user = userId,
      deployedAt = Timestamp.valueOf(LocalDateTime.now()),
      deploymentAction = DeploymentAction.Cancel,
      buildInfo = None
    )
    run(insertAction).map(_ => ())
  }

}

package pl.touk.nussknacker.engine.management.periodic.db

import java.time.{LocalDateTime, ZonedDateTime}
import io.circe.parser
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.management.periodic.{jar, _}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.periodic.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic.definition.{CronPeriodicProperty, PeriodicProperty}
import pl.touk.nussknacker.engine.management.periodic.jar.DeploymentWithJarData

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.util.Random

class InMemPeriodicProcessesRepository extends PeriodicProcessesRepository {

  var processEntities: mutable.ListBuffer[PeriodicProcessEntity] = ListBuffer.empty
  var deploymentEntities: mutable.ListBuffer[PeriodicProcessDeploymentEntity] = ListBuffer.empty

  def addActiveProcess(processName: ProcessName, deploymentStatus: PeriodicProcessDeploymentStatus): Unit = {
    val id = PeriodicProcessId(Random.nextLong())
    processEntities += PeriodicProcessEntity(
      id = id,
      processName = processName.value,
      processVersionId = 1,
      processJson = "{}",
      modelConfig = "",
      jarFileName = "",
      periodicProperty = (CronPeriodicProperty("0 0 * * * ?"): PeriodicProperty).asJson.noSpaces,
      scheduleName = "",
      active = true,
      createdAt = LocalDateTime.now()
    )
    deploymentEntities += PeriodicProcessDeploymentEntity(
      id = PeriodicProcessDeploymentId(Random.nextLong()),
      periodicProcessId = id,
      createdAt = LocalDateTime.now(),
      runAt = ZonedDateTime.now(),
      deployedAt = None,
      completedAt = None,
      status = deploymentStatus
    )
  }

  override def markInactive(processName: ProcessName): Future[Unit] = Future.successful {
    processEntities
      .zipWithIndex
      .find(processWithIndex => activeProcess(processName)(processWithIndex._1))
      .map { case (process, index) =>
        processEntities.update(index, process.copy(active = false))
      }
  }

  override def create(deploymentWithJarData: DeploymentWithJarData, scheduleName: String, periodicProperty: PeriodicProperty, runAt: ZonedDateTime): Future[Unit] = {
    val id = PeriodicProcessId(Random.nextLong())
    processEntities += PeriodicProcessEntity(
      id = id,
      processName = deploymentWithJarData.processVersion.processName.value,
      processVersionId = deploymentWithJarData.processVersion.versionId,
      processJson = deploymentWithJarData.processJson,
      modelConfig = deploymentWithJarData.modelConfig,
      jarFileName = deploymentWithJarData.jarFileName,
      periodicProperty = periodicProperty.asJson.noSpaces,
      scheduleName = scheduleName,
      active = true,
      createdAt = LocalDateTime.now()
    )
    deploymentEntities += PeriodicProcessDeploymentEntity(
      id = PeriodicProcessDeploymentId(-1),
      periodicProcessId = id,
      createdAt = LocalDateTime.now(),
      runAt = runAt,
      deployedAt = None,
      completedAt = None,
      status = PeriodicProcessDeploymentStatus.Scheduled
    )
    Future.successful(())
  }

  override def getScheduledRunDetails(processName: ProcessName): Future[Option[ScheduledRunDetails]] = Future.successful {
    for {
      process <- processEntities.find(activeProcess(processName))
      deployment <- deploymentEntities.find(_.periodicProcessId == process.id)
    } yield ScheduledRunDetails(process, deployment)
  }

  override def findToBeDeployed: Future[Seq[ScheduledRunDetails]] = ???

  override def findDeployed: Future[Seq[ScheduledRunDetails]] = Future.successful {
    for {
      p <- processEntities if p.active
      d <- deploymentEntities if d.periodicProcessId == p.id && d.status == PeriodicProcessDeploymentStatus.Deployed
    } yield ScheduledRunDetails(p, d)
  }

  override def findProcessData(id: PeriodicProcessDeploymentId): Future[DeploymentWithJarData] = Future.successful {
    (for {
      d <- deploymentEntities if d.id == id
      p <- processEntities if p.id == d.periodicProcessId
    } yield createDeploymentWithJarData(p)).head
  }

  override def findProcessData(processName: ProcessName): Future[Seq[DeploymentWithJarData]] = Future.successful {
    processEntities
      .filter(activeProcess(processName))
      .map(createDeploymentWithJarData)
  }

  override def markDeployed(id: PeriodicProcessDeploymentId): Future[Unit] = {
    update(id)(_.copy(status = PeriodicProcessDeploymentStatus.Deployed, deployedAt = Some(LocalDateTime.now())))
  }

  override def markFinished(id: PeriodicProcessDeploymentId): Future[Unit] = {
    update(id)(_.copy(status = PeriodicProcessDeploymentStatus.Finished, completedAt = Some(LocalDateTime.now())))
  }

  override def markFailed(id: PeriodicProcessDeploymentId): Future[Unit] = {
    update(id)(_.copy(status = PeriodicProcessDeploymentStatus.Failed, completedAt = Some(LocalDateTime.now())))
  }

  override def schedule(id: PeriodicProcessId, runAt: ZonedDateTime): Future[Unit] = Future.successful {
    val deploymentEntity = PeriodicProcessDeploymentEntity(
      id = PeriodicProcessDeploymentId(Random.nextLong()),
      periodicProcessId = id,
      createdAt = LocalDateTime.now(),
      runAt = runAt,
      deployedAt = None,
      completedAt = None,
      status = PeriodicProcessDeploymentStatus.Scheduled
    )
    deploymentEntities += deploymentEntity
  }

  private def createDeploymentWithJarData(processEntity: PeriodicProcessEntity): DeploymentWithJarData = {
    val processVersion = ProcessVersion.empty.copy(versionId = processEntity.processVersionId, processName = ProcessName(processEntity.processName))
    jar.DeploymentWithJarData(
      processVersion = processVersion,
      processJson = processEntity.processJson,
      modelConfig = processEntity.modelConfig,
      jarFileName = processEntity.jarFileName
    )
  }

  private def update(id: PeriodicProcessDeploymentId)(action: PeriodicProcessDeploymentEntity => PeriodicProcessDeploymentEntity): Future[Unit] = Future.successful {
    deploymentEntities
      .zipWithIndex
      .find { case (deployment, _) => deployment.id == id }
      .map { case (deployment, index) =>
        deploymentEntities.update(index, action(deployment))
      }
  }

  private def activeProcess(processName: ProcessName) = (process: PeriodicProcessEntity) => process.active && process.processName == processName.value
}

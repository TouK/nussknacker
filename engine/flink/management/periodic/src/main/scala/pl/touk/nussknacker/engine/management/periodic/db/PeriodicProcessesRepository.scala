package pl.touk.nussknacker.engine.management.periodic.db

import com.typesafe.scalalogging.LazyLogging
import io.circe.parser.decode
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.periodic.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic._
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{JdbcBackend, JdbcProfile}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}


trait PeriodicProcessesRepository {
  def markInactive(processName: ProcessName): Future[Unit]

  def create(deploymentWithJarData: DeploymentWithJarData,
             periodicProperty: PeriodicProperty,
             runAt: LocalDateTime): Future[Unit]

  def getScheduledRunDetails(processName: ProcessName): Future[Option[(ScheduledRunDetails, PeriodicProcessDeploymentStatus)]]

  def findToBeDeployed: Future[Seq[ScheduledRunDetails]]

  def findDeployed: Future[Seq[ScheduledRunDetails]]

  def findProcessData(id: PeriodicProcessDeploymentId): Future[DeploymentWithJarData]

  def findProcessData(processName: ProcessName): Future[Seq[DeploymentWithJarData]]

  def markDeployed(id: PeriodicProcessDeploymentId): Future[Unit]

  def markFinished(id: PeriodicProcessDeploymentId): Future[Unit]

  def markFailed(id: PeriodicProcessDeploymentId): Future[Unit]

  def schedule(id: PeriodicProcessId, runAt: LocalDateTime): Future[Unit]
}

class SlickPeriodicProcessesRepository(db: JdbcBackend.DatabaseDef,
                                       override val profile: JdbcProfile)
                                      (implicit ec: ExecutionContext)
  extends PeriodicProcessesRepository
    with PeriodicProcessesTableFactory
    with PeriodicProcessDeploymentsTableFactory with LazyLogging {

  import io.circe.syntax._

  override def create(deploymentWithJarData: DeploymentWithJarData,
                      periodicProperty: PeriodicProperty,
                      runAt: LocalDateTime): Future[Unit] = {
    val processEntity = PeriodicProcessEntity(
      id = PeriodicProcessId(-1),
      processName = deploymentWithJarData.processVersion.processName.value,
      processVersionId = deploymentWithJarData.processVersion.versionId,
      processJson = deploymentWithJarData.processJson,
      modelConfig = deploymentWithJarData.modelConfig,
      jarFileName = deploymentWithJarData.jarFileName,
      periodicProperty = periodicProperty.asJson.noSpaces,
      active = true,
      createdAt = LocalDateTime.now()
    )
    val createAction = for {
      periodicProcessId <- (PeriodicProcesses returning PeriodicProcesses.map(_.id) into ((_, id) => id)) += processEntity
      deploymentEntity = PeriodicProcessDeploymentEntity(
        id = PeriodicProcessDeploymentId(-1),
        periodicProcessId = periodicProcessId,
        createdAt = LocalDateTime.now(),
        runAt = runAt,
        deployedAt = None,
        completedAt = None,
        status = PeriodicProcessDeploymentStatus.Scheduled
      )
      _ <- PeriodicProcessDeployments += deploymentEntity
    } yield ()
    db.run(createAction)
  }

  override def findToBeDeployed: Future[Seq[ScheduledRunDetails]] = {
    val active = (PeriodicProcesses join PeriodicProcessDeployments on (_.id === _.periodicProcessId))
      .filter { case (p, _) => p.active === true }
      .filter { case (_, d) => d.runAt <= LocalDateTime.now() && d.status === (PeriodicProcessDeploymentStatus.Scheduled: PeriodicProcessDeploymentStatus) }
    db.run(active.result).map(_.map {
      //pobieranie wiecej danych??
      case (p, d) => ScheduledRunDetails(p, d)
    })
  }

  override def findProcessData(id: PeriodicProcessDeploymentId): Future[DeploymentWithJarData] = {
    val processWithDeployment = (PeriodicProcesses join PeriodicProcessDeployments on (_.id === _.periodicProcessId))
      .filter { case (_, deployment) => deployment.id === id }
    db.run(processWithDeployment.result.head).map { case (process, _) => createDeploymentWithJarData(process) }
  }

  override def findProcessData(processName: ProcessName): Future[Seq[DeploymentWithJarData]] = {
    db.run(PeriodicProcesses.filter(p => p.active === true && p.processName === processName.value).result)
      .map(maybeProcessEntity => maybeProcessEntity.map(createDeploymentWithJarData))
  }

  override def markDeployed(id: PeriodicProcessDeploymentId): Future[Unit] = {
    val q = for {
      d <- PeriodicProcessDeployments if d.id === id
    } yield (d.status, d.deployedAt)
    val update = q.update((PeriodicProcessDeploymentStatus.Deployed, Some(LocalDateTime.now())))
    db.run(update).map(_ => ())
  }

  override def markFailed(id: PeriodicProcessDeploymentId): Future[Unit] = {
    val q = for {
      d <- PeriodicProcessDeployments if d.id === id
    } yield (d.status, d.completedAt)
    val update = q.update((PeriodicProcessDeploymentStatus.Failed, Some(LocalDateTime.now())))
    db.run(update).map(_ => ())
  }

  override def markFinished(id: PeriodicProcessDeploymentId): Future[Unit] = {
    val q = for {
      d <- PeriodicProcessDeployments if d.id === id
    } yield (d.status, d.completedAt)
    val update = q.update((PeriodicProcessDeploymentStatus.Finished, Some(LocalDateTime.now())))
    db.run(update).map(_ => ())
  }

  override def getScheduledRunDetails(processName: ProcessName): Future[Option[(ScheduledRunDetails, PeriodicProcessDeploymentStatus)]] = {
    val processWithDeployment = (PeriodicProcesses join PeriodicProcessDeployments on (_.id === _.periodicProcessId))
      .filter { case (p, _) => (p.active === true) && (p.processName === processName.value) }
      .sortBy { case (_, d) => d.createdAt.desc }
    db.run(processWithDeployment.result).map(_.map {
      //pobieranie wiecej danych??
      case (p, d) => (ScheduledRunDetails(p, d), d.status)
    }).map(_.headOption)
  }

  override def schedule(id: PeriodicProcessId, runAt: LocalDateTime): Future[Unit] = {
    val deploymentEntity = PeriodicProcessDeploymentEntity(
      id = PeriodicProcessDeploymentId(-1),
      periodicProcessId = id,
      createdAt = LocalDateTime.now(),
      runAt = runAt,
      deployedAt = None,
      completedAt = None,
      status = PeriodicProcessDeploymentStatus.Scheduled
    )
    db.run((PeriodicProcessDeployments += deploymentEntity).map(_ => ()))
  }

  override def markInactive(processName: ProcessName): Future[Unit] = {
    val q = for {
      p <- PeriodicProcesses if p.processName === processName.value && p.active === true
    } yield p.active
    val update = q.update(false)
    db.run(update).map(_ => ())
  }

  override def findDeployed: Future[Seq[ScheduledRunDetails]] = {
    val processWithDeployment = (PeriodicProcesses join PeriodicProcessDeployments on (_.id === _.periodicProcessId))
      .filter { case (p, d) => (p.active === true) && (d.status === (PeriodicProcessDeploymentStatus.Deployed: PeriodicProcessDeploymentStatus)) }
    db.run(processWithDeployment.result).map(_.map {
      case (p, d) => ScheduledRunDetails(p, d)
    })
  }

  private def createDeploymentWithJarData(processEntity: PeriodicProcessEntity): DeploymentWithJarData = {
    val processVersion = ProcessVersion.empty.copy(versionId = processEntity.processVersionId, processName = ProcessName(processEntity.processName))
    DeploymentWithJarData(
      processVersion = processVersion,
      processJson = processEntity.processJson,
      modelConfig = processEntity.modelConfig,
      jarFileName = processEntity.jarFileName
    )
  }
}

object ScheduledRunDetails {

  def apply(process: PeriodicProcessEntity, deployment: PeriodicProcessDeploymentEntity): ScheduledRunDetails = {
    val processName =  ProcessName(process.processName)
    val periodicProperty = decode[PeriodicProperty](process.periodicProperty).right.get
    ScheduledRunDetails(
      process.id, processName, ProcessVersion(process.processVersionId, processName, "scheduler", None), periodicProperty,
      deployment.id, deployment.runAt
    )
  }

}

case class ScheduledRunDetails(periodicProcessId: PeriodicProcessId,
                               processName: ProcessName,
                               processVersion: ProcessVersion,
                               periodicProperty: PeriodicProperty,
                               processDeploymentId: PeriodicProcessDeploymentId,
                               runAt: LocalDateTime)

case class DeployedProcess(
                            processName: ProcessName,
                            deploymentId: PeriodicProcessDeploymentId,
                            periodicProcessId: PeriodicProcessId,
                            periodicProperty: PeriodicProperty
                          )

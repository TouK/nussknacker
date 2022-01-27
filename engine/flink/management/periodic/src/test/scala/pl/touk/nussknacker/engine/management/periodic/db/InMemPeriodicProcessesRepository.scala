package pl.touk.nussknacker.engine.management.periodic.db

import cats.{Id, Monad}
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.management.periodic._
import pl.touk.nussknacker.engine.management.periodic.db.InMemPeriodicProcessesRepository.{DeploymentIdSequence, ProcessIdSequence}
import pl.touk.nussknacker.engine.management.periodic.db.PeriodicProcessesRepository.createPeriodicProcessDeployment
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic.model._
import pl.touk.nussknacker.engine.marshall.ScenarioParser

import java.time.chrono.ChronoLocalDateTime
import java.time.{LocalDateTime, ZoneId}
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.util.Random

object InMemPeriodicProcessesRepository {
  private val ProcessIdSequence = new AtomicLong(0)
  private val DeploymentIdSequence = new AtomicLong(0)
}

class InMemPeriodicProcessesRepository extends PeriodicProcessesRepository {

  var processEntities: mutable.ListBuffer[PeriodicProcessEntity] = ListBuffer.empty
  var deploymentEntities: mutable.ListBuffer[PeriodicProcessDeploymentEntity] = ListBuffer.empty

  private implicit val localDateOrdering: Ordering[LocalDateTime] = Ordering.by(identity[ChronoLocalDateTime[_]])

  override type Action[T] = Id[T]

  override implicit def monad: Monad[Id] = cats.catsInstancesForId

  override def run[T](action: Id[T]): Future[T] = Future.successful(action)

  def addActiveProcess(processName: ProcessName,
                       deploymentStatus: PeriodicProcessDeploymentStatus,
                       scheduleProperty: SingleScheduleProperty = CronScheduleProperty("0 0 * * * ?"),
                       deployMaxRetries: Int = 0): PeriodicProcessDeploymentId = {
    val periodicProcessId = addOnlyProcess(processName, scheduleProperty)
    addOnlyDeployment(periodicProcessId, deploymentStatus, deployMaxRetries = deployMaxRetries)
  }

  def addOnlyProcess(processName: ProcessName,
                     scheduleProperty: ScheduleProperty = CronScheduleProperty("0 0 * * * ?")): PeriodicProcessId = {
    import pl.touk.nussknacker.engine.spel.Implicits.asSpelExpression

    val id = PeriodicProcessId(ProcessIdSequence.incrementAndGet())
    val entity = PeriodicProcessEntity(
      id = id,
      processName = processName,
      processVersionId = VersionId.initialVersionId,
      processJson = ScenarioParser.toGraphProcess(
        EspProcessBuilder
          .id(processName.value)
          .source("start", "source")
          .emptySink("end", "KafkaSink")
      ).marshalled,
      inputConfigDuringExecutionJson = "{}",
      jarFileName = "",
      scheduleProperty = scheduleProperty.asJson.noSpaces,
      active = true,
      createdAt = LocalDateTime.now()
    )
    processEntities += entity
    id
  }

  def addOnlyDeployment(periodicProcessId: PeriodicProcessId,
                        status: PeriodicProcessDeploymentStatus,
                        runAt: LocalDateTime = LocalDateTime.now(),
                        deployMaxRetries: Int = 0,
                        scheduleName: Option[String] = None): PeriodicProcessDeploymentId = {
    val id = PeriodicProcessDeploymentId(DeploymentIdSequence.incrementAndGet())
    val entity = PeriodicProcessDeploymentEntity(
      id = id,
      periodicProcessId = periodicProcessId,
      createdAt = LocalDateTime.now(),
      runAt = runAt,
      scheduleName = scheduleName,
      deployedAt = None,
      completedAt = None,
      status = status,
      retriesLeft = deployMaxRetries,
      nextRetryAt = None
    )
    deploymentEntities += entity
    id
  }

  override def markInactive(processName: ProcessName): Unit =
    processEntities
      .zipWithIndex
      .find(processWithIndex => activeProcess(processName)(processWithIndex._1))
      .foreach { case (process, index) =>
        processEntities.update(index, process.copy(active = false))
      }

  override def create(deploymentWithJarData: DeploymentWithJarData, scheduleProperty: ScheduleProperty): PeriodicProcess = {
    val id = PeriodicProcessId(Random.nextLong())
    val periodicProcess = PeriodicProcessEntity(
      id = id,
      processName = deploymentWithJarData.processVersion.processName,
      processVersionId = deploymentWithJarData.processVersion.versionId,
      processJson = deploymentWithJarData.graphProcess.marshalled,
      inputConfigDuringExecutionJson = deploymentWithJarData.inputConfigDuringExecutionJson,
      jarFileName = deploymentWithJarData.jarFileName,
      scheduleProperty = scheduleProperty.asJson.noSpaces,
      active = true,
      createdAt = LocalDateTime.now()
    )
    processEntities += periodicProcess
    PeriodicProcessesRepository.createPeriodicProcess(periodicProcess)
  }

  override def getLatestDeploymentForEachSchedule(processName: ProcessName): List[PeriodicProcessDeployment] =
    (for {
      process <- processEntities.filter(activeProcess(processName))
      deployment <- deploymentEntities
        .filter(_.periodicProcessId == process.id)
        .groupBy(_.scheduleName).values.map(_.maxBy(_.runAt.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli))
    } yield createPeriodicProcessDeployment(process, deployment)).toList

  override def findToBeDeployed: Seq[PeriodicProcessDeployment] = {
    val scheduled = findActive(PeriodicProcessDeploymentStatus.Scheduled)
    readyToRun(scheduled)
  }

  override def findToBeRetried: Action[Seq[PeriodicProcessDeployment]] = {
    val toBeRetried = findActive(PeriodicProcessDeploymentStatus.FailedOnDeploy).filter(_.retriesLeft > 0)
    readyToRun(toBeRetried)
  }

  override def findDeployed: Seq[PeriodicProcessDeployment] =
    findActive(PeriodicProcessDeploymentStatus.Deployed)

  override def findScheduled(id: PeriodicProcessId): Seq[PeriodicProcessDeployment] =
    findActive(PeriodicProcessDeploymentStatus.Scheduled)

  override def findProcessData(id: PeriodicProcessDeploymentId): PeriodicProcessDeployment =
    (for {
      d <- deploymentEntities if d.id == id
      p <- processEntities if p.id == d.periodicProcessId
    } yield createPeriodicProcessDeployment(p, d)).head

  override def findProcessData(processName: ProcessName): Seq[PeriodicProcess] =
    processEntities
      .filter(activeProcess(processName))
      .map(PeriodicProcessesRepository.createPeriodicProcess)

  override def markDeployed(id: PeriodicProcessDeploymentId): Unit = {
    update(id)(_.copy(status = PeriodicProcessDeploymentStatus.Deployed, deployedAt = Some(LocalDateTime.now())))
  }

  override def markFinished(id: PeriodicProcessDeploymentId): Unit = {
    update(id)(_.copy(status = PeriodicProcessDeploymentStatus.Finished, completedAt = Some(LocalDateTime.now())))
  }

  override def markFailedOnDeploy(id: PeriodicProcessDeploymentId, deployRetries: Int, retryAt: Option[LocalDateTime]): Action[Unit] = {
    update(id)(_.copy(
      status = PeriodicProcessDeploymentStatus.FailedOnDeploy,
      completedAt = Some(LocalDateTime.now()),
      retriesLeft = deployRetries,
      nextRetryAt = retryAt
    ))
  }

  override def markFailed(id: PeriodicProcessDeploymentId): Unit = {
    update(id)(_.copy(
      status = PeriodicProcessDeploymentStatus.Failed,
      completedAt = Some(LocalDateTime.now())
    ))
  }

  override def schedule(id: PeriodicProcessId, scheduleName: Option[String], runAt: LocalDateTime, deployMaxRetries: Int): PeriodicProcessDeployment = {
    val deploymentEntity = PeriodicProcessDeploymentEntity(
      id = PeriodicProcessDeploymentId(Random.nextLong()),
      periodicProcessId = id,
      createdAt = LocalDateTime.now(),
      runAt = runAt,
      scheduleName = scheduleName,
      deployedAt = None,
      completedAt = None,
      retriesLeft = deployMaxRetries,
      nextRetryAt = None,
      status = PeriodicProcessDeploymentStatus.Scheduled
    )
    deploymentEntities += deploymentEntity
    createPeriodicProcessDeployment(processEntities.find(_.id == id).head ,deploymentEntity)
  }

  private def update(id: PeriodicProcessDeploymentId)(action: PeriodicProcessDeploymentEntity => PeriodicProcessDeploymentEntity): Unit = {
    deploymentEntities
      .zipWithIndex
      .find { case (deployment, _) => deployment.id == id }
      .foreach { case (deployment, index) =>
        deploymentEntities.update(index, action(deployment))
      }
  }

  private def findActive(status: PeriodicProcessDeploymentStatus): Seq[PeriodicProcessDeployment] =
    for {
      p <- processEntities if p.active
      d <- deploymentEntities if d.periodicProcessId == p.id && d.status == status
    } yield createPeriodicProcessDeployment(p, d)

  private def readyToRun(deployments: Seq[PeriodicProcessDeployment]): Seq[PeriodicProcessDeployment] = {
    val now = LocalDateTime.now()
    deployments.filter(d => d.runAt.isBefore(now) || d.runAt.isEqual(now))
  }

  private def activeProcess(processName: ProcessName) = (process: PeriodicProcessEntity) => process.active && process.processName == processName
}

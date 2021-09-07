package pl.touk.nussknacker.engine.management.periodic.db

import cats.{Id, Monad}
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.management.periodic._
import pl.touk.nussknacker.engine.management.periodic.db.InMemPeriodicProcessesRepository.{DeploymentIdSequence, ProcessIdSequence}
import pl.touk.nussknacker.engine.management.periodic.db.PeriodicProcessesRepository.createPeriodicProcessDeployment
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic.model._
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

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
                       scheduleProperty: SingleScheduleProperty = CronScheduleProperty("0 0 * * * ?")): Unit = {
    val periodicProcessId = addOnlyProcess(processName, scheduleProperty)
    addOnlyDeployment(periodicProcessId, deploymentStatus)
  }

  def addOnlyProcess(processName: ProcessName,
                     scheduleProperty: ScheduleProperty = CronScheduleProperty("0 0 * * * ?")): PeriodicProcessId = {
    import pl.touk.nussknacker.engine.spel.Implicits.asSpelExpression

    val id = PeriodicProcessId(ProcessIdSequence.incrementAndGet())
    val entity = PeriodicProcessEntity(
      id = id,
      processName = processName.value,
      processVersionId = 1,
      processJson = ProcessMarshaller.toJson(ProcessCanonizer.canonize(
        EspProcessBuilder
          .id(processName.value)
          .exceptionHandler()
          .source("start", "source")
          .sink("end", "#input", "KafkaSink")
      )).noSpaces,
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
                        scheduleName: Option[String] = None): PeriodicProcessDeploymentId = {
    val id = PeriodicProcessDeploymentId(DeploymentIdSequence.incrementAndGet())
    val entity = PeriodicProcessDeploymentEntity(
      id = id,
      periodicProcessId = periodicProcessId,
      createdAt = LocalDateTime.now(),
      runAt = LocalDateTime.now(),
      scheduleName = scheduleName,
      deployedAt = None,
      completedAt = None,
      status = status
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
      processName = deploymentWithJarData.processVersion.processName.value,
      processVersionId = deploymentWithJarData.processVersion.versionId,
      processJson = deploymentWithJarData.processJson,
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

  override def findToBeDeployed: Seq[PeriodicProcessDeployment]= ???

  override def findDeployed: Seq[PeriodicProcessDeployment] =
    for {
      p <- processEntities if p.active
      d <- deploymentEntities if d.periodicProcessId == p.id && d.status == PeriodicProcessDeploymentStatus.Deployed
    } yield createPeriodicProcessDeployment(p, d)

  override def findScheduled(id: PeriodicProcessId): Seq[PeriodicProcessDeployment] = {
    for {
      p <- processEntities if p.active && p.id == id
      d <- deploymentEntities if d.periodicProcessId == p.id && d.status == PeriodicProcessDeploymentStatus.Scheduled
    } yield createPeriodicProcessDeployment(p, d)
  }

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

  override def markFailed(id: PeriodicProcessDeploymentId): Unit = {
    update(id)(_.copy(status = PeriodicProcessDeploymentStatus.Failed, completedAt = Some(LocalDateTime.now())))
  }

  override def schedule(id: PeriodicProcessId, scheduleName: Option[String], runAt: LocalDateTime): PeriodicProcessDeployment = {
    val deploymentEntity = PeriodicProcessDeploymentEntity(
      id = PeriodicProcessDeploymentId(Random.nextLong()),
      periodicProcessId = id,
      createdAt = LocalDateTime.now(),
      runAt = runAt,
      scheduleName = scheduleName,
      deployedAt = None,
      completedAt = None,
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

  private def activeProcess(processName: ProcessName) = (process: PeriodicProcessEntity) => process.active && process.processName == processName.value
}

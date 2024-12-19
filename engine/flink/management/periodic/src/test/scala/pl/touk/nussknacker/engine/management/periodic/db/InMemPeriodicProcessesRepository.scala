package pl.touk.nussknacker.engine.management.periodic.db

import cats.{Id, Monad}
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.management.periodic._
import pl.touk.nussknacker.engine.management.periodic.db.InMemPeriodicProcessesRepository.{
  DeploymentIdSequence,
  ProcessIdSequence,
  getLatestDeploymentQueryCount
}
import pl.touk.nussknacker.engine.management.periodic.db.PeriodicProcessesRepository.createPeriodicProcessDeployment
import pl.touk.nussknacker.engine.management.periodic.model.DeploymentWithJarData.WithCanonicalProcess
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic.model._

import java.time.chrono.ChronoLocalDateTime
import java.time.{LocalDateTime, ZoneId}
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.util.Random

object InMemPeriodicProcessesRepository {
  private val ProcessIdSequence    = new AtomicLong(0)
  private val DeploymentIdSequence = new AtomicLong(0)

  val getLatestDeploymentQueryCount = new AtomicLong(0)
}

class InMemPeriodicProcessesRepository(processingType: String) extends PeriodicProcessesRepository {

  var processEntities: mutable.ListBuffer[PeriodicProcessEntityWithJson]      = ListBuffer.empty
  var deploymentEntities: mutable.ListBuffer[PeriodicProcessDeploymentEntity] = ListBuffer.empty

  private implicit val localDateOrdering: Ordering[LocalDateTime] = Ordering.by(identity[ChronoLocalDateTime[_]])

  override type Action[T] = Id[T]

  override implicit def monad: Monad[Id] = cats.catsInstancesForId

  override def run[T](action: Id[T]): Future[T] = Future.successful(action)

  def addActiveProcess(
      processName: ProcessName,
      deploymentStatus: PeriodicProcessDeploymentStatus,
      scheduleProperty: SingleScheduleProperty = CronScheduleProperty("0 0 * * * ?"),
      deployMaxRetries: Int = 0,
      processingType: String = processingType,
      processActionId: Option[ProcessActionId] = None,
      runAt: LocalDateTime = LocalDateTime.now(),
      deployedAt: Option[LocalDateTime] = None
  ): PeriodicProcessDeploymentId = {
    val periodicProcessId = addOnlyProcess(processName, scheduleProperty, processingType, processActionId)
    addOnlyDeployment(
      periodicProcessId,
      deploymentStatus,
      deployMaxRetries = deployMaxRetries,
      runAt = runAt,
      deployedAt = deployedAt
    )
  }

  def addOnlyProcess(
      processName: ProcessName,
      scheduleProperty: ScheduleProperty = CronScheduleProperty("0 0 * * * ?"),
      processingType: String = processingType,
      processActionId: Option[ProcessActionId] = None
  ): PeriodicProcessId = {
    val id = PeriodicProcessId(ProcessIdSequence.incrementAndGet())
    val entity = PeriodicProcessEntityWithJson(
      id = id,
      processName = processName,
      processVersionId = VersionId.initialVersionId,
      processingType = processingType,
      processJson = ScenarioBuilder
        .streaming(processName.value)
        .source("start", "source")
        .emptySink("end", "KafkaSink"),
      inputConfigDuringExecutionJson = "{}",
      jarFileName = "",
      scheduleProperty = scheduleProperty.asJson.noSpaces,
      active = true,
      createdAt = LocalDateTime.now(),
      processActionId = processActionId
    )
    processEntities += entity
    id
  }

  def addOnlyDeployment(
      periodicProcessId: PeriodicProcessId,
      status: PeriodicProcessDeploymentStatus,
      runAt: LocalDateTime = LocalDateTime.now(),
      deployMaxRetries: Int = 0,
      scheduleName: Option[String] = None,
      deployedAt: Option[LocalDateTime] = None,
  ): PeriodicProcessDeploymentId = {
    val id = PeriodicProcessDeploymentId(DeploymentIdSequence.incrementAndGet())
    val entity = PeriodicProcessDeploymentEntity(
      id = id,
      periodicProcessId = periodicProcessId,
      createdAt = LocalDateTime.now(),
      runAt = runAt,
      scheduleName = scheduleName,
      deployedAt = deployedAt,
      completedAt = None,
      status = status,
      retriesLeft = deployMaxRetries,
      nextRetryAt = None
    )
    deploymentEntities += entity
    id
  }

  override def getSchedulesState(
      scenarioName: ProcessName,
      after: Option[LocalDateTime],
  ): Action[SchedulesState] = {
    val filteredProcesses = processEntities.filter { pe =>
      pe.processName == scenarioName && deploymentEntities.exists(d => d.periodicProcessId == pe.id)
    }.toSeq
    getLatestDeploymentsForPeriodicProcesses(filteredProcesses, deploymentsPerScheduleMaxCount = Int.MaxValue)
  }

  override def markInactive(processId: PeriodicProcessId): Unit =
    processEntities.zipWithIndex
      .find { case (process, _) => process.id == processId }
      .foreach { case (process, index) =>
        processEntities.update(index, process.copy(active = false))
      }

  override def create(
      deploymentWithJarData: DeploymentWithJarData.WithCanonicalProcess,
      scheduleProperty: ScheduleProperty,
      processActionId: ProcessActionId,
  ): PeriodicProcess[WithCanonicalProcess] = {
    val id = PeriodicProcessId(Random.nextLong())
    val periodicProcess = PeriodicProcessEntityWithJson(
      id = id,
      processName = deploymentWithJarData.processVersion.processName,
      processVersionId = deploymentWithJarData.processVersion.versionId,
      processingType = processingType,
      processJson = deploymentWithJarData.process,
      inputConfigDuringExecutionJson = deploymentWithJarData.inputConfigDuringExecutionJson,
      jarFileName = deploymentWithJarData.jarFileName,
      scheduleProperty = scheduleProperty.asJson.noSpaces,
      active = true,
      createdAt = LocalDateTime.now(),
      processActionId = Some(processActionId)
    )
    processEntities += periodicProcess
    PeriodicProcessesRepository.createPeriodicProcessWithJson(periodicProcess)
  }

  override def findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
      expectedDeploymentStatuses: Set[PeriodicProcessDeploymentStatus]
  ): Action[SchedulesState] = {
    val filteredProcesses = processEntities.filter { pe =>
      pe.processingType == processingType &&
      deploymentEntities.exists(d => d.periodicProcessId == pe.id && expectedDeploymentStatuses.contains(d.status))
    }.toSeq
    getLatestDeploymentsForPeriodicProcesses(filteredProcesses, deploymentsPerScheduleMaxCount = 1)
  }

  override def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int
  ): Action[SchedulesState] = {
    getLatestDeploymentQueryCount.incrementAndGet()
    getLatestDeploymentsForPeriodicProcesses(
      processEntities(processName).filter(_.active),
      deploymentsPerScheduleMaxCount
    )
  }

  override def getLatestDeploymentsForActiveSchedules(
      deploymentsPerScheduleMaxCount: Int
  ): Action[Map[ProcessName, SchedulesState]] = {
    getLatestDeploymentQueryCount.incrementAndGet()
    allProcessEntities.map { case (processName, list) =>
      processName -> getLatestDeploymentsForPeriodicProcesses(
        list.filter(_.active),
        deploymentsPerScheduleMaxCount
      )
    }
  }

  override def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int
  ): Action[SchedulesState] = {
    getLatestDeploymentQueryCount.incrementAndGet()
    val filteredProcesses =
      processEntities(processName).filterNot(_.active).sortBy(_.createdAt).takeRight(inactiveProcessesMaxCount)
    getLatestDeploymentsForPeriodicProcesses(filteredProcesses, deploymentsPerScheduleMaxCount)
  }

  override def getLatestDeploymentsForLatestInactiveSchedules(
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int
  ): Action[Map[ProcessName, SchedulesState]] = {
    getLatestDeploymentQueryCount.incrementAndGet()
    allProcessEntities.map { case (processName, list) =>
      processName -> getLatestDeploymentsForPeriodicProcesses(
        list.filterNot(_.active).sortBy(_.createdAt).takeRight(inactiveProcessesMaxCount),
        deploymentsPerScheduleMaxCount
      )
    }
  }

  private def getLatestDeploymentsForPeriodicProcesses(
      processes: Seq[PeriodicProcessEntity],
      deploymentsPerScheduleMaxCount: Int
  ): SchedulesState =
    SchedulesState((for {
      process <- processes
      deploymentGroupedByScheduleName <- deploymentEntities
        .filter(_.periodicProcessId == process.id)
        .groupBy(_.scheduleName)
        .map { case (scheduleName, deployments) =>
          val scheduleId = ScheduleId(process.id, ScheduleName(scheduleName))
          val ds = deployments
            .sortBy(d => -d.runAt.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli)
            .take(deploymentsPerScheduleMaxCount)
            .map(ScheduleDeploymentData(_))
            .toList
          scheduleId -> ScheduleData(PeriodicProcessesRepository.createPeriodicProcessWithoutJson(process), ds)
        }
    } yield deploymentGroupedByScheduleName).toMap)

  override def findToBeDeployed: Seq[PeriodicProcessDeployment[WithCanonicalProcess]] = {
    val scheduled = findActive(PeriodicProcessDeploymentStatus.Scheduled)
    readyToRun(scheduled)
  }

  override def findToBeRetried: Action[Seq[PeriodicProcessDeployment[WithCanonicalProcess]]] = {
    val toBeRetried = findActive(PeriodicProcessDeploymentStatus.FailedOnDeploy).filter(_.retriesLeft > 0)
    readyToRun(toBeRetried)
  }

  override def findProcessData(id: PeriodicProcessDeploymentId): PeriodicProcessDeployment[WithCanonicalProcess] =
    (for {
      d <- deploymentEntities if d.id == id
      p <- processEntities if p.id == d.periodicProcessId
    } yield createPeriodicProcessDeployment(p, d)).head

  override def findProcessData(processName: ProcessName): Seq[PeriodicProcess[WithCanonicalProcess]] =
    processEntities(processName)
      .filter(_.active)
      .map(PeriodicProcessesRepository.createPeriodicProcessWithJson)

  private def allProcessEntities: Map[ProcessName, Seq[PeriodicProcessEntity]] =
    processEntities
      .filter(process => process.processingType == processingType)
      .toSeq
      .groupBy(_.processName)

  private def processEntities(processName: ProcessName): Seq[PeriodicProcessEntityWithJson] =
    processEntities
      .filter(process => process.processName == processName && process.processingType == processingType)
      .toSeq

  override def markDeployed(id: PeriodicProcessDeploymentId): Unit = {
    update(id)(_.copy(status = PeriodicProcessDeploymentStatus.Deployed, deployedAt = Some(LocalDateTime.now())))
  }

  override def markFinished(id: PeriodicProcessDeploymentId): Unit = {
    update(id)(_.copy(status = PeriodicProcessDeploymentStatus.Finished, completedAt = Some(LocalDateTime.now())))
  }

  override def markFailedOnDeployWithStatus(
      id: PeriodicProcessDeploymentId,
      status: PeriodicProcessDeploymentStatus,
      deployRetries: Int,
      retryAt: Option[LocalDateTime]
  ): Action[Unit] = {
    update(id)(
      _.copy(
        status = status,
        completedAt = Some(LocalDateTime.now()),
        retriesLeft = deployRetries,
        nextRetryAt = retryAt
      )
    )
  }

  override def markFailed(id: PeriodicProcessDeploymentId): Unit = {
    update(id)(
      _.copy(
        status = PeriodicProcessDeploymentStatus.Failed,
        completedAt = Some(LocalDateTime.now())
      )
    )
  }

  override def schedule(
      id: PeriodicProcessId,
      scheduleName: ScheduleName,
      runAt: LocalDateTime,
      deployMaxRetries: Int
  ): PeriodicProcessDeployment[WithCanonicalProcess] = {
    val deploymentEntity = PeriodicProcessDeploymentEntity(
      id = PeriodicProcessDeploymentId(Random.nextLong()),
      periodicProcessId = id,
      createdAt = LocalDateTime.now(),
      runAt = runAt,
      scheduleName = scheduleName.value,
      deployedAt = None,
      completedAt = None,
      retriesLeft = deployMaxRetries,
      nextRetryAt = None,
      status = PeriodicProcessDeploymentStatus.Scheduled
    )
    deploymentEntities += deploymentEntity
    createPeriodicProcessDeployment(processEntities.find(_.id == id).head, deploymentEntity)
  }

  private def update(
      id: PeriodicProcessDeploymentId
  )(action: PeriodicProcessDeploymentEntity => PeriodicProcessDeploymentEntity): Unit = {
    deploymentEntities.zipWithIndex
      .find { case (deployment, _) => deployment.id == id }
      .foreach { case (deployment, index) =>
        deploymentEntities.update(index, action(deployment))
      }
  }

  private def findActive(
      status: PeriodicProcessDeploymentStatus
  ): Seq[PeriodicProcessDeployment[WithCanonicalProcess]] =
    findActive(
      Seq(status)
    )

  private def findActive(
      statusList: Seq[PeriodicProcessDeploymentStatus]
  ): Seq[PeriodicProcessDeployment[WithCanonicalProcess]] =
    (for {
      p <- processEntities if p.active && p.processingType == processingType
      d <- deploymentEntities if d.periodicProcessId == p.id && statusList.contains(d.status)
    } yield createPeriodicProcessDeployment(p, d)).toSeq

  private def readyToRun(
      deployments: Seq[PeriodicProcessDeployment[WithCanonicalProcess]]
  ): Seq[PeriodicProcessDeployment[WithCanonicalProcess]] = {
    val now = LocalDateTime.now()
    deployments.filter(d => d.runAt.isBefore(now) || d.runAt.isEqual(now))
  }

}

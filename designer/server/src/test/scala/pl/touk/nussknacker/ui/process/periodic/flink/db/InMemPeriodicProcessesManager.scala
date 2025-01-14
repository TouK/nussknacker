package pl.touk.nussknacker.ui.process.periodic.flink.db

import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.periodic.model.{DeploymentWithRuntimeParams, RuntimeParams}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.periodic._
import pl.touk.nussknacker.ui.process.periodic.flink.db.InMemPeriodicProcessesManager._
import pl.touk.nussknacker.ui.process.periodic.flink.db.InMemPeriodicProcessesRepository.getLatestDeploymentQueryCount
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.ui.process.periodic.model._

import java.time.chrono.ChronoLocalDateTime
import java.time.{LocalDateTime, ZoneId}
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.util.Random

object InMemPeriodicProcessesRepository {
  val getLatestDeploymentQueryCount = new AtomicLong(0)
}

class InMemPeriodicProcessesManager(processingType: String) extends PeriodicProcessesManager {

  var processEntities: mutable.ListBuffer[TestPeriodicProcessEntity]              = ListBuffer.empty
  var deploymentEntities: mutable.ListBuffer[TestPeriodicProcessDeploymentEntity] = ListBuffer.empty

  private def canonicalProcess(processName: ProcessName) = {
    ScenarioBuilder
      .streaming(processName.value)
      .source("start", "source")
      .emptySink("end", "KafkaSink")
  }

  private implicit val localDateOrdering: Ordering[LocalDateTime] = Ordering.by(identity[ChronoLocalDateTime[_]])

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
    val entity = TestPeriodicProcessEntity(
      id = id,
      processId = None,
      processName = processName,
      processVersionId = VersionId.initialVersionId,
      processingType = processingType,
      inputConfigDuringExecutionJson = "{}",
      runtimeParams = RuntimeParams(Map("jarFileName" -> "")),
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
    val entity = TestPeriodicProcessDeploymentEntity(
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
  ): Future[SchedulesState] = Future.successful {
    val filteredProcesses = processEntities.filter { pe =>
      pe.processName == scenarioName && deploymentEntities.exists(d => d.periodicProcessId == pe.id)
    }.toSeq
    getLatestDeploymentsForPeriodicProcesses(filteredProcesses, deploymentsPerScheduleMaxCount = Int.MaxValue)
  }

  override def markInactive(processId: PeriodicProcessId): Future[Unit] = Future.successful {
    processEntities.zipWithIndex
      .find { case (process, _) => process.id == processId }
      .foreach { case (process, index) =>
        processEntities.update(index, process.copy(active = false))
      }
  }

  override def create(
      deploymentWithRuntimeParams: DeploymentWithRuntimeParams,
      inputConfigDuringExecutionJson: String,
      canonicalProcess: CanonicalProcess,
      scheduleProperty: ScheduleProperty,
      processActionId: ProcessActionId,
  ): Future[PeriodicProcess] = Future.successful {
    val id = PeriodicProcessId(Random.nextLong())
    val periodicProcess = TestPeriodicProcessEntity(
      id = id,
      processId = deploymentWithRuntimeParams.processId,
      processName = deploymentWithRuntimeParams.processName,
      processVersionId = deploymentWithRuntimeParams.versionId,
      processingType = processingType,
      inputConfigDuringExecutionJson = inputConfigDuringExecutionJson,
      runtimeParams = deploymentWithRuntimeParams.runtimeParams,
      scheduleProperty = scheduleProperty.asJson.noSpaces,
      active = true,
      createdAt = LocalDateTime.now(),
      processActionId = Some(processActionId)
    )
    processEntities += periodicProcess
    createPeriodicProcessWithJson(periodicProcess)
  }

  override def findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
      expectedDeploymentStatuses: Set[PeriodicProcessDeploymentStatus],
  ): Future[SchedulesState] = Future.successful {
    val filteredProcesses = processEntities.filter { pe =>
      pe.processingType == processingType &&
      deploymentEntities.exists(d => d.periodicProcessId == pe.id && expectedDeploymentStatuses.contains(d.status))
    }.toSeq
    getLatestDeploymentsForPeriodicProcesses(filteredProcesses, deploymentsPerScheduleMaxCount = 1)
  }

  override def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int,
  ): Future[SchedulesState] = Future.successful {
    getLatestDeploymentQueryCount.incrementAndGet()
    getLatestDeploymentsForPeriodicProcesses(
      processEntities(processName).filter(_.active),
      deploymentsPerScheduleMaxCount
    )
  }

  override def getLatestDeploymentsForActiveSchedules(
      deploymentsPerScheduleMaxCount: Int
  ): Future[Map[ProcessName, SchedulesState]] = Future.successful {
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
      deploymentsPerScheduleMaxCount: Int,
  ): Future[SchedulesState] = Future.successful {
    getLatestDeploymentQueryCount.incrementAndGet()
    val filteredProcesses =
      processEntities(processName).filterNot(_.active).sortBy(_.createdAt).takeRight(inactiveProcessesMaxCount)
    getLatestDeploymentsForPeriodicProcesses(filteredProcesses, deploymentsPerScheduleMaxCount)
  }

  override def getLatestDeploymentsForLatestInactiveSchedules(
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int
  ): Future[Map[ProcessName, SchedulesState]] = Future.successful {
    getLatestDeploymentQueryCount.incrementAndGet()
    allProcessEntities.map { case (processName, list) =>
      processName -> getLatestDeploymentsForPeriodicProcesses(
        list.filterNot(_.active).sortBy(_.createdAt).takeRight(inactiveProcessesMaxCount),
        deploymentsPerScheduleMaxCount
      )
    }
  }

  private def getLatestDeploymentsForPeriodicProcesses(
      processes: Seq[TestPeriodicProcessEntity],
      deploymentsPerScheduleMaxCount: Int
  ): SchedulesState = {
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
            .map(scheduleDeploymentData(_))
            .toList
          scheduleId -> ScheduleData(createPeriodicProcessWithoutJson(process), ds)
        }
    } yield deploymentGroupedByScheduleName).toMap)
  }

  override def findToBeDeployed: Future[Seq[PeriodicProcessDeployment]] = {
    val scheduled = findActive(PeriodicProcessDeploymentStatus.Scheduled)
    readyToRun(scheduled)
  }

  override def findToBeRetried: Future[Seq[PeriodicProcessDeployment]] = {
    val toBeRetried = findActive(PeriodicProcessDeploymentStatus.FailedOnDeploy).filter(_.retriesLeft > 0)
    readyToRun(toBeRetried)
  }

  override def findProcessData(
      id: PeriodicProcessDeploymentId,
  ): Future[PeriodicProcessDeployment] = Future.successful {
    (for {
      d <- deploymentEntities if d.id == id
      p <- processEntities if p.id == d.periodicProcessId
    } yield createPeriodicProcessDeployment(p, d)).head
  }

  private def processEntities(processName: ProcessName): Seq[TestPeriodicProcessEntity] =
    processEntities
      .filter(process => process.processName == processName && process.processingType == processingType)
      .toSeq

  private def allProcessEntities: Map[ProcessName, Seq[TestPeriodicProcessEntity]] =
    processEntities
      .filter(process => process.processingType == processingType)
      .toSeq
      .groupBy(_.processName)

  override def markDeployed(id: PeriodicProcessDeploymentId): Future[Unit] = Future.successful {
    update(id)(_.copy(status = PeriodicProcessDeploymentStatus.Deployed, deployedAt = Some(LocalDateTime.now())))
  }

  override def markFinished(id: PeriodicProcessDeploymentId): Future[Unit] = Future.successful {
    update(id)(_.copy(status = PeriodicProcessDeploymentStatus.Finished, completedAt = Some(LocalDateTime.now())))
  }

  override def markFailedOnDeployWithStatus(
      id: PeriodicProcessDeploymentId,
      status: PeriodicProcessDeploymentStatus,
      deployRetries: Int,
      retryAt: Option[LocalDateTime]
  ): Future[Unit] = Future.successful {
    update(id)(
      _.copy(
        status = status,
        completedAt = Some(LocalDateTime.now()),
        retriesLeft = deployRetries,
        nextRetryAt = retryAt
      )
    )
  }

  override def markFailed(id: PeriodicProcessDeploymentId): Future[Unit] = Future.successful {
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
      deployMaxRetries: Int,
  ): Future[PeriodicProcessDeployment] = Future.successful {
    val deploymentEntity = TestPeriodicProcessDeploymentEntity(
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
    val processEntity = processEntities.find(_.id == id).head
    createPeriodicProcessDeployment(processEntity, deploymentEntity)
  }

  private def update(
      id: PeriodicProcessDeploymentId
  )(action: TestPeriodicProcessDeploymentEntity => TestPeriodicProcessDeploymentEntity): Unit = {
    deploymentEntities.zipWithIndex
      .find { case (deployment, _) => deployment.id == id }
      .foreach { case (deployment, index) =>
        deploymentEntities.update(index, action(deployment))
      }
  }

  private def findActive(status: PeriodicProcessDeploymentStatus): Seq[PeriodicProcessDeployment] =
    findActive(
      Seq(status)
    )

  private def findActive(
      statusList: Seq[PeriodicProcessDeploymentStatus]
  ): Seq[PeriodicProcessDeployment] =
    (for {
      p <- processEntities if p.active && p.processingType == processingType
      d <- deploymentEntities if d.periodicProcessId == p.id && statusList.contains(d.status)
    } yield createPeriodicProcessDeployment(p, d)).toSeq

  private def readyToRun(
      deployments: Seq[PeriodicProcessDeployment]
  ): Future[Seq[PeriodicProcessDeployment]] = {
    val now = LocalDateTime.now()
    Future.successful(deployments.filter(d => d.runAt.isBefore(now) || d.runAt.isEqual(now)))
  }

  override def fetchCanonicalProcessWithVersion(
      processName: ProcessName,
      versionId: VersionId
  ): Future[Option[(CanonicalProcess, ProcessVersion)]] = Future.successful {
    Some(canonicalProcess(processName), ProcessVersion.empty)
  }

  override def fetchInputConfigDuringExecutionJson(
      processName: ProcessName,
      versionId: VersionId
  ): Future[Option[String]] =
    Future.successful(Some("{}"))

}

object InMemPeriodicProcessesManager {

  private val ProcessIdSequence    = new AtomicLong(0)
  private val DeploymentIdSequence = new AtomicLong(0)

  final case class TestPeriodicProcessEntity(
      id: PeriodicProcessId,
      processId: Option[ProcessId],
      processName: ProcessName,
      processVersionId: VersionId,
      processingType: String,
      inputConfigDuringExecutionJson: String,
      runtimeParams: RuntimeParams,
      scheduleProperty: String,
      active: Boolean,
      createdAt: LocalDateTime,
      processActionId: Option[ProcessActionId]
  )

  case class TestPeriodicProcessDeploymentEntity(
      id: PeriodicProcessDeploymentId,
      periodicProcessId: PeriodicProcessId,
      createdAt: LocalDateTime,
      runAt: LocalDateTime,
      scheduleName: Option[String],
      deployedAt: Option[LocalDateTime],
      completedAt: Option[LocalDateTime],
      retriesLeft: Int,
      nextRetryAt: Option[LocalDateTime],
      status: PeriodicProcessDeploymentStatus
  )

  def createPeriodicProcessDeployment(
      processEntity: TestPeriodicProcessEntity,
      processDeploymentEntity: TestPeriodicProcessDeploymentEntity
  ): PeriodicProcessDeployment = {
    val process = createPeriodicProcessWithJson(processEntity)
    PeriodicProcessDeployment(
      processDeploymentEntity.id,
      process,
      processDeploymentEntity.createdAt,
      processDeploymentEntity.runAt,
      ScheduleName(processDeploymentEntity.scheduleName),
      processDeploymentEntity.retriesLeft,
      processDeploymentEntity.nextRetryAt,
      createPeriodicDeploymentState(processDeploymentEntity)
    )
  }

  def createPeriodicDeploymentState(
      processDeploymentEntity: TestPeriodicProcessDeploymentEntity
  ): PeriodicProcessDeploymentState = {
    PeriodicProcessDeploymentState(
      processDeploymentEntity.deployedAt,
      processDeploymentEntity.completedAt,
      processDeploymentEntity.status
    )
  }

  def createPeriodicProcessWithJson(
      processEntity: TestPeriodicProcessEntity
  ): PeriodicProcess = {
    val scheduleProperty = prepareScheduleProperty(processEntity)
    PeriodicProcess(
      processEntity.id,
      DeploymentWithRuntimeParams(
        processId = processEntity.processId,
        processName = processEntity.processName,
        versionId = processEntity.processVersionId,
        runtimeParams = processEntity.runtimeParams,
      ),
      scheduleProperty,
      processEntity.active,
      processEntity.createdAt,
      processEntity.processActionId
    )
  }

  def createPeriodicProcessWithoutJson(
      processEntity: TestPeriodicProcessEntity
  ): PeriodicProcess = {
    val scheduleProperty = prepareScheduleProperty(processEntity)
    PeriodicProcess(
      processEntity.id,
      DeploymentWithRuntimeParams(
        processId = processEntity.processId,
        processName = processEntity.processName,
        versionId = processEntity.processVersionId,
        runtimeParams = processEntity.runtimeParams,
      ),
      scheduleProperty,
      processEntity.active,
      processEntity.createdAt,
      processEntity.processActionId
    )
  }

  private def prepareScheduleProperty(processEntity: TestPeriodicProcessEntity) = {
    val scheduleProperty = io.circe.parser
      .decode[ScheduleProperty](processEntity.scheduleProperty)
      .fold(e => throw new IllegalArgumentException(e), identity)
    scheduleProperty
  }

  private def scheduleDeploymentData(deployment: TestPeriodicProcessDeploymentEntity): ScheduleDeploymentData = {
    ScheduleDeploymentData(
      deployment.id,
      deployment.createdAt,
      deployment.runAt,
      deployment.deployedAt,
      deployment.retriesLeft,
      deployment.nextRetryAt,
      createPeriodicDeploymentState(deployment)
    )
  }

}

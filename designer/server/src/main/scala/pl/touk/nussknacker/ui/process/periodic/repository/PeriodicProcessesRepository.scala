package pl.touk.nussknacker.ui.process.periodic.repository

import cats.Monad
import com.github.tminglei.slickpg.ExPostgresProfile
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances
import io.circe.parser.decode
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.periodic.DeploymentWithRuntimeParams
import pl.touk.nussknacker.ui.db.entity._
import pl.touk.nussknacker.ui.process.periodic._
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.ui.process.periodic.model._
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{JdbcBackend, JdbcProfile}

import java.time.{Clock, LocalDateTime}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object PeriodicProcessesRepository {

  def createPeriodicProcessDeployment(
      processEntity: PeriodicProcessEntity,
      processDeploymentEntity: PeriodicProcessDeploymentEntity,
      canonicalProcess: CanonicalProcess,
  ): PeriodicProcessDeployment[CanonicalProcess] = {
    val process = createPeriodicProcessWithJson(processEntity, canonicalProcess)
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
      processDeploymentEntity: PeriodicProcessDeploymentEntity
  ): PeriodicProcessDeploymentState = {
    PeriodicProcessDeploymentState(
      processDeploymentEntity.deployedAt,
      processDeploymentEntity.completedAt,
      processDeploymentEntity.status
    )
  }

  def createPeriodicProcessWithJson(
      processEntity: PeriodicProcessEntity,
      canonicalProcess: CanonicalProcess
  ): PeriodicProcess[CanonicalProcess] = {
    val processVersion   = createProcessVersion(processEntity)
    val scheduleProperty = prepareScheduleProperty(processEntity)
    PeriodicProcess(
      processEntity.id,
      DeploymentWithRuntimeParams(
        processVersion = processVersion,
        inputConfigDuringExecutionJson = processEntity.inputConfigDuringExecutionJson,
        runtimeParams = processEntity.runtimeParams,
        process = canonicalProcess,
      ),
      scheduleProperty,
      processEntity.active,
      processEntity.createdAt,
      processEntity.processActionId
    )
  }

  def createPeriodicProcessWithoutJson(processEntity: PeriodicProcessEntity): PeriodicProcess[Unit] = {
    val processVersion   = createProcessVersion(processEntity)
    val scheduleProperty = prepareScheduleProperty(processEntity)
    PeriodicProcess(
      processEntity.id,
      DeploymentWithRuntimeParams(
        processVersion = processVersion,
        inputConfigDuringExecutionJson = processEntity.inputConfigDuringExecutionJson,
        runtimeParams = processEntity.runtimeParams,
        process = ()
      ),
      scheduleProperty,
      processEntity.active,
      processEntity.createdAt,
      processEntity.processActionId
    )
  }

  private def prepareScheduleProperty(processEntity: PeriodicProcessEntity) = {
    val scheduleProperty = decode[ScheduleProperty](processEntity.scheduleProperty)
      .fold(e => throw new IllegalArgumentException(e), identity)
    scheduleProperty
  }

  private def createProcessVersion(processEntity: PeriodicProcessEntity): ProcessVersion = {
    ProcessVersion.empty.copy(versionId = processEntity.processVersionId, processName = processEntity.processName)
  }

}

trait PeriodicProcessesRepository {

  type Action[_]

  implicit def monad: Monad[Action]

  implicit class RunOps[T](action: Action[T]) {
    def run: Future[T] = PeriodicProcessesRepository.this.run(action)
  }

  def run[T](action: Action[T]): Future[T]

  def markInactive(processId: PeriodicProcessId): Action[Unit]

  def getSchedulesState(
      scenarioName: ProcessName
  ): Action[SchedulesState]

  def create(
      deploymentWithRuntimeParams: DeploymentWithRuntimeParams[CanonicalProcess],
      scheduleProperty: ScheduleProperty,
      processActionId: ProcessActionId,
  ): Action[PeriodicProcess[CanonicalProcess]]

  def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int
  ): Action[SchedulesState]

  def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int
  ): Action[SchedulesState]

  def findToBeDeployed: Action[Seq[PeriodicProcessDeployment[CanonicalProcess]]]

  def findToBeRetried: Action[Seq[PeriodicProcessDeployment[CanonicalProcess]]]

  def findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
      expectedDeploymentStatuses: Set[PeriodicProcessDeploymentStatus]
  ): Action[SchedulesState]

  def findProcessData(id: PeriodicProcessDeploymentId): Action[PeriodicProcessDeployment[CanonicalProcess]]

  def markDeployed(id: PeriodicProcessDeploymentId): Action[Unit]

  def markFinished(id: PeriodicProcessDeploymentId): Action[Unit]

  def markFailedOnDeployWithStatus(
      id: PeriodicProcessDeploymentId,
      status: PeriodicProcessDeploymentStatus,
      deployRetries: Int,
      retryAt: Option[LocalDateTime]
  ): Action[Unit]

  def markFailed(id: PeriodicProcessDeploymentId): Action[Unit]

  def schedule(
      id: PeriodicProcessId,
      scheduleName: ScheduleName,
      runAt: LocalDateTime,
      deployMaxRetries: Int
  ): Action[PeriodicProcessDeployment[CanonicalProcess]]

}

class SlickPeriodicProcessesRepository(
    db: JdbcBackend.DatabaseDef,
    override val profile: JdbcProfile,
    clock: Clock,
    processingType: String
)(implicit ec: ExecutionContext)
    extends PeriodicProcessesRepository
    with PeriodicProcessesTableFactory
    with PeriodicProcessDeploymentsTableFactory
    with ProcessEntityFactory
    with ProcessVersionEntityFactory
    with LazyLogging {

  import io.circe.syntax._
  import pl.touk.nussknacker.engine.util.Implicits._

  type Action[T] = DBIOActionInstances.DB[T]

  override implicit def monad: Monad[Action] = DBIOActionInstances.dbMonad

  override def run[T](action: DBIOAction[T, NoStream, Effect.All]): Future[T] = db.run(action.transactionally)

  override def getSchedulesState(
      scenarioName: ProcessName
  ): Action[SchedulesState] = {
    PeriodicProcesses
      .filter(_.processName === scenarioName)
      .join(PeriodicProcessDeployments)
      .on(_.id === _.periodicProcessId)
      .result
      .map(toSchedulesState)
  }

  override def create(
      deploymentWithRuntimeParams: DeploymentWithRuntimeParams[CanonicalProcess],
      scheduleProperty: ScheduleProperty,
      processActionId: ProcessActionId
  ): Action[PeriodicProcess[CanonicalProcess]] = {
    val processEntity = PeriodicProcessEntity(
      id = PeriodicProcessId(-1),
      processName = deploymentWithRuntimeParams.processVersion.processName,
      processVersionId = deploymentWithRuntimeParams.processVersion.versionId,
      processingType = processingType,
      inputConfigDuringExecutionJson = deploymentWithRuntimeParams.inputConfigDuringExecutionJson,
      runtimeParams = deploymentWithRuntimeParams.runtimeParams,
      scheduleProperty = scheduleProperty.asJson.noSpaces,
      active = true,
      createdAt = now(),
      Some(processActionId)
    )
    ((PeriodicProcesses returning PeriodicProcesses into ((_, id) => id)) += processEntity)
      .map(PeriodicProcessesRepository.createPeriodicProcessWithJson(_, deploymentWithRuntimeParams.process))
  }

  private def now(): LocalDateTime = LocalDateTime.now(clock)

  override def findToBeDeployed: Action[Seq[PeriodicProcessDeployment[CanonicalProcess]]] = findProcesses(
    activePeriodicProcessWithDeploymentQuery
      .filter { case (_, d) =>
        d.runAt <= now() &&
        d.status === (PeriodicProcessDeploymentStatus.Scheduled: PeriodicProcessDeploymentStatus)
      }
  )

  override def findToBeRetried: Action[Seq[PeriodicProcessDeployment[CanonicalProcess]]] = findProcesses(
    activePeriodicProcessWithDeploymentQuery
      .filter { case (_, d) =>
        d.nextRetryAt <= now() &&
        d.status === (PeriodicProcessDeploymentStatus.RetryingDeploy: PeriodicProcessDeploymentStatus)
      }
  )

  private def findProcesses(
      query: Query[
        (PeriodicProcessesTable, PeriodicProcessDeploymentsTable),
        (PeriodicProcessEntity, PeriodicProcessDeploymentEntity),
        Seq
      ]
  ) = {
    query
      .join(fetchCanonicalProcess)
      .on { case ((periodicProcess, _), (process, _)) =>
        periodicProcess.processName === process.name
      }
      .map { case ((periodicProcess, periodicDeployment), (_, version)) =>
        (periodicProcess, periodicDeployment, version.json)
      }
      .result
      .map(_.map { case (periodicProcess, periodicDeployment, json) =>
        PeriodicProcessesRepository.createPeriodicProcessDeployment(
          periodicProcess,
          periodicDeployment,
          ProcessMarshaller.fromJsonUnsafe(json)
        )
      })
  }

  private def fetchCanonicalProcess = {
    processesTable.join(processVersionsTable).on(_.id === _.processId)
  }

  override def findProcessData(id: PeriodicProcessDeploymentId): Action[PeriodicProcessDeployment[CanonicalProcess]] =
    findProcesses(
      (PeriodicProcesses join PeriodicProcessDeployments on (_.id === _.periodicProcessId))
        .filter { case (_, deployment) => deployment.id === id }
    ).map(_.head)

  override def markDeployed(id: PeriodicProcessDeploymentId): Action[Unit] = {
    val q = for {
      d <- PeriodicProcessDeployments if d.id === id
    } yield (d.status, d.deployedAt)
    val update = q.update((PeriodicProcessDeploymentStatus.Deployed, Some(now())))
    update.map(_ => ())
  }

  override def markFailed(id: PeriodicProcessDeploymentId): Action[Unit] = {
    updateCompleted(id, PeriodicProcessDeploymentStatus.Failed)
  }

  override def markFinished(id: PeriodicProcessDeploymentId): Action[Unit] = {
    updateCompleted(id, PeriodicProcessDeploymentStatus.Finished)
  }

  override def markFailedOnDeployWithStatus(
      id: PeriodicProcessDeploymentId,
      status: PeriodicProcessDeploymentStatus,
      retriesLeft: Int,
      retryAt: Option[LocalDateTime]
  ): Action[Unit] = {
    val q = for {
      d <- PeriodicProcessDeployments if d.id === id
    } yield (d.status, d.completedAt, d.retriesLeft, d.nextRetryAt)
    val update = q.update((status, Some(now()), retriesLeft, retryAt))
    update.map(_ => ())
  }

  private def updateCompleted(
      id: PeriodicProcessDeploymentId,
      status: PeriodicProcessDeploymentStatus
  ): Action[Unit] = {
    val q = for {
      d <- PeriodicProcessDeployments if d.id === id
    } yield (d.status, d.completedAt)
    val update = q.update((status, Some(now())))
    update.map(_ => ())
  }

  override def findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
      expectedDeploymentStatuses: Set[PeriodicProcessDeploymentStatus]
  ): Action[SchedulesState] = {
    val processesHavingDeploymentsWithMatchingStatus = PeriodicProcesses.filter(p =>
      p.active &&
        PeriodicProcessDeployments
          .filter(d => d.periodicProcessId === p.id && d.status.inSet(expectedDeploymentStatuses))
          .exists
    )
    getLatestDeploymentsForEachSchedule(
      processesHavingDeploymentsWithMatchingStatus,
      deploymentsPerScheduleMaxCount = 1
    )
  }

  override def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int
  ): Action[SchedulesState] = {
    val activeProcessesQuery = PeriodicProcesses.filter(p => p.processName === processName && p.active)
    getLatestDeploymentsForEachSchedule(activeProcessesQuery, deploymentsPerScheduleMaxCount)
  }

  override def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int
  ): Action[SchedulesState] = {
    val filteredProcessesQuery = PeriodicProcesses
      .filter(p => p.processName === processName && !p.active)
      .sortBy(_.createdAt.desc)
      .take(inactiveProcessesMaxCount)
    getLatestDeploymentsForEachSchedule(filteredProcessesQuery, deploymentsPerScheduleMaxCount)
  }

  private def getLatestDeploymentsForEachSchedule(
      periodicProcessesQuery: Query[PeriodicProcessesTable, PeriodicProcessEntity, Seq],
      deploymentsPerScheduleMaxCount: Int
  ): Action[SchedulesState] = {
    val filteredPeriodicProcessQuery = periodicProcessesQuery.filter(p => p.processingType === processingType)
    val latestDeploymentsForSchedules = profile match {
      case _: ExPostgresProfile =>
        getLatestDeploymentsForEachSchedulePostgres(filteredPeriodicProcessQuery, deploymentsPerScheduleMaxCount)
      case _ =>
        getLatestDeploymentsForEachScheduleJdbcGeneric(filteredPeriodicProcessQuery, deploymentsPerScheduleMaxCount)
    }
    latestDeploymentsForSchedules.map(toSchedulesState)
  }

  private def getLatestDeploymentsForEachSchedulePostgres(
      periodicProcessesQuery: Query[PeriodicProcessesTable, PeriodicProcessEntity, Seq],
      deploymentsPerScheduleMaxCount: Int
  ): Action[Seq[(PeriodicProcessEntity, PeriodicProcessDeploymentEntity)]] = {
    // To effectively limit deployments to given count for each schedule in one query, we use window functions in slick
    import ExPostgresProfile.api._
    import com.github.tminglei.slickpg.window.PgWindowFuncSupport.WindowFunctions._

    val deploymentsForProcesses =
      periodicProcessesQuery join PeriodicProcessDeployments on (_.id === _.periodicProcessId)
    deploymentsForProcesses
      .map { case (process, deployment) =>
        (
          rowNumber() :: Over
            .partitionBy((deployment.periodicProcessId, deployment.scheduleName))
            .sortBy(
              deployment.runAt.desc,
              deployment.createdAt.desc
            ), // Remember to change DeploymentStatus.ordering accordingly
          process,
          deployment
        )
      }
      .subquery
      .filter(_._1 <= deploymentsPerScheduleMaxCount.longValue())
      .map { case (_, process, deployment) =>
        (process, deployment)
      }
      .result
  }

  // This variant of method is much less optimal than postgres one. It is highly recommended to use postgres with periodics
  // If we decided to support more databases, we should consider some optimization like extracting periodic_schedule table
  // with foreign key to periodic_process and with schedule_name column - it would reduce number of queries
  private def getLatestDeploymentsForEachScheduleJdbcGeneric(
      periodicProcessesQuery: Query[PeriodicProcessesTable, PeriodicProcessEntity, Seq],
      deploymentsPerScheduleMaxCount: Int
  ): Action[Seq[(PeriodicProcessEntity, PeriodicProcessDeploymentEntity)]] = {
    // It is debug instead of warn to not bloast logs when e.g. for some reasons is used hsql under the hood
    logger.debug(
      "WARN: Using not optimized version of getLatestDeploymentsForEachSchedule that not uses window functions"
    )
    for {
      processes <- periodicProcessesQuery.result
      schedulesForProcesses <-
        DBIO
          .sequence(processes.map { process =>
            PeriodicProcessDeployments
              .filter(_.periodicProcessId === process.id)
              .map(_.scheduleName)
              .distinct
              .result
              .map(_.map((process, _)))
          })
          .map(_.flatten)
      deploymentsForSchedules <-
        DBIO
          .sequence(schedulesForProcesses.map { case (process, scheduleName) =>
            PeriodicProcessDeployments
              // In SQL when you compare nulls, you will get always false
              .filter(deployment =>
                deployment.periodicProcessId === process.id && (deployment.scheduleName === scheduleName || deployment.scheduleName.isEmpty && scheduleName.isEmpty)
              )
              .sortBy(a => (a.runAt.desc, a.createdAt.desc)) // Remember to change DeploymentStatus.ordering accordingly
              .take(deploymentsPerScheduleMaxCount)
              .result
              .map(_.map((process, _)))
          })
          .map(_.flatten)
    } yield deploymentsForSchedules
  }

  override def schedule(
      id: PeriodicProcessId,
      scheduleName: ScheduleName,
      runAt: LocalDateTime,
      deployMaxRetries: Int
  ): Action[PeriodicProcessDeployment[CanonicalProcess]] = {
    val deploymentEntity = PeriodicProcessDeploymentEntity(
      id = PeriodicProcessDeploymentId(-1),
      periodicProcessId = id,
      createdAt = now(),
      runAt = runAt,
      scheduleName = scheduleName.value,
      deployedAt = None,
      completedAt = None,
      retriesLeft = deployMaxRetries,
      nextRetryAt = None,
      status = PeriodicProcessDeploymentStatus.Scheduled
    )
    ((PeriodicProcessDeployments returning PeriodicProcessDeployments.map(_.id) into ((_, id) =>
      id
    )) += deploymentEntity).flatMap(findProcessData)
  }

  override def markInactive(processId: PeriodicProcessId): Action[Unit] = {
    val q = for {
      p <- PeriodicProcesses if p.id === processId
    } yield p.active
    val update = q.update(false)
    update.map(_ => ())
  }

  private def activePeriodicProcessWithDeploymentQuery = {
    (PeriodicProcesses.filter(p => p.active === true && p.processingType === processingType)
      join PeriodicProcessDeployments on (_.id === _.periodicProcessId))
  }

  private def toSchedulesState(list: Seq[(PeriodicProcessEntity, PeriodicProcessDeploymentEntity)]): SchedulesState = {
    SchedulesState(
      list
        .map { case (process, deployment) =>
          val scheduleId = ScheduleId(process.id, ScheduleName(deployment.scheduleName))
          val scheduleDataWithoutDeployment =
            (scheduleId, PeriodicProcessesRepository.createPeriodicProcessWithoutJson(process))
          val scheduleDeployment = ScheduleDeploymentData(deployment)
          (scheduleDataWithoutDeployment, scheduleDeployment)
        }
        .toList
        .toGroupedMap
        .toList
        .map { case ((scheduleId, process), deployments) =>
          scheduleId -> ScheduleData(process, deployments)
        }
        .toMap
    )
  }

}

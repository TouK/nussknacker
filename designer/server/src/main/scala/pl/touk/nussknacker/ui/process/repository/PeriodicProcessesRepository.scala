package pl.touk.nussknacker.ui.process.repository

import com.github.tminglei.slickpg.ExPostgresProfile
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances
import db.util.DBIOActionInstances.DB
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.deployment.scheduler.model.DeploymentWithRuntimeParams
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.db.entity._
import pl.touk.nussknacker.ui.process.periodic.ScheduleProperty
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.ui.process.periodic.model._
import pl.touk.nussknacker.ui.process.repository.PeriodicProcessesRepository.createPeriodicProcess
import pl.touk.nussknacker.ui.security.api.NussknackerInternalUser
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{JdbcBackend, JdbcProfile}

import java.time.{Clock, LocalDateTime}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object PeriodicProcessesRepository {

  def createPeriodicProcessDeployment(
      processEntity: PeriodicProcessEntity,
      processDeploymentEntity: PeriodicProcessDeploymentEntity
  ): PeriodicProcessDeployment = {
    val process = createPeriodicProcess(processEntity)
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

  def createPeriodicProcess(
      processEntity: PeriodicProcessEntity
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

  private def prepareScheduleProperty(processEntity: PeriodicProcessEntity) = {
    val scheduleProperty = decode[ScheduleProperty](processEntity.scheduleProperty)
      .fold(e => throw new IllegalArgumentException(e), identity)
    scheduleProperty
  }

}

trait PeriodicProcessesRepository {

  type Action[_]

  implicit class RunOps[T](action: Action[T]) {
    def run: Future[T] = PeriodicProcessesRepository.this.run(action)
  }

  def run[T](action: Action[T]): Future[T]

  def markInactive(processId: PeriodicProcessId): Action[Unit]

  def getSchedulesState(
      scenarioName: ProcessName,
      after: Option[LocalDateTime],
  ): Action[SchedulesState]

  def create(
      deploymentWithRuntimeParams: DeploymentWithRuntimeParams,
      inputConfigDuringExecutionJson: String,
      canonicalProcess: CanonicalProcess,
      scheduleProperty: ScheduleProperty,
      processActionId: ProcessActionId,
  ): Action[PeriodicProcess]

  def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int,
  ): Action[SchedulesState]

  def getLatestDeploymentsForActiveSchedules(
      deploymentsPerScheduleMaxCount: Int,
  ): Action[Map[ProcessName, SchedulesState]]

  def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int,
  ): Action[SchedulesState]

  def getLatestDeploymentsForLatestInactiveSchedules(
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int,
  ): Action[Map[ProcessName, SchedulesState]]

  def findToBeDeployed: Action[Seq[PeriodicProcessDeployment]]

  def findToBeRetried: Action[Seq[PeriodicProcessDeployment]]

  def findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
      expectedDeploymentStatuses: Set[PeriodicProcessDeploymentStatus],
  ): Action[SchedulesState]

  def findProcessData(id: PeriodicProcessDeploymentId): Action[PeriodicProcessDeployment]

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
  ): Action[PeriodicProcessDeployment]

  def fetchInputConfigDuringExecutionJson(
      processName: ProcessName,
      versionId: VersionId
  ): Action[Option[String]]

  def fetchCanonicalProcessWithVersion(
      processName: ProcessName,
      versionId: VersionId
  ): Future[Option[(CanonicalProcess, ProcessVersion)]]

}

class SlickPeriodicProcessesRepository(
    processingType: String,
    db: JdbcBackend.DatabaseDef,
    override val profile: JdbcProfile,
    clock: Clock,
    fetchingProcessRepository: FetchingProcessRepository[Future],
)(implicit ec: ExecutionContext)
    extends PeriodicProcessesRepository
    with PeriodicProcessesTableFactory
    with PeriodicProcessDeploymentsTableFactory
    with LazyLogging {

  import pl.touk.nussknacker.engine.util.Implicits._

  type Action[T] = DBIOActionInstances.DB[T]

  override def run[T](action: DBIOAction[T, NoStream, Effect.All]): Future[T] = db.run(action.transactionally)

  override def getSchedulesState(
      scenarioName: ProcessName,
      afterOpt: Option[LocalDateTime],
  ): Action[SchedulesState] = {
    PeriodicProcessesWithoutInputConfig
      .filter(_.processName === scenarioName)
      .join(PeriodicProcessDeployments)
      .on(_.id === _.periodicProcessId)
      .filterOpt(afterOpt)((entities, after) => entities._2.completedAt > after)
      .result
      .map(toSchedulesStateForSinglePeriodicProcess)
  }

  override def create(
      deploymentWithRuntimeParams: DeploymentWithRuntimeParams,
      inputConfigDuringExecutionJson: String,
      canonicalProcess: CanonicalProcess,
      scheduleProperty: ScheduleProperty,
      processActionId: ProcessActionId,
  ): Action[PeriodicProcess] = {
    val processEntity = PeriodicProcessEntityWithInputConfigJson(
      id = PeriodicProcessId(-1),
      processId = deploymentWithRuntimeParams.processId,
      processName = deploymentWithRuntimeParams.processName,
      processVersionId = deploymentWithRuntimeParams.versionId,
      processingType = processingType,
      runtimeParams = deploymentWithRuntimeParams.runtimeParams,
      scheduleProperty = scheduleProperty.asJson.noSpaces,
      active = true,
      createdAt = now(),
      Some(processActionId),
      inputConfigDuringExecutionJson = inputConfigDuringExecutionJson,
    )
    ((PeriodicProcessesWithInputConfig returning PeriodicProcessesWithInputConfig into ((_, id) =>
      id
    )) += processEntity)
      .map(PeriodicProcessesRepository.createPeriodicProcess)
  }

  private def now(): LocalDateTime = LocalDateTime.now(clock)

  override def findToBeDeployed: Action[Seq[PeriodicProcessDeployment]] =
    findProcesses(
      activePeriodicProcessWithDeploymentQuery(processingType)
        .filter { case (_, d) =>
          d.runAt <= now() &&
          d.status === (PeriodicProcessDeploymentStatus.Scheduled: PeriodicProcessDeploymentStatus)
        }
    )

  override def findToBeRetried: Action[Seq[PeriodicProcessDeployment]] =
    findProcesses(
      activePeriodicProcessWithDeploymentQuery(processingType)
        .filter { case (_, d) =>
          d.nextRetryAt <= now() &&
          d.status === (PeriodicProcessDeploymentStatus.RetryingDeploy: PeriodicProcessDeploymentStatus)
        }
    )

  private def findProcesses(
      query: Query[
        (PeriodicProcessesWithoutInputConfigJsonTable, PeriodicProcessDeploymentsTable),
        (PeriodicProcessEntityWithoutInputConfigJson, PeriodicProcessDeploymentEntity),
        Seq
      ]
  ) = {
    query.result
      .map(_.map { case (periodicProcess, periodicDeployment) =>
        PeriodicProcessesRepository.createPeriodicProcessDeployment(
          periodicProcess,
          periodicDeployment,
        )
      })
  }

  override def findProcessData(id: PeriodicProcessDeploymentId): Action[PeriodicProcessDeployment] =
    findProcesses(
      (PeriodicProcessesWithoutInputConfig join PeriodicProcessDeployments on (_.id === _.periodicProcessId))
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
      expectedDeploymentStatuses: Set[PeriodicProcessDeploymentStatus],
  ): Action[SchedulesState] = {
    val processesHavingDeploymentsWithMatchingStatus = PeriodicProcessesWithoutInputConfig.filter(p =>
      p.active &&
        PeriodicProcessDeployments
          .filter(d => d.periodicProcessId === p.id && d.status.inSet(expectedDeploymentStatuses))
          .exists
    )
    getLatestDeploymentsForEachSchedule(
      processesHavingDeploymentsWithMatchingStatus,
      deploymentsPerScheduleMaxCount = 1,
    ).map(schedulesForProcessNames =>
      SchedulesState(
        schedulesForProcessNames.values.map(_.schedules).foldLeft(Map.empty[ScheduleId, ScheduleData])(_ ++ _)
      )
    )
  }

  override def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int,
  ): Action[SchedulesState] = {
    val activeProcessesQuery =
      PeriodicProcessesWithoutInputConfig.filter(p => p.processName === processName && p.active)
    getLatestDeploymentsForEachSchedule(activeProcessesQuery, deploymentsPerScheduleMaxCount)
      .map(_.getOrElse(processName, SchedulesState(Map.empty)))
  }

  override def getLatestDeploymentsForActiveSchedules(
      deploymentsPerScheduleMaxCount: Int,
  ): Action[Map[ProcessName, SchedulesState]] = {
    val activeProcessesQuery = PeriodicProcessesWithoutInputConfig.filter(_.active)
    getLatestDeploymentsForEachSchedule(activeProcessesQuery, deploymentsPerScheduleMaxCount)
  }

  override def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int,
  ): Action[SchedulesState] = {
    val filteredProcessesQuery = PeriodicProcessesWithoutInputConfig
      .filter(p => p.processName === processName && !p.active)
      .sortBy(_.createdAt.desc)
      .take(inactiveProcessesMaxCount)
    getLatestDeploymentsForEachSchedule(filteredProcessesQuery, deploymentsPerScheduleMaxCount)
      .map(_.getOrElse(processName, SchedulesState(Map.empty)))
  }

  override def getLatestDeploymentsForLatestInactiveSchedules(
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int,
  ): Action[Map[ProcessName, SchedulesState]] = {
    val filteredProcessesQuery = PeriodicProcessesWithoutInputConfig
      .filter(!_.active)
      .sortBy(_.createdAt.desc)
      .take(inactiveProcessesMaxCount)
    getLatestDeploymentsForEachSchedule(filteredProcessesQuery, deploymentsPerScheduleMaxCount)
  }

  private def getLatestDeploymentsForEachSchedule(
      periodicProcessesQuery: Query[
        PeriodicProcessesWithoutInputConfigJsonTable,
        PeriodicProcessEntityWithoutInputConfigJson,
        Seq
      ],
      deploymentsPerScheduleMaxCount: Int,
  ): Action[Map[ProcessName, SchedulesState]] = {
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
      periodicProcessesQuery: Query[
        PeriodicProcessesWithoutInputConfigJsonTable,
        PeriodicProcessEntityWithoutInputConfigJson,
        Seq
      ],
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
      periodicProcessesQuery: Query[
        PeriodicProcessesWithoutInputConfigJsonTable,
        PeriodicProcessEntityWithoutInputConfigJson,
        Seq
      ],
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
  ): Action[PeriodicProcessDeployment] = {
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
      p <- PeriodicProcessesWithoutInputConfig if p.id === processId
    } yield p.active
    val update = q.update(false)
    update.map(_ => ())
  }

  def fetchInputConfigDuringExecutionJson(processName: ProcessName, versionId: VersionId): Action[Option[String]] =
    PeriodicProcessesWithInputConfig
      .filter(p => p.processName === processName && p.processVersionId === versionId)
      .map(_.inputConfigDuringExecutionJson)
      .result
      .headOption

  private def activePeriodicProcessWithDeploymentQuery(processingType: String) = {
    (PeriodicProcessesWithoutInputConfig.filter(p => p.active === true && p.processingType === processingType)
      join PeriodicProcessDeployments on (_.id === _.periodicProcessId))
  }

  private def toSchedulesState(
      list: Seq[(PeriodicProcessEntity, PeriodicProcessDeploymentEntity)]
  ): Map[ProcessName, SchedulesState] = {
    list
      .groupBy(_._1.processName)
      .map { case (processName, list) => processName -> toSchedulesStateForSinglePeriodicProcess(list) }
  }

  private def toSchedulesStateForSinglePeriodicProcess(
      list: Seq[(PeriodicProcessEntity, PeriodicProcessDeploymentEntity)]
  ): SchedulesState = {
    SchedulesState(
      list
        .map { case (process, deployment) =>
          val scheduleId         = ScheduleId(process.id, ScheduleName(deployment.scheduleName))
          val scheduleData       = (scheduleId, process)
          val scheduleDeployment = scheduleDeploymentData(deployment)
          (scheduleData, scheduleDeployment)
        }
        .toList
        .toGroupedMap
        .toList
        .map { case ((scheduleId, process), deployments) =>
          scheduleId -> ScheduleData(createPeriodicProcess(process), deployments)
        }
        .toMap
    )
  }

  private def scheduleDeploymentData(deployment: PeriodicProcessDeploymentEntity): ScheduleDeploymentData = {
    ScheduleDeploymentData(
      deployment.id,
      deployment.createdAt,
      deployment.runAt,
      deployment.deployedAt,
      deployment.retriesLeft,
      deployment.nextRetryAt,
      PeriodicProcessesRepository.createPeriodicDeploymentState(deployment)
    )
  }

  override def fetchCanonicalProcessWithVersion(
      processName: ProcessName,
      versionId: VersionId
  ): Future[Option[(CanonicalProcess, ProcessVersion)]] =
    fetchingProcessRepository.getCanonicalProcessWithVersion(processName, versionId)(NussknackerInternalUser.instance)

}

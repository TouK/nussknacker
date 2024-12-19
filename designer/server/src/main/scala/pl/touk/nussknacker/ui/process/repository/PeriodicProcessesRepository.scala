package pl.touk.nussknacker.ui.process.repository

import _root_.db.util.DBIOActionInstances
import cats.Monad
import com.github.tminglei.slickpg.ExPostgresProfile
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.deployment.periodic.PeriodicProcessesManager
import pl.touk.nussknacker.engine.api.deployment.periodic.model.DeploymentWithRuntimeParams.{WithConfig, WithoutConfig}
import pl.touk.nussknacker.engine.api.deployment.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.api.deployment.periodic.model._
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.common.periodic.ScheduleProperty.{fromApi, toApi}
import pl.touk.nussknacker.engine.common.periodic._
import pl.touk.nussknacker.ui.db.entity._
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{JdbcBackend, JdbcProfile}

import java.time.{Clock, LocalDateTime}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object PeriodicProcessesRepository {

  def createPeriodicProcessDeployment(
      processEntity: PeriodicProcessEntityWithJson,
      processDeploymentEntity: PeriodicProcessDeploymentEntity
  ): PeriodicProcessDeployment[WithConfig] = {
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
      processDeploymentEntity: PeriodicProcessDeploymentEntity
  ): PeriodicProcessDeploymentState = {
    PeriodicProcessDeploymentState(
      processDeploymentEntity.deployedAt,
      processDeploymentEntity.completedAt,
      processDeploymentEntity.status
    )
  }

  def createPeriodicProcessWithJson(
      processEntity: PeriodicProcessEntityWithJson
  ): PeriodicProcess[WithConfig] = {
    val processVersion   = createProcessVersion(processEntity)
    val scheduleProperty = prepareScheduleProperty(processEntity)
    PeriodicProcess(
      processEntity.id,
      DeploymentWithRuntimeParams.WithConfig(
        processVersion = processVersion,
        inputConfigDuringExecutionJson = processEntity.inputConfigDuringExecutionJson,
        runtimeParams = processEntity.runtimeParams,
      ),
      toApi(scheduleProperty),
      processEntity.active,
      processEntity.createdAt,
      processEntity.processActionId
    )
  }

  def createPeriodicProcessWithoutJson(
      processEntity: PeriodicProcessEntity
  ): PeriodicProcess[WithoutConfig] = {
    val processVersion   = createProcessVersion(processEntity)
    val scheduleProperty = prepareScheduleProperty(processEntity)
    PeriodicProcess(
      processEntity.id,
      DeploymentWithRuntimeParams.WithoutConfig(
        processVersion = processVersion,
        runtimeParams = processEntity.runtimeParams,
      ),
      toApi(scheduleProperty),
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
      scenarioName: ProcessName,
      after: Option[LocalDateTime],
  ): Action[SchedulesState]

  def create(
      deploymentWithRuntimeParams: DeploymentWithRuntimeParams.WithConfig,
      scheduleProperty: PeriodicProcessesManager.ScheduleProperty,
      processActionId: ProcessActionId,
      processingType: String,
  ): Action[PeriodicProcess[DeploymentWithRuntimeParams.WithConfig]]

  def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int,
      processingType: String,
  ): Action[SchedulesState]

  def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int,
      processingType: String,
  ): Action[SchedulesState]

  def findToBeDeployed(processingType: String): Action[Seq[PeriodicProcessDeployment[WithConfig]]]

  def findToBeRetried(processingType: String): Action[Seq[PeriodicProcessDeployment[WithConfig]]]

  def findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
      expectedDeploymentStatuses: Set[PeriodicProcessDeploymentStatus],
      processingType: String,
  ): Action[SchedulesState]

  def findProcessData(id: PeriodicProcessDeploymentId): Action[PeriodicProcessDeployment[WithConfig]]

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
  ): Action[PeriodicProcessDeployment[WithConfig]]

  def fetchCanonicalProcess(
      processName: ProcessName,
      versionId: VersionId,
  ): Action[Option[CanonicalProcess]]

}

class SlickPeriodicProcessesRepository(
    db: JdbcBackend.DatabaseDef,
    override val profile: JdbcProfile,
    clock: Clock,
)(implicit ec: ExecutionContext)
    extends PeriodicProcessesRepository
    with PeriodicProcessesTableFactory
    with PeriodicProcessDeploymentsTableFactory
    with ProcessVersionEntityFactory
    with ProcessEntityFactory
    with LazyLogging {

  import pl.touk.nussknacker.engine.util.Implicits._

  type Action[T] = DBIOActionInstances.DB[T]

  override implicit def monad: Monad[Action] = DBIOActionInstances.dbMonad

  override def run[T](action: DBIOAction[T, NoStream, Effect.All]): Future[T] = db.run(action.transactionally)

  override def getSchedulesState(
      scenarioName: ProcessName,
      afterOpt: Option[LocalDateTime],
  ): Action[SchedulesState] = {
    PeriodicProcessesWithoutJson
      .filter(_.processName === scenarioName)
      .join(PeriodicProcessDeployments)
      .on(_.id === _.periodicProcessId)
      .filterOpt(afterOpt)((entities, after) => entities._2.completedAt > after)
      .result
      .map(toSchedulesState)
  }

  override def create(
      deploymentWithRuntimeParams: DeploymentWithRuntimeParams.WithConfig,
      scheduleProperty: PeriodicProcessesManager.ScheduleProperty,
      processActionId: ProcessActionId,
      processingType: String,
  ): Action[PeriodicProcess[DeploymentWithRuntimeParams.WithConfig]] = {
    val processEntity = PeriodicProcessEntityWithJson(
      id = PeriodicProcessId(-1),
      processName = deploymentWithRuntimeParams.processVersion.processName,
      processVersionId = deploymentWithRuntimeParams.processVersion.versionId,
      processingType = processingType,
      inputConfigDuringExecutionJson = deploymentWithRuntimeParams.inputConfigDuringExecutionJson,
      runtimeParams = deploymentWithRuntimeParams.runtimeParams,
      scheduleProperty = fromApi(scheduleProperty).asJson.noSpaces,
      active = true,
      createdAt = now(),
      Some(processActionId)
    )
    ((PeriodicProcessesWithJson returning PeriodicProcessesWithJson into ((_, id) => id)) += processEntity)
      .map(PeriodicProcessesRepository.createPeriodicProcessWithJson)
  }

  private def now(): LocalDateTime = LocalDateTime.now(clock)

  override def findToBeDeployed(processingType: String): Action[Seq[PeriodicProcessDeployment[WithConfig]]] =
    findProcesses(
      activePeriodicProcessWithDeploymentQuery(processingType)
        .filter { case (_, d) =>
          d.runAt <= now() &&
          d.status === (PeriodicProcessDeploymentStatus.Scheduled: PeriodicProcessDeploymentStatus)
        }
    )

  override def findToBeRetried(processingType: String): Action[Seq[PeriodicProcessDeployment[WithConfig]]] =
    findProcesses(
      activePeriodicProcessWithDeploymentQuery(processingType)
        .filter { case (_, d) =>
          d.nextRetryAt <= now() &&
          d.status === (PeriodicProcessDeploymentStatus.RetryingDeploy: PeriodicProcessDeploymentStatus)
        }
    )

  private def findProcesses(
      query: Query[
        (PeriodicProcessesWithJsonTable, PeriodicProcessDeploymentsTable),
        (PeriodicProcessEntityWithJson, PeriodicProcessDeploymentEntity),
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

  override def findProcessData(id: PeriodicProcessDeploymentId): Action[PeriodicProcessDeployment[WithConfig]] =
    findProcesses(
      (PeriodicProcessesWithJson join PeriodicProcessDeployments on (_.id === _.periodicProcessId))
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
      processingType: String,
  ): Action[SchedulesState] = {
    val processesHavingDeploymentsWithMatchingStatus = PeriodicProcessesWithoutJson.filter(p =>
      p.active &&
        PeriodicProcessDeployments
          .filter(d => d.periodicProcessId === p.id && d.status.inSet(expectedDeploymentStatuses))
          .exists
    )
    getLatestDeploymentsForEachSchedule(
      processesHavingDeploymentsWithMatchingStatus,
      deploymentsPerScheduleMaxCount = 1,
      processingType = processingType,
    )
  }

  override def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int,
      processingType: String,
  ): Action[SchedulesState] = {
    val activeProcessesQuery = PeriodicProcessesWithoutJson.filter(p => p.processName === processName && p.active)
    getLatestDeploymentsForEachSchedule(activeProcessesQuery, deploymentsPerScheduleMaxCount, processingType)
  }

  override def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int,
      processingType: String,
  ): Action[SchedulesState] = {
    val filteredProcessesQuery = PeriodicProcessesWithoutJson
      .filter(p => p.processName === processName && !p.active)
      .sortBy(_.createdAt.desc)
      .take(inactiveProcessesMaxCount)
    getLatestDeploymentsForEachSchedule(filteredProcessesQuery, deploymentsPerScheduleMaxCount, processingType)
  }

  private def getLatestDeploymentsForEachSchedule(
      periodicProcessesQuery: Query[PeriodicProcessWithoutJson, PeriodicProcessEntityWithoutJson, Seq],
      deploymentsPerScheduleMaxCount: Int,
      processingType: String,
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
      periodicProcessesQuery: Query[PeriodicProcessWithoutJson, PeriodicProcessEntityWithoutJson, Seq],
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
      periodicProcessesQuery: Query[PeriodicProcessWithoutJson, PeriodicProcessEntityWithoutJson, Seq],
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
  ): Action[PeriodicProcessDeployment[WithConfig]] = {
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
      p <- PeriodicProcessesWithoutJson if p.id === processId
    } yield p.active
    val update = q.update(false)
    update.map(_ => ())
  }

  def fetchCanonicalProcess(processName: ProcessName, versionId: VersionId): Action[Option[CanonicalProcess]] = {
    processesTable
      .filter(_.name === processName)
      .join(processVersionsTable)
      .on((process, version) => process.id === version.processId)
      .filter { case (_, version) => version.id === versionId }
      .result
      .headOption
      .map(_.flatMap(_._2.json))
  }

  private def activePeriodicProcessWithDeploymentQuery(processingType: String) = {
    (PeriodicProcessesWithJson.filter(p => p.active === true && p.processingType === processingType)
      join PeriodicProcessDeployments on (_.id === _.periodicProcessId))
  }

  private def toSchedulesState(list: Seq[(PeriodicProcessEntity, PeriodicProcessDeploymentEntity)]): SchedulesState = {
    SchedulesState(
      list
        .map { case (process, deployment) =>
          val scheduleId = ScheduleId(process.id, ScheduleName(deployment.scheduleName))
          val scheduleDataWithoutDeployment =
            (scheduleId, PeriodicProcessesRepository.createPeriodicProcessWithoutJson(process))
          val scheduleDeployment = scheduleDeploymentData(deployment)
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

}

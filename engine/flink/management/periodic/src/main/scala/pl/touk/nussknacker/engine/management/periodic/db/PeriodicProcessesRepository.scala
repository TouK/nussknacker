package pl.touk.nussknacker.engine.management.periodic.db

import cats.Monad
import com.github.tminglei.slickpg.ExPostgresProfile
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser.decode
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, ProcessActionId}
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.management.periodic._
import pl.touk.nussknacker.engine.management.periodic.db.PeriodicProcessesRepository.{
  createPeriodicProcessMetadata,
  createPeriodicProcessWithoutJson,
  prepareScheduleProperty
}
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic.model._
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{JdbcBackend, JdbcProfile}

import java.time.{Clock, LocalDateTime}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object PeriodicProcessesRepository {

  def createPeriodicProcessDeploymentWithFullProcess(
      processEntity: PeriodicProcessEntity,
      processDeploymentEntity: PeriodicProcessDeploymentEntity
  ): PeriodicProcessDeploymentWithFullProcess = {
    val process = createPeriodicProcessWithJson(processEntity)
    PeriodicProcessDeploymentWithFullProcess(
      PeriodicProcessesRepository.createPeriodicProcessDeploymentFromProcess(process, processDeploymentEntity),
      process.deploymentData.process,
      process.deploymentData.inputConfigDuringExecutionJson,
    )
  }

  def createPeriodicProcessDeployment(
      processEntity: PeriodicProcessEntity,
      processDeploymentEntity: PeriodicProcessDeploymentEntity
  ): PeriodicProcessDeployment = {
    val process = createPeriodicProcessWithJson(processEntity)
    createPeriodicProcessDeploymentFromProcess(
      process,
      processDeploymentEntity
    )
  }

  def createPeriodicProcessDeploymentFromProcess(
      periodicProcess: PeriodicProcess[CanonicalProcess],
      processDeploymentEntity: PeriodicProcessDeploymentEntity
  ): PeriodicProcessDeployment = {
    PeriodicProcessDeployment(
      processDeploymentEntity.id,
      createPeriodicProcessMetadata(periodicProcess),
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

  def createPeriodicProcessWithJson(processEntity: PeriodicProcessEntity): PeriodicProcess[CanonicalProcess] = {
    val scheduleProperty = prepareScheduleProperty(processEntity.scheduleProperty)
    PeriodicProcess(
      processEntity.id,
      model.DeploymentWithJarData(
        processName = processEntity.processName,
        versionId = processEntity.processVersionId,
        inputConfigDuringExecutionJson = processEntity.inputConfigDuringExecutionJson,
        jarFileName = processEntity.jarFileName,
        process = processEntity.processJson.getOrElse(
          throw new IllegalArgumentException("Missing required scenario json in processEntity")
        )
      ),
      scheduleProperty,
      processEntity.active,
      processEntity.createdAt,
      processEntity.processActionId
    )
  }

  def createPeriodicProcessWithoutJson(processEntity: PeriodicProcessEntity): PeriodicProcess[Unit] = {
    val scheduleProperty = prepareScheduleProperty(processEntity.scheduleProperty)
    PeriodicProcess(
      processEntity.id,
      model.DeploymentWithJarData(
        processName = processEntity.processName,
        versionId = processEntity.processVersionId,
        inputConfigDuringExecutionJson = processEntity.inputConfigDuringExecutionJson,
        jarFileName = processEntity.jarFileName,
        process = ()
      ),
      scheduleProperty,
      processEntity.active,
      processEntity.createdAt,
      processEntity.processActionId
    )
  }

  def createPeriodicProcessMetadata(process: PeriodicProcess[_]): PeriodicProcessMetadata = {
    PeriodicProcessMetadata(
      id = process.id,
      processName = process.deploymentData.processName,
      versionId = process.deploymentData.versionId,
      jarFileName = process.deploymentData.jarFileName,
      scheduleProperty = process.scheduleProperty,
      active = process.active,
      processActionId = process.processActionId
    )
  }

  def prepareScheduleProperty(scheduleProperty: String) = {
    decode[ScheduleProperty](scheduleProperty).fold(e => throw new IllegalArgumentException(e), identity)
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
      deploymentWithJarData: DeploymentWithJarData[CanonicalProcess],
      scheduleProperty: ScheduleProperty,
      processActionId: ProcessActionId
  ): Action[PeriodicProcess[CanonicalProcess]]

  def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[SchedulesState]

  def getLatestDeploymentsForActiveSchedules(
      deploymentsPerScheduleMaxCount: Int
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[Map[ProcessName, SchedulesState]]

  def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[SchedulesState]

  def getLatestDeploymentsForLatestInactiveSchedules(
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[Map[ProcessName, SchedulesState]]

  def findToBeDeployed: Action[Seq[PeriodicProcessDeploymentWithFullProcess]]

  def findToBeRetried: Action[Seq[PeriodicProcessDeploymentWithFullProcess]]

  def findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
      expectedDeploymentStatuses: Set[PeriodicProcessDeploymentStatus]
  ): Action[SchedulesState]

  def findProcessData(id: PeriodicProcessDeploymentId): Action[PeriodicProcessDeploymentWithFullProcess]

  def findProcessData(processName: ProcessName): Action[Seq[PeriodicProcess[CanonicalProcess]]]

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
  ): Action[PeriodicProcessDeploymentWithFullProcess]

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
    with LazyLogging {

  import io.circe.syntax._
  import pl.touk.nussknacker.engine.util.Implicits._

  type Action[T] = DBIOActionInstances.DB[T]

  override implicit def monad: Monad[Action] = DBIOActionInstances.dbMonad

  override def run[T](action: DBIOAction[T, NoStream, Effect.All]): Future[T] = db.run(action.transactionally)

  override def getSchedulesState(
      scenarioName: ProcessName
  ): Action[SchedulesState] = {
    PeriodicProcessesWithoutJson
      .filter(_.processName === scenarioName)
      .map(extractPeriodicProcessMetadataColumns)
      .join(PeriodicProcessDeployments)
      .on(_._1 === _.periodicProcessId)
      .result
      .map(_.map { case (periodicProcessMetadataColumnValues, deploymentEntity) =>
        (createPeriodicProcessMetadataFromColumnValues(periodicProcessMetadataColumnValues), deploymentEntity)
      })
      .map(toSchedulesState)
      .map(_.getOrElse(scenarioName, SchedulesState(Map.empty)))
  }

  override def create(
      deploymentWithJarData: DeploymentWithJarData[CanonicalProcess],
      scheduleProperty: ScheduleProperty,
      processActionId: ProcessActionId
  ): Action[PeriodicProcess[CanonicalProcess]] = {
    val processEntity = PeriodicProcessEntity(
      id = PeriodicProcessId(-1),
      processName = deploymentWithJarData.processName,
      processVersionId = deploymentWithJarData.versionId,
      processingType = processingType,
      processJson = Some(deploymentWithJarData.process),
      inputConfigDuringExecutionJson = deploymentWithJarData.inputConfigDuringExecutionJson,
      jarFileName = deploymentWithJarData.jarFileName,
      scheduleProperty = scheduleProperty.asJson.noSpaces,
      active = true,
      createdAt = now(),
      Some(processActionId)
    )
    ((PeriodicProcessesWithJson returning PeriodicProcessesWithJson into ((_, id) => id)) += processEntity)
      .map(PeriodicProcessesRepository.createPeriodicProcessWithJson)
  }

  private def now(): LocalDateTime = LocalDateTime.now(clock)

  override def findToBeDeployed: Action[Seq[PeriodicProcessDeploymentWithFullProcess]] =
    activePeriodicProcessWithDeploymentQuery
      .filter { case (_, d) =>
        d.runAt <= now() &&
        d.status === (PeriodicProcessDeploymentStatus.Scheduled: PeriodicProcessDeploymentStatus)
      }
      .result
      .map(_.map((PeriodicProcessesRepository.createPeriodicProcessDeploymentWithFullProcess _).tupled))

  override def findToBeRetried: Action[Seq[PeriodicProcessDeploymentWithFullProcess]] =
    activePeriodicProcessWithDeploymentQuery
      .filter { case (_, d) =>
        d.nextRetryAt <= now() &&
        d.status === (PeriodicProcessDeploymentStatus.RetryingDeploy: PeriodicProcessDeploymentStatus)
      }
      .result
      .map(_.map((PeriodicProcessesRepository.createPeriodicProcessDeploymentWithFullProcess _).tupled))

  override def findProcessData(
      id: PeriodicProcessDeploymentId
  ): Action[PeriodicProcessDeploymentWithFullProcess] = {
    (PeriodicProcessesWithJson join PeriodicProcessDeployments on (_.id === _.periodicProcessId))
      .filter { case (_, deployment) => deployment.id === id }
      .result
      .head
      .map((PeriodicProcessesRepository.createPeriodicProcessDeploymentWithFullProcess _).tupled)
  }

  override def findProcessData(processName: ProcessName): Action[Seq[PeriodicProcess[CanonicalProcess]]] = {
    PeriodicProcessesWithJson
      .filter(p => p.active === true && p.processName === processName)
      .result
      .map(_.map(PeriodicProcessesRepository.createPeriodicProcessWithJson))
  }

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
    val processesHavingDeploymentsWithMatchingStatus = PeriodicProcessesWithoutJson.filter(p =>
      p.active &&
        PeriodicProcessDeployments
          .filter(d => d.periodicProcessId === p.id && d.status.inSet(expectedDeploymentStatuses))
          .exists
    )
    getLatestDeploymentsForEachSchedule(
      processesHavingDeploymentsWithMatchingStatus,
      deploymentsPerScheduleMaxCount = 1
    ).map(_.values.headOption.getOrElse(SchedulesState(Map.empty)))
  }

  override def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[SchedulesState] = {
    val activeProcessesQuery = PeriodicProcessesWithoutJson.filter(p => p.processName === processName && p.active)
    getLatestDeploymentsForEachSchedule(activeProcessesQuery, deploymentsPerScheduleMaxCount)
      .map(_.getOrElse(processName, SchedulesState(Map.empty)))
      .run
  }

  override def getLatestDeploymentsForActiveSchedules(
      deploymentsPerScheduleMaxCount: Int
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[Map[ProcessName, SchedulesState]] = {
    val activeProcessesQuery = PeriodicProcessesWithoutJson.filter(_.active)
    getLatestDeploymentsForEachSchedule(activeProcessesQuery, deploymentsPerScheduleMaxCount).run
  }

  override def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[SchedulesState] = {
    val filteredProcessesQuery = PeriodicProcessesWithoutJson
      .filter(p => p.processName === processName && !p.active)
      .sortBy(_.createdAt.desc)
      .take(inactiveProcessesMaxCount)
    getLatestDeploymentsForEachSchedule(filteredProcessesQuery, deploymentsPerScheduleMaxCount)
      .map(_.getOrElse(processName, SchedulesState(Map.empty)))
      .run
  }

  override def getLatestDeploymentsForLatestInactiveSchedules(
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[Map[ProcessName, SchedulesState]] = {
    val filteredProcessesQuery = PeriodicProcessesWithoutJson
      .filter(!_.active)
      .sortBy(_.createdAt.desc)
      .take(inactiveProcessesMaxCount)
    getLatestDeploymentsForEachSchedule(filteredProcessesQuery, deploymentsPerScheduleMaxCount).run
  }

  private def getLatestDeploymentsForEachSchedule(
      periodicProcessesQuery: Query[PeriodicProcessesTable, PeriodicProcessEntity, Seq],
      deploymentsPerScheduleMaxCount: Int
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
      periodicProcessesQuery: Query[PeriodicProcessesTable, PeriodicProcessEntity, Seq],
      deploymentsPerScheduleMaxCount: Int
  ): Action[Seq[(PeriodicProcessMetadata, PeriodicProcessDeploymentEntity)]] = {
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
      .map { case (_, process, deployment) => (extractPeriodicProcessMetadataColumns(process), deployment) }
      .result
      .map(_.map { case (periodicProcessMetadataColumnValues, deploymentEntity) =>
        (createPeriodicProcessMetadataFromColumnValues(periodicProcessMetadataColumnValues), deploymentEntity)
      })
  }

  private def extractPeriodicProcessMetadataColumns(
      periodicProcessesTable: PeriodicProcessesTable,
  ): (
      Rep[PeriodicProcessId],
      Rep[ProcessName],
      Rep[VersionId],
      Rep[String],
      Rep[String],
      Rep[Boolean],
      Rep[Option[ProcessActionId]]
  ) =
    (
      periodicProcessesTable.id,
      periodicProcessesTable.processName,
      periodicProcessesTable.processVersionId,
      periodicProcessesTable.jarFileName,
      periodicProcessesTable.scheduleProperty,
      periodicProcessesTable.active,
      periodicProcessesTable.processActionId,
    )

  private def createPeriodicProcessMetadataFromColumnValues(
      columnValues: (PeriodicProcessId, ProcessName, VersionId, String, String, Boolean, Option[ProcessActionId])
  ): PeriodicProcessMetadata = {
    columnValues match {
      case (id, processName, versionId, jarFileName, scheduleProperty, active, processActionId) =>
        PeriodicProcessMetadata(
          id,
          processName,
          versionId,
          jarFileName,
          prepareScheduleProperty(scheduleProperty),
          active,
          processActionId
        )
    }
  }

  // This variant of method is much less optimal than postgres one. It is highly recommended to use postgres with periodics
  // If we decided to support more databases, we should consider some optimization like extracting periodic_schedule table
  // with foreign key to periodic_process and with schedule_name column - it would reduce number of queries
  private def getLatestDeploymentsForEachScheduleJdbcGeneric(
      periodicProcessesQuery: Query[PeriodicProcessesTable, PeriodicProcessEntity, Seq],
      deploymentsPerScheduleMaxCount: Int
  ): Action[Seq[(PeriodicProcessMetadata, PeriodicProcessDeploymentEntity)]] = {
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
          .map(_.map { case (entity, scheduleName) =>
            val process  = createPeriodicProcessWithoutJson(entity)
            val metadata = createPeriodicProcessMetadata(process)
            (metadata, scheduleName)
          })
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
  ): Action[PeriodicProcessDeploymentWithFullProcess] = {
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

  private def activePeriodicProcessWithDeploymentQuery = {
    (PeriodicProcessesWithJson.filter(p => p.active === true && p.processingType === processingType)
      join PeriodicProcessDeployments on (_.id === _.periodicProcessId))
  }

  private def toSchedulesState(
      list: Seq[(PeriodicProcessMetadata, PeriodicProcessDeploymentEntity)]
  ): Map[ProcessName, SchedulesState] = {
    list
      .groupBy(_._1.processName)
      .map { case (processName, list) => processName -> toSchedulesStateForSinglePeriodicProcess(list) }
  }

  private def toSchedulesStateForSinglePeriodicProcess(
      list: Seq[(PeriodicProcessMetadata, PeriodicProcessDeploymentEntity)]
  ): SchedulesState = {
    SchedulesState(
      list
        .map { case (periodicProcessMetadata, deployment) =>
          val scheduleId         = ScheduleId(periodicProcessMetadata.id, ScheduleName(deployment.scheduleName))
          val scheduleData       = (scheduleId, periodicProcessMetadata)
          val scheduleDeployment = ScheduleDeploymentData(deployment)
          (scheduleData, scheduleDeployment)
        }
        .toList
        .toGroupedMap
        .toList
        .map { case ((scheduleId, periodicProcessMetadata), deployments) =>
          scheduleId -> ScheduleData(periodicProcessMetadata, deployments)
        }
        .toMap
    )
  }

}

//Copied from designer/server.
object DBIOActionInstances {

  type DB[A] = DBIOAction[A, NoStream, Effect.All]

  implicit def dbMonad(implicit ec: ExecutionContext): Monad[DB] = new Monad[DB] {

    override def pure[A](x: A) = DBIO.successful(x)

    override def flatMap[A, B](fa: DB[A])(f: (A) => DB[B]) = fa.flatMap(f)

    // this is *not* tail recursive
    override def tailRecM[A, B](a: A)(f: (A) => DB[Either[A, B]]): DB[B] =
      f(a).flatMap {
        case Right(r) => pure(r)
        case Left(l)  => tailRecM(l)(f)
      }

  }

}

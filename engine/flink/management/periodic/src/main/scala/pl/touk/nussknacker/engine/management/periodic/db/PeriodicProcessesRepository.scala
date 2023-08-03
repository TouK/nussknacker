package pl.touk.nussknacker.engine.management.periodic.db

import cats.Monad
import com.github.tminglei.slickpg.ExPostgresProfile
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser.decode
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.periodic._
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic.model._
import slick.{dbio, lifted}
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{JdbcBackend, JdbcProfile}

import java.time.{Clock, LocalDateTime}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds


object PeriodicProcessesRepository {

 def createPeriodicProcessDeployment(processEntity: PeriodicProcessEntity,
                                     processDeploymentEntity: PeriodicProcessDeploymentEntity): PeriodicProcessDeployment = {
    val process = createPeriodicProcess(processEntity)
    PeriodicProcessDeployment(
      processDeploymentEntity.id,
      process,
      processDeploymentEntity.runAt,
      processDeploymentEntity.scheduleName,
      processDeploymentEntity.retriesLeft,
      processDeploymentEntity.nextRetryAt,
      createPeriodicDeploymentState(processDeploymentEntity)
    )
  }

  def createPeriodicDeploymentState(processDeploymentEntity: PeriodicProcessDeploymentEntity): PeriodicProcessDeploymentState = {
    PeriodicProcessDeploymentState(
      processDeploymentEntity.deployedAt,
      processDeploymentEntity.completedAt,
      processDeploymentEntity.status
    )
  }

  def createPeriodicProcess(processEntity: PeriodicProcessEntity): PeriodicProcess = {
    val processVersion = createProcessVersion(processEntity)
    val scheduleProperty = decode[ScheduleProperty](processEntity.scheduleProperty).fold(e => throw new IllegalArgumentException(e), identity)
    PeriodicProcess(processEntity.id, model.DeploymentWithJarData(
      processVersion = processVersion,
      canonicalProcess = processEntity.processJson,
      inputConfigDuringExecutionJson = processEntity.inputConfigDuringExecutionJson,
      jarFileName = processEntity.jarFileName
    ), scheduleProperty, processEntity.active, processEntity.createdAt)
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

  def markInactive(processName: ProcessName): Action[Unit]

  def create(deploymentWithJarData: DeploymentWithJarData,
             scheduleProperty: ScheduleProperty): Action[PeriodicProcess]

  def getLatestDeploymentsForActiveSchedules(processName: ProcessName, deploymentsPerScheduleMaxCount: Int): Action[Map[ScheduleId, ScheduleData]]

  def getLatestDeploymentsForLatestInactiveSchedules(processName: ProcessName, inactiveProcessesMaxCount: Int, deploymentsPerScheduleMaxCount: Int): Action[Map[ScheduleId, ScheduleData]]

  def findToBeDeployed: Action[Seq[PeriodicProcessDeployment]]

  def findToBeRetried: Action[Seq[PeriodicProcessDeployment]]

  def findDeployedOrFailedOnDeploy: Action[Seq[PeriodicProcessDeployment]]

  def findScheduled(id: PeriodicProcessId): Action[Seq[PeriodicProcessDeployment]]

  def findProcessData(id: PeriodicProcessDeploymentId): Action[PeriodicProcessDeployment]

  def findProcessData(processName: ProcessName): Action[Seq[PeriodicProcess]]

  def markDeployed(id: PeriodicProcessDeploymentId): Action[Unit]

  def markFinished(id: PeriodicProcessDeploymentId): Action[Unit]

  def markFailedOnDeployWithStatus(id: PeriodicProcessDeploymentId, status: PeriodicProcessDeploymentStatus, deployRetries: Int, retryAt: Option[LocalDateTime]): Action[Unit]

  def markFailed(id: PeriodicProcessDeploymentId): Action[Unit]

  def schedule(id: PeriodicProcessId, scheduleName: Option[String], runAt: LocalDateTime, deployMaxRetries: Int): Action[PeriodicProcessDeployment]

}

class SlickPeriodicProcessesRepository(db: JdbcBackend.DatabaseDef,
                                       override val profile: JdbcProfile,
                                       clock: Clock,
                                       processingType: String)
                                      (implicit ec: ExecutionContext)
  extends PeriodicProcessesRepository
    with PeriodicProcessesTableFactory
    with PeriodicProcessDeploymentsTableFactory with LazyLogging {

  import io.circe.syntax._
  import pl.touk.nussknacker.engine.util.Implicits._

  type Action[T] = DBIOActionInstances.DB[T]

  override implicit def monad: Monad[Action] = DBIOActionInstances.dbMonad

  override def run[T](action: DBIOAction[T, NoStream, Effect.All]): Future[T] = db.run(action.transactionally)

  override def create(deploymentWithJarData: DeploymentWithJarData,
                      scheduleProperty: ScheduleProperty): Action[PeriodicProcess] = {
    val processEntity = PeriodicProcessEntity(
      id = PeriodicProcessId(-1),
      processName = deploymentWithJarData.processVersion.processName,
      processVersionId = deploymentWithJarData.processVersion.versionId,
      processingType = processingType,
      processJson = deploymentWithJarData.canonicalProcess,
      inputConfigDuringExecutionJson = deploymentWithJarData.inputConfigDuringExecutionJson,
      jarFileName = deploymentWithJarData.jarFileName,
      scheduleProperty = scheduleProperty.asJson.noSpaces,
      active = true,
      createdAt = now()
    )
    ((PeriodicProcesses returning PeriodicProcesses into ((_, id) => id)) += processEntity).map(PeriodicProcessesRepository.createPeriodicProcess)
  }

  private def now(): LocalDateTime = LocalDateTime.now(clock)

  override def findToBeDeployed: Action[Seq[PeriodicProcessDeployment]] =
      activePeriodicProcessWithDeploymentQuery
        .filter { case (_, d) =>
          d.runAt <= now() &&
          d.status === (PeriodicProcessDeploymentStatus.Scheduled: PeriodicProcessDeploymentStatus)
        }
        .result
        .map(createPeriodicProcessDeployment)

  override def findToBeRetried: Action[Seq[PeriodicProcessDeployment]] =
    activePeriodicProcessWithDeploymentQuery
      .filter { case (_, d) =>
        d.nextRetryAt <= now() &&
        d.status === (PeriodicProcessDeploymentStatus.RetryingDeploy: PeriodicProcessDeploymentStatus)
      }
      .result
      .map(createPeriodicProcessDeployment)

  override def findProcessData(id: PeriodicProcessDeploymentId): Action[PeriodicProcessDeployment] = {
    (PeriodicProcesses join PeriodicProcessDeployments on (_.id === _.periodicProcessId))
      .filter { case (_, deployment) => deployment.id === id }
      .result.head
      .map((PeriodicProcessesRepository.createPeriodicProcessDeployment _).tupled)
  }

  override def findProcessData(processName: ProcessName): Action[Seq[PeriodicProcess]] = {
    PeriodicProcesses
      .filter(p => p.active === true && p.processName === processName.value)
      .result
      .map(_.map(PeriodicProcessesRepository.createPeriodicProcess))
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

  override def markFailedOnDeployWithStatus(id: PeriodicProcessDeploymentId, status: PeriodicProcessDeploymentStatus, retriesLeft: Int, retryAt: Option[LocalDateTime]): Action[Unit] = {
    val q = for {
      d <- PeriodicProcessDeployments if d.id === id
    } yield (d.status, d.completedAt, d.retriesLeft, d.nextRetryAt)
    val update = q.update((status, Some(now()), retriesLeft, retryAt))
    update.map(_ => ())
  }

  private def updateCompleted(id: PeriodicProcessDeploymentId, status: PeriodicProcessDeploymentStatus): Action[Unit] = {
    val q = for {
      d <- PeriodicProcessDeployments if d.id === id
    } yield (d.status, d.completedAt)
    val update = q.update((status, Some(now())))
    update.map(_ => ())
  }

  override def getLatestDeploymentsForActiveSchedules(processName: ProcessName, deploymentsPerScheduleMaxCount: Int): Action[Map[ScheduleId, ScheduleData]] = {
    val activeProcessesQuery = PeriodicProcesses.filter(_.active === true)
    getLatestDeploymentsForEachSchedule(activeProcessesQuery, processName, deploymentsPerScheduleMaxCount)
  }

  override def getLatestDeploymentsForLatestInactiveSchedules(processName: ProcessName, inactiveProcessesMaxCount: Int, deploymentsPerScheduleMaxCount: Int): Action[Map[ScheduleId, ScheduleData]] = {
    val filteredProcessesQuery = PeriodicProcesses
      .filter(_.active === false)
      .sortBy(_.createdAt.desc)
      .take(inactiveProcessesMaxCount)
    getLatestDeploymentsForEachSchedule(filteredProcessesQuery, processName, deploymentsPerScheduleMaxCount)
  }

  private def getLatestDeploymentsForEachSchedule(periodicProcessesQuery: Query[PeriodicProcessesTable, PeriodicProcessEntity, Seq],
                                                  processName: ProcessName,
                                                  deploymentsPerScheduleMaxCount: Int): Action[Map[ScheduleId, ScheduleData]] = {
    val filteredPeriodicProcessQuery = periodicProcessesQuery.filter(p => p.processingType === processingType && p.processName === processName.value)
    val latestDeploymentsForSchedules = profile match {
      case postgresProfile: ExPostgresProfile =>
        getLatestDeploymentsForEachSchedulePostgres(filteredPeriodicProcessQuery, deploymentsPerScheduleMaxCount)
      case _ =>
        getLatestDeploymentsForEachScheduleJdbcGeneric(filteredPeriodicProcessQuery, deploymentsPerScheduleMaxCount)
    }
    latestDeploymentsForSchedules.map(_.map {
      case (process, deployment) =>
        val scheduleId = ScheduleId(process.id, deployment.scheduleName)
        val scheduleDataWithoutDeployment = (scheduleId, PeriodicProcessesRepository.createPeriodicProcess(process))
        val scheduleDeployment = ScheduleDeploymentData(deployment)
        (scheduleDataWithoutDeployment, scheduleDeployment)
    }.toList.toGroupedMap.toList.map {
      case ((scheduleId, process), deployments) =>
        scheduleId -> ScheduleData(process, deployments)
    }.toMap)
  }

  // TODO: test for postgres implementation
  private def getLatestDeploymentsForEachSchedulePostgres(periodicProcessesQuery: Query[PeriodicProcessesTable, PeriodicProcessEntity, Seq],
                                                          deploymentsPerScheduleMaxCount: Int): Action[Seq[(PeriodicProcessEntity, PeriodicProcessDeploymentEntity)]] = {
    // To effectively limit deployments to given count for each schedule in one query, we use window functions in slick
    import ExPostgresProfile.api._
    import com.github.tminglei.slickpg.window.PgWindowFuncSupport.WindowFunctions._

    val deploymentsForProcesses = periodicProcessesQuery join PeriodicProcessDeployments on (_.id === _.periodicProcessId)
    deploymentsForProcesses.map {
      case (process, deployment) =>
        (rank() :: Over.partitionBy((deployment.periodicProcessId, deployment.scheduleName)).sortBy(deployment.runAt.desc), process, deployment)
    }.filter(_._1 <= deploymentsPerScheduleMaxCount.longValue()).map {
      case (_, process, deployment) =>
        (process, deployment)
    }.result
  }

  private def getLatestDeploymentsForEachScheduleJdbcGeneric(periodicProcessesQuery: Query[PeriodicProcessesTable, PeriodicProcessEntity, Seq],
                                                             deploymentsPerScheduleMaxCount: Int): Action[Seq[(PeriodicProcessEntity, PeriodicProcessDeploymentEntity)]] = {
    for {
      processes <- periodicProcessesQuery.result
      schedulesForProcesses <-
        DBIO.sequence(processes.map { process =>
          PeriodicProcessDeployments.filter(_.periodicProcessId === process.id).map(_.scheduleName).distinct.result.map(_.map((process, _)))
        }).map(_.flatten)
      deploymentsForSchedules <-
        DBIO.sequence(schedulesForProcesses.map {
          case (process, scheduleName) =>
            PeriodicProcessDeployments
              // In SQL when you compare nulls, you will get always false
              .filter(deployment => deployment.periodicProcessId === process.id && (deployment.scheduleName === scheduleName || deployment.scheduleName.isEmpty && scheduleName.isEmpty))
              .sortBy(_.runAt.desc).take(deploymentsPerScheduleMaxCount).result.map(_.map((process, _)))
        }).map(_.flatten)
    } yield deploymentsForSchedules
  }

  override def schedule(id: PeriodicProcessId, scheduleName: Option[String], runAt: LocalDateTime, deployMaxRetries: Int): Action[PeriodicProcessDeployment] = {
    val deploymentEntity = PeriodicProcessDeploymentEntity(
      id = PeriodicProcessDeploymentId(-1),
      periodicProcessId = id,
      createdAt = now(),
      runAt = runAt,
      scheduleName = scheduleName,
      deployedAt = None,
      completedAt = None,
      retriesLeft = deployMaxRetries,
      nextRetryAt = None,
      status = PeriodicProcessDeploymentStatus.Scheduled
    )
    ((PeriodicProcessDeployments returning PeriodicProcessDeployments.map(_.id) into ((_, id) => id)) += deploymentEntity).flatMap(findProcessData)
  }

  override def markInactive(processName: ProcessName): Action[Unit] = {
    val q = for {
      p <- PeriodicProcesses if p.processName === processName.value && p.active === true
    } yield p.active
    val update = q.update(false)
    update.map(_ => ())
  }

  override def findDeployedOrFailedOnDeploy: Action[Seq[PeriodicProcessDeployment]] = {
    val processWithDeployment = activePeriodicProcessWithDeploymentQuery
      .filter { case (_, d) => d.status inSet Seq(PeriodicProcessDeploymentStatus.Deployed, PeriodicProcessDeploymentStatus.FailedOnDeploy) }
    processWithDeployment
      .result
      .map(createPeriodicProcessDeployment)
  }

  override def findScheduled(id: PeriodicProcessId): Action[Seq[PeriodicProcessDeployment]] = {
    activePeriodicProcessWithDeploymentQuery
      .filter { case (p, d) => p.id === id && d.status === (PeriodicProcessDeploymentStatus.Scheduled: PeriodicProcessDeploymentStatus) }
      .result
      .map(createPeriodicProcessDeployment)
  }

  private def activePeriodicProcessWithDeploymentQuery = {
    (PeriodicProcesses.filter(p => p.active === true && p.processingType === processingType)
        join PeriodicProcessDeployments on (_.id === _.periodicProcessId))
  }

  private def createPeriodicProcessDeployment(all: Seq[(PeriodicProcessEntity, PeriodicProcessDeploymentEntity)]): Seq[PeriodicProcessDeployment] =
    all.map((PeriodicProcessesRepository.createPeriodicProcessDeployment _).tupled)

}

//Copied from designer/server.
object DBIOActionInstances {

  type DB[A] = DBIOAction[A, NoStream, Effect.All]

  implicit def dbMonad(implicit ec: ExecutionContext): Monad[DB] = new Monad[DB] {

    override def pure[A](x: A) = dbio.DBIO.successful(x)

    override def flatMap[A, B](fa: DB[A])(f: (A) => DB[B]) = fa.flatMap(f)

    //this is *not* tail recursive
    override def tailRecM[A, B](a: A)(f: (A) => DB[Either[A, B]]): DB[B] =
      f(a).flatMap {
        case Right(r) => pure(r)
        case Left(l) => tailRecM(l)(f)
      }
  }

}

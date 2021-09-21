package pl.touk.nussknacker.engine.management.periodic.db

import cats.Monad
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser.decode
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic.{model, _}
import pl.touk.nussknacker.engine.management.periodic.model.{DeploymentWithJarData, PeriodicProcess, PeriodicProcessDeployment, PeriodicProcessDeploymentId, PeriodicProcessDeploymentState, PeriodicProcessDeploymentStatus, PeriodicProcessId}
import slick.dbio
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
    PeriodicProcessDeployment(processDeploymentEntity.id, process, processDeploymentEntity.runAt, processDeploymentEntity.scheduleName,
      PeriodicProcessDeploymentState(
      processDeploymentEntity.deployedAt, processDeploymentEntity.completedAt, processDeploymentEntity.status
    ))
  }

  def createPeriodicProcess(processEntity: PeriodicProcessEntity): PeriodicProcess = {
    val processVersion = ProcessVersion.empty.copy(versionId = processEntity.processVersionId, processName = ProcessName(processEntity.processName))
    val scheduleProperty = decode[ScheduleProperty](processEntity.scheduleProperty).fold(e => throw new IllegalArgumentException(e), identity)
    PeriodicProcess(processEntity.id, model.DeploymentWithJarData(
      processVersion = processVersion,
      processJson = processEntity.processJson,
      inputConfigDuringExecutionJson = processEntity.inputConfigDuringExecutionJson,
      jarFileName = processEntity.jarFileName
    ), scheduleProperty, processEntity.active, processEntity.createdAt)
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

  def getLatestDeploymentForEachSchedule(processName: ProcessName): Action[Seq[PeriodicProcessDeployment]]

  def findToBeDeployed: Action[Seq[PeriodicProcessDeployment]]

  def findDeployed: Action[Seq[PeriodicProcessDeployment]]

  def findScheduled(id: PeriodicProcessId): Action[Seq[PeriodicProcessDeployment]]

  def findProcessData(id: PeriodicProcessDeploymentId): Action[PeriodicProcessDeployment]

  def findProcessData(processName: ProcessName): Action[Seq[PeriodicProcess]]

  def markDeployed(id: PeriodicProcessDeploymentId): Action[Unit]

  def markFinished(id: PeriodicProcessDeploymentId): Action[Unit]

  def markFailed(id: PeriodicProcessDeploymentId): Action[Unit]

  def schedule(id: PeriodicProcessId, scheduleName: Option[String], runAt: LocalDateTime): Action[PeriodicProcessDeployment]

}

class SlickPeriodicProcessesRepository(db: JdbcBackend.DatabaseDef,
                                       override val profile: JdbcProfile,
                                       clock: Clock)
                                      (implicit ec: ExecutionContext)
  extends PeriodicProcessesRepository
    with PeriodicProcessesTableFactory
    with PeriodicProcessDeploymentsTableFactory with LazyLogging {

  import io.circe.syntax._

  type Action[T] = DBIOActionInstances.DB[T]

  override implicit def monad: Monad[Action] = DBIOActionInstances.dbMonad

  override def run[T](action: DBIOAction[T, NoStream, Effect.All]): Future[T] = db.run(action.transactionally)

  override def create(deploymentWithJarData: DeploymentWithJarData,
                      scheduleProperty: ScheduleProperty): Action[PeriodicProcess] = {
    val processEntity = PeriodicProcessEntity(
      id = PeriodicProcessId(-1),
      processName = deploymentWithJarData.processVersion.processName.value,
      processVersionId = deploymentWithJarData.processVersion.versionId,
      processJson = deploymentWithJarData.processJson,
      inputConfigDuringExecutionJson = deploymentWithJarData.inputConfigDuringExecutionJson,
      jarFileName = deploymentWithJarData.jarFileName,
      scheduleProperty = scheduleProperty.asJson.noSpaces,
      active = true,
      createdAt = now()
    )
    ((PeriodicProcesses returning PeriodicProcesses into ((_, id) => id)) += processEntity).map(PeriodicProcessesRepository.createPeriodicProcess)
  }

  private def now(): LocalDateTime = LocalDateTime.now(clock)

  override def findToBeDeployed: Action[Seq[PeriodicProcessDeployment]] = {
    val active = activePeriodicProcessWithDeploymentQuery
      .filter { case (_, d) => d.runAt <= now && d.status === (PeriodicProcessDeploymentStatus.Scheduled: PeriodicProcessDeploymentStatus) }
    active
      .result
      .map(createPeriodicProcessDeployment)
  }

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
    val q = for {
      d <- PeriodicProcessDeployments if d.id === id
    } yield (d.status, d.completedAt)
    val update = q.update((PeriodicProcessDeploymentStatus.Failed, Some(now())))
    update.map(_ => ())
  }

  override def markFinished(id: PeriodicProcessDeploymentId): Action[Unit] = {
    val q = for {
      d <- PeriodicProcessDeployments if d.id === id
    } yield (d.status, d.completedAt)
    val update = q.update((PeriodicProcessDeploymentStatus.Finished, Some(now())))
    update.map(_ => ())
  }

  override def getLatestDeploymentForEachSchedule(processName: ProcessName): Action[Seq[PeriodicProcessDeployment]] = {
    val activeDeployments = activePeriodicProcessWithDeploymentQuery
      .filter { case (p, _) => p.processName === processName.value }
    val latestRunAtForEachDeployment = activeDeployments
      .groupBy { case (_, deployment) => deployment.scheduleName }
      .map { case (scheduleName, group) =>
        (scheduleName, group.map { case (_, deployment) => deployment.runAt }.max)
      }
    latestRunAtForEachDeployment
      .join(activeDeployments)
      .on { case ((scheduleName, runAt), deployment) =>
        //this is SQL, so we have to handle None separately :)
        (scheduleName === deployment._2.scheduleName || (scheduleName.isEmpty && deployment._2.scheduleName.isEmpty)) && runAt === deployment._2.runAt }
      .map(_._2)
      .result
      .map(_.map((PeriodicProcessesRepository.createPeriodicProcessDeployment _).tupled))
  }

  override def schedule(id: PeriodicProcessId, scheduleName: Option[String], runAt: LocalDateTime): Action[PeriodicProcessDeployment] = {
    val deploymentEntity = PeriodicProcessDeploymentEntity(
      id = PeriodicProcessDeploymentId(-1),
      periodicProcessId = id,
      createdAt = now(),
      runAt = runAt,
      scheduleName = scheduleName,
      deployedAt = None,
      completedAt = None,
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

  override def findDeployed: Action[Seq[PeriodicProcessDeployment]] = {
    val processWithDeployment = activePeriodicProcessWithDeploymentQuery
      .filter { case (p, d) => d.status === (PeriodicProcessDeploymentStatus.Deployed: PeriodicProcessDeploymentStatus) }
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
    (PeriodicProcesses join PeriodicProcessDeployments on (_.id === _.periodicProcessId))
      .filter { case (p, _) => p.active === true }
  }

  private def createPeriodicProcessDeployment(all: Seq[(PeriodicProcessEntity, PeriodicProcessDeploymentEntity)]): Seq[PeriodicProcessDeployment] =
    all.map((PeriodicProcessesRepository.createPeriodicProcessDeployment _).tupled)

}

//Copied from ui/server. 
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

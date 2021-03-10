package pl.touk.nussknacker.engine.management.periodic.db

import cats.Monad
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser.decode
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.periodic.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic._
import slick.dbio
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{JdbcBackend, JdbcProfile}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds


trait PeriodicProcessesRepository {

  type Action[_]

  implicit def monad: Monad[Action]

  implicit class RunOps[T](action: Action[T]) {
    def run: Future[T] = PeriodicProcessesRepository.this.run(action)
  }

  def run[T](action: Action[T]): Future[T]

  def markInactive(processName: ProcessName): Action[Unit]

  def create(deploymentWithJarData: DeploymentWithJarData,
             periodicProperty: PeriodicProperty,
             runAt: LocalDateTime): Action[Unit]

  def getScheduledRunDetails(processName: ProcessName): Action[Option[(ScheduledRunDetails, PeriodicProcessDeploymentStatus)]]

  def findToBeDeployed: Action[Seq[ScheduledRunDetails]]

  def findDeployed: Action[Seq[ScheduledRunDetails]]

  def findProcessData(id: PeriodicProcessDeploymentId): Action[DeploymentWithJarData]

  def findProcessData(processName: ProcessName): Action[Seq[DeploymentWithJarData]]

  def markDeployed(id: PeriodicProcessDeploymentId): Action[Unit]

  def markFinished(id: PeriodicProcessDeploymentId): Action[Unit]

  def markFailed(id: PeriodicProcessDeploymentId): Action[Unit]

  def schedule(id: PeriodicProcessId, runAt: LocalDateTime): Action[Unit]
}

class SlickPeriodicProcessesRepository(db: JdbcBackend.DatabaseDef,
                                       override val profile: JdbcProfile)
                                      (implicit ec: ExecutionContext)
  extends PeriodicProcessesRepository
    with PeriodicProcessesTableFactory
    with PeriodicProcessDeploymentsTableFactory with LazyLogging {

  import io.circe.syntax._

  type Action[T] = DBIOActionInstances.DB[T]

  override implicit def monad: Monad[Action] = DBIOActionInstances.dbMonad

  override def run[T](action: DBIOAction[T, NoStream, Effect.All]): Future[T] = db.run(action.transactionally)

  override def create(deploymentWithJarData: DeploymentWithJarData,
                      periodicProperty: PeriodicProperty,
                      runAt: LocalDateTime): Action[Unit] = {
    val processEntity = PeriodicProcessEntity(
      id = PeriodicProcessId(-1),
      processName = deploymentWithJarData.processVersion.processName.value,
      processVersionId = deploymentWithJarData.processVersion.versionId,
      processJson = deploymentWithJarData.processJson,
      modelConfig = deploymentWithJarData.modelConfig,
      jarFileName = deploymentWithJarData.jarFileName,
      periodicProperty = periodicProperty.asJson.noSpaces,
      active = true,
      createdAt = LocalDateTime.now()
    )
    val createAction = for {
      periodicProcessId <- (PeriodicProcesses returning PeriodicProcesses.map(_.id) into ((_, id) => id)) += processEntity
      deploymentEntity = PeriodicProcessDeploymentEntity(
        id = PeriodicProcessDeploymentId(-1),
        periodicProcessId = periodicProcessId,
        createdAt = LocalDateTime.now(),
        runAt = runAt,
        deployedAt = None,
        completedAt = None,
        status = PeriodicProcessDeploymentStatus.Scheduled
      )
      _ <- PeriodicProcessDeployments += deploymentEntity
    } yield ()
    createAction
  }

  override def findToBeDeployed: Action[Seq[ScheduledRunDetails]] = {
    val active = (PeriodicProcesses join PeriodicProcessDeployments on (_.id === _.periodicProcessId))
      .filter { case (p, _) => p.active === true }
      .filter { case (_, d) => d.runAt <= LocalDateTime.now() && d.status === (PeriodicProcessDeploymentStatus.Scheduled: PeriodicProcessDeploymentStatus) }
    active.result.map(_.map {
      //pobieranie wiecej danych??
      case (p, d) => ScheduledRunDetails(p, d)
    })
  }

  override def findProcessData(id: PeriodicProcessDeploymentId): Action[DeploymentWithJarData] = {
    val processWithDeployment = (PeriodicProcesses join PeriodicProcessDeployments on (_.id === _.periodicProcessId))
      .filter { case (_, deployment) => deployment.id === id }
    processWithDeployment.result.head.map { case (process, _) => createDeploymentWithJarData(process) }
  }

  override def findProcessData(processName: ProcessName): Action[Seq[DeploymentWithJarData]] = {
    PeriodicProcesses.filter(p => p.active === true && p.processName === processName.value).result
      .map(maybeProcessEntity => maybeProcessEntity.map(createDeploymentWithJarData))
  }

  override def markDeployed(id: PeriodicProcessDeploymentId): Action[Unit] = {
    val q = for {
      d <- PeriodicProcessDeployments if d.id === id
    } yield (d.status, d.deployedAt)
    val update = q.update((PeriodicProcessDeploymentStatus.Deployed, Some(LocalDateTime.now())))
    update.map(_ => ())
  }

  override def markFailed(id: PeriodicProcessDeploymentId): Action[Unit] = {
    val q = for {
      d <- PeriodicProcessDeployments if d.id === id
    } yield (d.status, d.completedAt)
    val update = q.update((PeriodicProcessDeploymentStatus.Failed, Some(LocalDateTime.now())))
    update.map(_ => ())
  }

  override def markFinished(id: PeriodicProcessDeploymentId): Action[Unit] = {
    val q = for {
      d <- PeriodicProcessDeployments if d.id === id
    } yield (d.status, d.completedAt)
    val update = q.update((PeriodicProcessDeploymentStatus.Finished, Some(LocalDateTime.now())))
    update.map(_ => ())
  }

  override def getScheduledRunDetails(processName: ProcessName): Action[Option[(ScheduledRunDetails, PeriodicProcessDeploymentStatus)]] = {
    val processWithDeployment = (PeriodicProcesses join PeriodicProcessDeployments on (_.id === _.periodicProcessId))
      .filter { case (p, _) => (p.active === true) && (p.processName === processName.value) }
      .sortBy { case (_, d) => d.createdAt.desc }
    processWithDeployment.result.map(_.map {
      //pobieranie wiecej danych??
      case (p, d) => (ScheduledRunDetails(p, d), d.status)
    }).map(_.headOption)
  }

  override def schedule(id: PeriodicProcessId, runAt: LocalDateTime): Action[Unit] = {
    val deploymentEntity = PeriodicProcessDeploymentEntity(
      id = PeriodicProcessDeploymentId(-1),
      periodicProcessId = id,
      createdAt = LocalDateTime.now(),
      runAt = runAt,
      deployedAt = None,
      completedAt = None,
      status = PeriodicProcessDeploymentStatus.Scheduled
    )
    (PeriodicProcessDeployments += deploymentEntity).map(_ => ())
  }

  override def markInactive(processName: ProcessName): Action[Unit] = {
    val q = for {
      p <- PeriodicProcesses if p.processName === processName.value && p.active === true
    } yield p.active
    val update = q.update(false)
    update.map(_ => ())
  }

  override def findDeployed: Action[Seq[ScheduledRunDetails]] = {
    val processWithDeployment = (PeriodicProcesses join PeriodicProcessDeployments on (_.id === _.periodicProcessId))
      .filter { case (p, d) => (p.active === true) && (d.status === (PeriodicProcessDeploymentStatus.Deployed: PeriodicProcessDeploymentStatus)) }
    processWithDeployment.result.map(_.map {
      case (p, d) => ScheduledRunDetails(p, d)
    })
  }

  private def createDeploymentWithJarData(processEntity: PeriodicProcessEntity): DeploymentWithJarData = {
    val processVersion = ProcessVersion.empty.copy(versionId = processEntity.processVersionId, processName = ProcessName(processEntity.processName))
    DeploymentWithJarData(
      processVersion = processVersion,
      processJson = processEntity.processJson,
      modelConfig = processEntity.modelConfig,
      jarFileName = processEntity.jarFileName
    )
  }
}

object ScheduledRunDetails {

  def apply(process: PeriodicProcessEntity, deployment: PeriodicProcessDeploymentEntity): ScheduledRunDetails = {
    val processName =  ProcessName(process.processName)
    val periodicProperty = decode[PeriodicProperty](process.periodicProperty).right.get
    ScheduledRunDetails(
      process.id, processName, ProcessVersion(process.processVersionId, processName, "scheduler", None), periodicProperty,
      deployment.id, deployment.runAt
    )
  }

}

case class ScheduledRunDetails(periodicProcessId: PeriodicProcessId,
                               processName: ProcessName,
                               processVersion: ProcessVersion,
                               periodicProperty: PeriodicProperty,
                               processDeploymentId: PeriodicProcessDeploymentId,
                               runAt: LocalDateTime)

case class DeployedProcess(
                            processName: ProcessName,
                            deploymentId: PeriodicProcessDeploymentId,
                            periodicProcessId: PeriodicProcessId,
                            periodicProperty: PeriodicProperty
                          )


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

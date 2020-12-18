package pl.touk.nussknacker.engine.management.periodic

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.management.periodic.db.{DeployedProcess, PeriodicProcessesRepository, ScheduledRunDetails}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName

import scala.concurrent.{ExecutionContext, Future}

class PeriodicProcessService(delegateProcessManager: ProcessManager,
                             jarManager: JarManager,
                             scheduledProcessesRepository: PeriodicProcessesRepository)
                            (implicit ec: ExecutionContext) extends LazyLogging {

  def schedule(periodicProperty: PeriodicProperty,
               processVersion: ProcessVersion,
               processJson: String): Future[Unit] = {
    periodicProperty.nextRunAt() match {
      case Right(Some(runAt)) =>
        logger.info("Scheduling periodic process: {} on {}", processVersion, runAt)
        jarManager.prepareDeploymentWithJar(processVersion, processJson).flatMap { deploymentWithJarData =>
          scheduledProcessesRepository.create(deploymentWithJarData, periodicProperty, runAt)
        }
      case Right(None) =>
        Future.failed[Unit](new PeriodicProcessException(s"No future date determined by $periodicProperty"))
      case Left(error) =>
        Future.failed[Unit](new PeriodicProcessException(error))
    }
  }

  def findToBeDeployed: Future[Seq[PeriodicProcessDeploymentId]] = {
    scheduledProcessesRepository.findToBeDeployed
  }

  def handleFinished: Future[Unit] = {
    type Handler = () => Future[Unit]
    def toHandle(deployedProcess: DeployedProcess): Future[Option[Handler]] =
      delegateProcessManager.findJobStatus(deployedProcess.processName).map {
        case Some(js) if js.status.isFailed => Some(() => markFailed(deployedProcess))
        case Some(js) if js.status.isFinished => Some(() => reschedule(deployedProcess))
        case None => Some(() => reschedule(deployedProcess))
        case _ => None
      }

    for {
      deployed <- scheduledProcessesRepository.findDeployed
      maybeHandle <- Future.sequence(deployed.map(toHandle))
      handled <- Future.sequence(maybeHandle.flatten.map(_ ()))
    } yield handled.map(_ -> {})
  }

  private def reschedule(process: DeployedProcess): Future[Unit] = {
    logger.info(s"Rescheduling process $process")
    for {
      _ <- scheduledProcessesRepository.markFinished(process.deploymentId)
      _ <- process.periodicProperty.nextRunAt() match {
        case Right(Some(futureDate)) =>
          logger.info(s"Rescheduling process $process to $futureDate")
          scheduledProcessesRepository.schedule(process.periodicProcessId, futureDate)
        case Right(None) =>
          logger.info(s"No next run of $process. Deactivating")
          deactivate(process.processName)
        case Left(error) =>
          // This case should not happen. It would mean periodic property was valid when scheduling a process
          // but was made invalid when rescheduling again.
          logger.error(s"Wrong periodic property, error: $error. Deactivating $process")
          deactivate(process.processName)
      }
    } yield ()
  }

  private def markFailed(process: DeployedProcess): Future[Unit] = {
    logger.info(s"Marking process $process as failed")
    scheduledProcessesRepository.markFailed(process.deploymentId)
  }

  def deactivate(processName: ProcessName): Future[Unit] = {
    logger.info(s"Deactivate $processName")
    for {
      // Order does matter. We need to find process data for *active* process and then
      // it can be safely marked as inactive. Otherwise we would not be able to find the data
      // and leave unused jars.
      maybeProcessData <- scheduledProcessesRepository.findProcessData(processName)
      _ <- scheduledProcessesRepository.markInactive(processName)
      _ <- maybeProcessData.fold(Future.successful(()))(processData => jarManager.deleteJar(processData.jarFileName))
    } yield ()
  }

  def getScheduledRunDetails(processName: ProcessName): Future[Option[ScheduledRunDetails]] = {
    scheduledProcessesRepository.getScheduledRunDetails(processName)
  }

  def deploy(id: PeriodicProcessDeploymentId): Future[Unit] = {
    // TODO: set status before deployment?
    val deployment = for {
      deploymentWithJarData <- scheduledProcessesRepository.findProcessData(id)
      processVersion = deploymentWithJarData.processVersion
      _ <- Future.successful(logger.info("Deploying process {} for deployment id {}", processVersion, id))
      _ <- jarManager.deployWithJar(deploymentWithJarData)
    } yield processVersion
    deployment
      .flatMap { processVersion =>
        logger.info("Process has been deployed {} for deployment id {}", processVersion, id)
        scheduledProcessesRepository.markDeployed(id)
      }
      // We can recover since deployment actor watches only future completion.
      .recoverWith { case exception =>
        logger.error(s"Process deployment failed for deployment id $id", exception)
        scheduledProcessesRepository.markFailed(id)
      }
  }
}

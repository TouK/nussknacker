package pl.touk.nussknacker.engine.management.periodic

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.management.periodic.db.{PeriodicProcessesRepository, ScheduledRunDetails}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.periodic.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic.service.{AdditionalDeploymentDataProvider, DeployedEvent, FailedEvent, FinishedEvent, PeriodicProcessEvent, PeriodicProcessListener, ScheduledEvent}

import scala.concurrent.{ExecutionContext, Future}

class PeriodicProcessService(delegateProcessManager: ProcessManager,
                             jarManager: JarManager,
                             scheduledProcessesRepository: PeriodicProcessesRepository,
                             periodicProcessListener: PeriodicProcessListener,
                             additionalDeploymentDataProvider: AdditionalDeploymentDataProvider)
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

  def findToBeDeployed: Future[Seq[ScheduledRunDetails]] = {
    scheduledProcessesRepository.findToBeDeployed
  }

  def handleFinished: Future[Unit] = {
    type Handler = () => Future[Unit]

    def handle(deployedProcess: ScheduledRunDetails): Future[Unit] = {
      delegateProcessManager.findJobStatus(deployedProcess.processName).flatMap { state =>
        handleBase(deployedProcess)(state)
      }
    }

    def handleBase(deployedProcess: ScheduledRunDetails)(processState: Option[ProcessState]): Future[Unit] = processState match {
      case Some(js) if js.status.isFailed => markFailed(deployedProcess, processState)
      case Some(js) if js.status.isFinished => reschedule(deployedProcess, processState)
      case None => reschedule(deployedProcess, processState)
      case _ => Future.successful(())
    }

    for {
      deployed <- scheduledProcessesRepository.findDeployed
      handled <- Future.sequence(deployed.map(handle))
    } yield handled.map(_ -> {})
  }

  private def reschedule(process: ScheduledRunDetails, state: Option[ProcessState]): Future[Unit] = {
    logger.info(s"Rescheduling process $process")
    for {
      _ <- scheduledProcessesRepository.markFinished(process.processDeploymentId)
      _ <- handleEvent(FinishedEvent(process, state))
      _ <- process.periodicProperty.nextRunAt() match {
        case Right(Some(futureDate)) =>
          logger.info(s"Rescheduling process $process to $futureDate")
          scheduledProcessesRepository.schedule(process.periodicProcessId, futureDate)
          handleEvent(ScheduledEvent(process.periodicProcessId, futureDate))
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

  private def markFailed(process: ScheduledRunDetails, state: Option[ProcessState]): Future[Unit] = {
    logger.info(s"Marking process $process as failed")
    scheduledProcessesRepository.markFailed(process.processDeploymentId)
    handleEvent(FailedEvent(process, state))
  }

  def deactivate(processName: ProcessName): Future[Unit] = {
    logger.info(s"Deactivate $processName")
    for {
      // Order does matter. We need to find process data for *active* process and then
      // it can be safely marked as inactive. Otherwise we would not be able to find the data
      // and leave unused jars.
      processData <- scheduledProcessesRepository.findProcessData(processName)
      _ <- scheduledProcessesRepository.markInactive(processName)
      _ <- Future.sequence(processData.map(_.jarFileName).map(jarManager.deleteJar))
    } yield ()
  }

  def getScheduledRunDetails(processName: ProcessName): Future[Option[(ScheduledRunDetails, PeriodicProcessDeploymentStatus)]] = {
    scheduledProcessesRepository.getScheduledRunDetails(processName)
  }

  def deploy(runDetails: ScheduledRunDetails): Future[Unit] = {
    // TODO: set status before deployment?
    val id = runDetails.processDeploymentId
    val deploymentData = DeploymentData(DeploymentId(id.value.toString), DeploymentData.systemUser,
      additionalDeploymentDataProvider.prepareAdditionalData(runDetails))
    val deployment = for {
      deploymentWithJarData <- scheduledProcessesRepository.findProcessData(id)
      _ <- Future.successful(logger.info("Deploying process {} for deployment id {}", deploymentWithJarData.processVersion, id))
      externalDeploymentId <- jarManager.deployWithJar(deploymentWithJarData, deploymentData)
    } yield (deploymentWithJarData, externalDeploymentId)
    deployment
      .flatMap { case (deploymentWithJarData, externalDeploymentId) =>
        logger.info("Process has been deployed {} for deployment id {}", deploymentWithJarData.processVersion, id)
        //TODO: add externalDeploymentId??
        scheduledProcessesRepository.markDeployed(id)
          .flatMap(_ => handleEvent(DeployedEvent(runDetails, externalDeploymentId)))
      }
      // We can recover since deployment actor watches only future completion.
      .recoverWith { case exception =>
        logger.error(s"Process deployment failed for deployment id $id", exception)
        markFailed(runDetails, None)
      }
  }

  private def handleEvent(event: PeriodicProcessEvent): Future[Unit] = {
    periodicProcessListener.onPeriodicProcessEvent.applyOrElse(event, (_:PeriodicProcessEvent) => Future.successful(()))
  }

}

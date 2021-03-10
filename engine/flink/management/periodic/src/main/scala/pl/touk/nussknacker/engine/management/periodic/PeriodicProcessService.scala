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

  import cats.syntax.all._
  import scheduledProcessesRepository._
  private type RepositoryAction[T] = scheduledProcessesRepository.Action[T]
  type Callback = () => Future[Unit]

  private implicit class WithCallbacks(result: RepositoryAction[Callback]) {
    def runWithCallbacks: Future[Unit] = result.run.flatMap(_())
  }

  private implicit class EmptyCallback(result: RepositoryAction[Unit]) {
    def emptyCallback: RepositoryAction[Callback] = result.map(_ => () => Future.successful(()))
  }

  def schedule(periodicProperty: PeriodicProperty,
               processVersion: ProcessVersion,
               processJson: String): Future[Unit] = {
    periodicProperty.nextRunAt() match {
      case Right(Some(runAt)) =>
        logger.info("Scheduling periodic process: {} on {}", processVersion, runAt)
        jarManager.prepareDeploymentWithJar(processVersion, processJson).flatMap { deploymentWithJarData =>
          scheduledProcessesRepository.create(deploymentWithJarData, periodicProperty, runAt).run
        }
      case Right(None) =>
        Future.failed[Unit](new PeriodicProcessException(s"No future date determined by $periodicProperty"))
      case Left(error) =>
        Future.failed[Unit](new PeriodicProcessException(error))
    }
  }

  def findToBeDeployed: Future[Seq[ScheduledRunDetails]] = {
    scheduledProcessesRepository.findToBeDeployed.run
  }

  def handleFinished: Future[Unit] = {

    def handleSingleProcess(deployedProcess: ScheduledRunDetails): Future[Unit] = {
      delegateProcessManager.findJobStatus(deployedProcess.processName).flatMap { state =>
        handleFinished(deployedProcess, state).runWithCallbacks
      }
    }

    for {
      deployed <- scheduledProcessesRepository.findDeployed.run
      //we handle each job separately, if we fail at some point, we will continue on next handleFinished run
      handled <- Future.sequence(deployed.map(handleSingleProcess))
    } yield handled.map(_ -> {})
  }

  //We assume that this method leaves with data in consistent state
  private def handleFinished(deployedProcess: ScheduledRunDetails, processState: Option[ProcessState]): RepositoryAction[Callback] = processState match {
    case Some(js) if js.status.isFailed => markFailed(deployedProcess, processState).emptyCallback
    case Some(js) if js.status.isFinished => reschedule(deployedProcess, processState)
    case None => reschedule(deployedProcess, processState)
    case _ => scheduledProcessesRepository.monad.pure(()).emptyCallback
  }

  //Mark process as Finished and reschedule - we do it transactionally
  private def reschedule(process: ScheduledRunDetails, state: Option[ProcessState]): RepositoryAction[Callback] = {
    logger.info(s"Rescheduling process $process")
    for {
      _ <- scheduledProcessesRepository.markFinished(process.processDeploymentId)
      _ = handleEvent(FinishedEvent(process, state))
      callback <- process.periodicProperty.nextRunAt() match {
        case Right(Some(futureDate)) =>
          logger.info(s"Rescheduling process $process to $futureDate")
          scheduledProcessesRepository.schedule(process.periodicProcessId, futureDate).flatMap { _ =>
            handleEvent(ScheduledEvent(process.periodicProcessId, futureDate))
          }.emptyCallback
        case Right(None) =>
          logger.info(s"No next run of $process. Deactivating")
          deactivateInternal(process.processName)
        case Left(error) =>
          // This case should not happen. It would mean periodic property was valid when scheduling a process
          // but was made invalid when rescheduling again.
          logger.error(s"Wrong periodic property, error: $error. Deactivating $process")
          deactivateInternal(process.processName)
      }
    } yield callback
  }

  private def markFailed(process: ScheduledRunDetails, state: Option[ProcessState]): RepositoryAction[Unit] = {
    logger.info(s"Marking process $process as failed")
    for {
      _ <- scheduledProcessesRepository.markFailed(process.processDeploymentId)
    } yield handleEvent(FailedEvent(process, state))
  }

  def deactivate(processName: ProcessName): Future[Unit] = deactivateInternal(processName).runWithCallbacks

  private def deactivateInternal(processName: ProcessName): RepositoryAction[Callback] = {
    logger.info(s"Deactivate $processName")
    for {
      // Order does matter. We need to find process data for *active* process and then
      // it can be safely marked as inactive. Otherwise we would not be able to find the data
      // and leave unused jars.
      processData <- scheduledProcessesRepository.findProcessData(processName)
      _ <- scheduledProcessesRepository.markInactive(processName)
      //we want to delete jars only after we successfully mark process as inactive. It's better to leave jar garbage than
      //have process without jar
    } yield () => Future.sequence(processData.map(_.jarFileName).map(jarManager.deleteJar)).map(_ => ())
  }

  def getScheduledRunDetails(processName: ProcessName): Future[Option[(ScheduledRunDetails, PeriodicProcessDeploymentStatus)]] = {
    scheduledProcessesRepository.getScheduledRunDetails(processName).run
  }

  def deploy(runDetails: ScheduledRunDetails): Future[Unit] = {
    // TODO: set status before deployment?
    val id = runDetails.processDeploymentId
    val deploymentData = DeploymentData(DeploymentId(id.value.toString), DeploymentData.systemUser,
      additionalDeploymentDataProvider.prepareAdditionalData(runDetails))
    val deployment = for {
      deploymentWithJarData <- scheduledProcessesRepository.findProcessData(id).run
      _ <- Future.successful(logger.info("Deploying process {} for deployment id {}", deploymentWithJarData.processVersion, id))
      externalDeploymentId <- jarManager.deployWithJar(deploymentWithJarData, deploymentData)
    } yield (deploymentWithJarData, externalDeploymentId)
    deployment
      .flatMap { case (deploymentWithJarData, externalDeploymentId) =>
        logger.info("Process has been deployed {} for deployment id {}", deploymentWithJarData.processVersion, id)
        //TODO: add externalDeploymentId??
        scheduledProcessesRepository.markDeployed(id)
          .flatMap(_ => handleEvent(DeployedEvent(runDetails, externalDeploymentId))).run
      }
      // We can recover since deployment actor watches only future completion.
      .recoverWith { case exception =>
        logger.error(s"Process deployment failed for deployment id $id", exception)
        markFailed(runDetails, None).run
      }
  }

  private def handleEvent(event: PeriodicProcessEvent): scheduledProcessesRepository.Action[Unit] = {
    scheduledProcessesRepository.monad.pure(
      periodicProcessListener.onPeriodicProcessEvent.applyOrElse(event, (_:PeriodicProcessEvent) => ()))
  }

}

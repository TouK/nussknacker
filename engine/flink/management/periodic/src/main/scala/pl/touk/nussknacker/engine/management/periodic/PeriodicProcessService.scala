package pl.touk.nussknacker.engine.management.periodic

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.periodic.db.PeriodicProcessesRepository
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeployment
import pl.touk.nussknacker.engine.management.periodic.service._

import java.time.Clock
import scala.concurrent.{ExecutionContext, Future}

class PeriodicProcessService(delegateProcessManager: ProcessManager,
                             jarManager: JarManager,
                             scheduledProcessesRepository: PeriodicProcessesRepository,
                             periodicProcessListener: PeriodicProcessListener,
                             additionalDeploymentDataProvider: AdditionalDeploymentDataProvider,
                             clock: Clock)
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
    periodicProperty.nextRunAt(clock) match {
      case Right(Some(runAt)) =>
        logger.info("Scheduling periodic process: {} on {}", processVersion, runAt)
        jarManager.prepareDeploymentWithJar(processVersion, processJson).flatMap { deploymentWithJarData =>
          scheduledProcessesRepository.create(deploymentWithJarData, periodicProperty, runAt).flatMap { data =>
            handleEvent(ScheduledEvent(data, firstSchedule = true))
          }.run
        }
      case Right(None) =>
        Future.failed[Unit](new PeriodicProcessException(s"No future date determined by $periodicProperty"))
      case Left(error) =>
        Future.failed[Unit](new PeriodicProcessException(error))
    }
  }

  def findToBeDeployed: Future[Seq[PeriodicProcessDeployment]] = {
    scheduledProcessesRepository.findToBeDeployed.run
  }

  def handleFinished: Future[Unit] = {

    def handleSingleProcess(deployedProcess: PeriodicProcessDeployment): Future[Unit] = {
      delegateProcessManager.findJobStatus(deployedProcess.periodicProcess.processVersion.processName).flatMap { state =>
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
  private def handleFinished(deployedProcess: PeriodicProcessDeployment, processState: Option[ProcessState]): RepositoryAction[Callback] = processState match {
    case Some(js) if js.status.isFailed => markFailed(deployedProcess, processState).emptyCallback
    case Some(js) if js.status.isFinished => reschedule(deployedProcess, processState)
    case None => reschedule(deployedProcess, processState)
    case _ => scheduledProcessesRepository.monad.pure(()).emptyCallback
  }

  //Mark process as Finished and reschedule - we do it transactionally
  private def reschedule(deployment: PeriodicProcessDeployment, state: Option[ProcessState]): RepositoryAction[Callback] = {
    logger.info(s"Rescheduling process $deployment")
    val process = deployment.periodicProcess
    for {
      _ <- scheduledProcessesRepository.markFinished(deployment.id)
      currentState <- scheduledProcessesRepository.findProcessData(deployment.id)
      _ <- handleEvent(FinishedEvent(currentState, state))
      callback <- process.periodicProperty.nextRunAt(clock) match {
        case Right(Some(futureDate)) =>
          logger.info(s"Rescheduling process $deployment to $futureDate")
          scheduledProcessesRepository.schedule(process.id, futureDate).flatMap { data =>
            handleEvent(ScheduledEvent(data, firstSchedule = false))
          }.emptyCallback
        case Right(None) =>
          logger.info(s"No next run of $deployment. Deactivating")
          deactivateInternal(process.processVersion.processName)
        case Left(error) =>
          // This case should not happen. It would mean periodic property was valid when scheduling a process
          // but was made invalid when rescheduling again.
          logger.error(s"Wrong periodic property, error: $error. Deactivating $deployment")
          deactivateInternal(process.processVersion.processName)
      }
    } yield callback
  }

  private def markFailed(deployment: PeriodicProcessDeployment, state: Option[ProcessState]): RepositoryAction[Unit] = {
    logger.info(s"Marking process $deployment as failed")
    for {
      _ <- scheduledProcessesRepository.markFailed(deployment.id)
      currentState <- scheduledProcessesRepository.findProcessData(deployment.id)
    } yield handleEvent(FailedEvent(currentState, state))
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
    } yield () => Future.sequence(processData.map(_.deploymentData.jarFileName).map(jarManager.deleteJar)).map(_ => ())
  }

  def getScheduledRunDetails(processName: ProcessName): Future[Option[PeriodicProcessDeployment]] = {
    scheduledProcessesRepository.getScheduledRunDetails(processName).run
  }

  def deploy(deployment: PeriodicProcessDeployment): Future[Unit] = {
    // TODO: set status before deployment?
    val id = deployment.id
    val deploymentData = DeploymentData(DeploymentId(id.value.toString), DeploymentData.systemUser,
      additionalDeploymentDataProvider.prepareAdditionalData(deployment))
    val deploymentWithJarData = deployment.periodicProcess.deploymentData
    val deploymentAction = for {
      _ <- Future.successful(logger.info("Deploying process {} for deployment id {}", deploymentWithJarData.processVersion, id))
      externalDeploymentId <- jarManager.deployWithJar(deploymentWithJarData, deploymentData)
    } yield externalDeploymentId
    deploymentAction
      .flatMap { externalDeploymentId =>
        logger.info("Process has been deployed {} for deployment id {}", deploymentWithJarData.processVersion, id)
        //TODO: add externalDeploymentId??
        scheduledProcessesRepository.markDeployed(id)
          .flatMap(_ => scheduledProcessesRepository.findProcessData(id))
          .flatMap(afterChange => handleEvent(DeployedEvent(afterChange, externalDeploymentId))).run
      }
      // We can recover since deployment actor watches only future completion.
      .recoverWith { case exception =>
        logger.error(s"Process deployment failed for deployment id $id", exception)
        markFailed(deployment, None).run
      }
  }

  //TODO: allow access to DB in listener?
  private def handleEvent(event: PeriodicProcessEvent): scheduledProcessesRepository.Action[Unit] = {
    scheduledProcessesRepository.monad.pure(
      periodicProcessListener.onPeriodicProcessEvent.applyOrElse(event, (_:PeriodicProcessEvent) => ()))
  }

}

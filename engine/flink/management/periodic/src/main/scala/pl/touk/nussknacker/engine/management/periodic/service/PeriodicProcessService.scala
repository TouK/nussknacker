package pl.touk.nussknacker.engine.management.periodic.service

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.periodic.db.{PeriodicProcessesRepository, ScheduledRunDetails}
import pl.touk.nussknacker.engine.management.periodic.definition.PeriodicProperty
import pl.touk.nussknacker.engine.management.periodic.jar.JarManager
import pl.touk.nussknacker.engine.management.periodic.{Clock, PeriodicProcessException}

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}
import scala.concurrent.{ExecutionContext, Future}

class PeriodicProcessService(delegateProcessManager: ProcessManager,
                             jarManager: JarManager,
                             scheduledProcessesRepository: PeriodicProcessesRepository,
                             periodicProcessListener: PeriodicProcessListener,
                             clock: Clock)
                            (implicit ec: ExecutionContext) extends LazyLogging {

  //this is *first schedule*,
  def schedule(scheduleName: String,
               periodicProperty: PeriodicProperty,
               processVersion: ProcessVersion,
               processJson: String): Future[Unit] = {
    val timeToSchedule = Instant.ofEpochMilli(clock.currentTimestamp).atZone(ZoneId.systemDefault())
    periodicProperty.nextRunAt(timeToSchedule) match {
      case Right(Some(runAt)) =>
        logger.info("Scheduling periodic process: {} on {}", processVersion, runAt)
        jarManager.prepareDeploymentWithJar(processVersion, processJson).flatMap { deploymentWithJarData =>
          scheduledProcessesRepository.create(deploymentWithJarData, scheduleName, periodicProperty, runAt)
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
      //tutaj chcemy znajdowac takze po ExternalDeploymentId???
      delegateProcessManager.findJobStatus(deployedProcess.processName).flatMap { state =>
        handleBase(deployedProcess)(state)
          .map(_ => handleEvent(FinishedEvent(deployedProcess, state)))
      }
    }

    def handleBase(deployedProcess: ScheduledRunDetails)(processState: Option[ProcessState]): Future[Unit] = processState match {
      case Some(js) if js.status.isFailed => markFailed(deployedProcess)
      case Some(js) if js.status.isFinished => reschedule(deployedProcess)
      case None => reschedule(deployedProcess)
      case _ => Future.successful(())
    }

    for {
      deployed <- scheduledProcessesRepository.findDeployed
      handled <- Future.sequence(deployed.map(handle))
    } yield handled.map(_ -> {})
  }

  private def reschedule(process: ScheduledRunDetails): Future[Unit] = {
    logger.info(s"Rescheduling process $process")
    for {
      _ <- scheduledProcessesRepository.markFinished(process.processDeploymentId)
      //TODO:
      _ <- process.periodicProperty.nextRunAt(process.runAt) match {
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

  private def markFailed(process: ScheduledRunDetails): Future[Unit] = {
    logger.info(s"Marking process $process as failed")
    scheduledProcessesRepository.markFailed(process.processDeploymentId)
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

  def getScheduledRunDetails(processName: ProcessName): Future[Option[ScheduledRunDetails]] = {
    scheduledProcessesRepository.getScheduledRunDetails(processName)
  }

  def deploy(runDetails: ScheduledRunDetails): Future[Unit] = {
    // TODO: set status before deployment?
    val id = runDetails.processDeploymentId
    val deploymentData = DeploymentData(DeploymentId(id.value.toString), User("", ""), Map(
      "deploymentId" -> id.value.toString,
      "scheduleName" -> runDetails.scheduleName,
      "runAt" -> runDetails.runAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    ))
    val deployment = for {
      //TODO: niepotrzebne?
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
        scheduledProcessesRepository.markFailed(id)
        //TODO: invoke listener??
      }
  }

  private def handleEvent(event: PeriodicProcessEvent): Future[Unit] = {
    periodicProcessListener.onPeriodicProcessEvent.applyOrElse(event, (_:PeriodicProcessEvent) => Future.successful(()))
  }

}
